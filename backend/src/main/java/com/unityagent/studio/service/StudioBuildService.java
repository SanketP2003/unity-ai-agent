package com.unityagent.studio.service;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.model.BuildRecord;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing build and export operations for Unity projects.
 * Enforces output path security and requires physical artifact validation before declaring success.
 */
@Service
public class StudioBuildService {

    private static final Logger log = LoggerFactory.getLogger(StudioBuildService.class);

    private final MemoryDatabase memoryDb;
    private final UnityConnection unityConnection;
    private final Map<String, BuildRecord> inMemoryBuilds = new ConcurrentHashMap<>();

    public StudioBuildService(MemoryDatabase memoryDb, UnityConnection unityConnection) {
        this.memoryDb = memoryDb;
        this.unityConnection = unityConnection;
    }

    /**
     * Sanitizes and validates the output path to prevent directory traversal outside allowed build roots.
     */
    public String sanitizeAndValidateOutputPath(String projectRoot, String requestedOutputPath) {
        if (requestedOutputPath == null || requestedOutputPath.isBlank()) {
            throw new IllegalArgumentException("Output path cannot be empty");
        }

        if (requestedOutputPath.contains("..") || requestedOutputPath.contains("%2e%2e")) {
            throw new SecurityException("Directory traversal detected in build output path: " + requestedOutputPath);
        }

        Path rootPath = Paths.get(projectRoot != null ? projectRoot : ".").toAbsolutePath().normalize();
        Path targetPath = rootPath.resolve(requestedOutputPath).normalize();

        if (!targetPath.startsWith(rootPath)) {
            throw new SecurityException("Build output path escaped project root: " + targetPath);
        }

        return targetPath.toString();
    }

    /**
     * Creates and queues a new build.
     */
    public synchronized BuildRecord queueBuild(String projectId, String platform, String configuration, String targetOutputPath) {
        String buildId = "build_" + UUID.randomUUID().toString().substring(0, 8);
        String now = Instant.now().toString();

        BuildRecord record = new BuildRecord(buildId, projectId, platform, platform, configuration,
                BuildRecord.BuildStatus.QUEUED, now);
        record.setOutputPath(targetOutputPath);
        inMemoryBuilds.put(buildId, record);

        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO build_records (build_id, project_id, platform, build_target, configuration, status, output_path, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
            stmt.setString(1, buildId);
            stmt.setString(2, projectId);
            stmt.setString(3, platform);
            stmt.setString(4, platform);
            stmt.setString(5, configuration);
            stmt.setString(6, BuildRecord.BuildStatus.QUEUED.name());
            stmt.setString(7, targetOutputPath);
            stmt.setString(8, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert build_record {}: {}", buildId, e.getMessage());
        }

        log.info("Queued build {} for project={}, platform={}, target={}", buildId, projectId, platform, targetOutputPath);
        return record;
    }

    /**
     * Executes the build and validates physical output before declaring success.
     */
    public synchronized BuildRecord executeBuild(String buildId) {
        BuildRecord record = getBuild(buildId);
        if (record == null) {
            throw new IllegalArgumentException("Build record not found: " + buildId);
        }

        long startTime = System.currentTimeMillis();
        record.setStatus(BuildRecord.BuildStatus.BUILDING);
        updateBuildInDb(record);

        // Send build command to Unity if connected
        if (unityConnection != null && unityConnection.isReady()) {
            try {
                UnityMessage buildReq = UnityMessage.toolRequest(UUID.randomUUID().toString(), "build_project", Map.of(
                        "platform", record.getPlatform(),
                        "configuration", record.getConfiguration(),
                        "outputPath", record.getOutputPath()
                ));
                UnityMessage resp = unityConnection.sendToolRequest(record.getProjectId(), buildReq);
                if (resp != null && resp.getData() != null && Boolean.FALSE.equals(resp.getData().get("success"))) {
                    record.setStatus(BuildRecord.BuildStatus.FAILED);
                    record.setErrors("Unity build failed: " + resp.getData().get("error"));
                    record.setCompletedAt(Instant.now().toString());
                    record.setDurationMs(System.currentTimeMillis() - startTime);
                    updateBuildInDb(record);
                    return record;
                }
            } catch (Exception e) {
                log.warn("Unity build invocation error: {}", e.getMessage());
            }
        }

        // PHYSICAL ARTIFACT VALIDATION:
        // Never declare SUCCEEDED without physical evidence on disk.
        String outPath = record.getOutputPath();
        if (outPath != null) {
            File artifactFile = new File(outPath);
            if (artifactFile.exists()) {
                long size = artifactFile.isDirectory() ? getDirectorySize(artifactFile) : artifactFile.length();
                if (size > 0) {
                    record.setStatus(BuildRecord.BuildStatus.SUCCEEDED);
                    record.setArtifactSize(size);
                    record.setValidationEvidence("Physical artifact verified on disk: " + artifactFile.getAbsolutePath() + " (" + size + " bytes)");
                } else {
                    record.setStatus(BuildRecord.BuildStatus.FAILED);
                    record.setErrors("Physical artifact file exists but is empty (0 bytes)");
                }
            } else {
                record.setStatus(BuildRecord.BuildStatus.FAILED);
                record.setErrors("Physical artifact not found at expected path: " + artifactFile.getAbsolutePath());
            }
        } else {
            record.setStatus(BuildRecord.BuildStatus.FAILED);
            record.setErrors("Output path was null");
        }

        record.setDurationMs(System.currentTimeMillis() - startTime);
        record.setCompletedAt(Instant.now().toString());
        updateBuildInDb(record);

        log.info("Build {} finished with status={}, size={}, evidence={}",
                buildId, record.getStatus(), record.getArtifactSize(), record.getValidationEvidence());
        return record;
    }

    /**
     * Cancels a queued or active build.
     */
    public synchronized BuildRecord cancelBuild(String buildId) {
        BuildRecord record = getBuild(buildId);
        if (record == null) {
            throw new IllegalArgumentException("Build not found: " + buildId);
        }

        if (record.getStatus() == BuildRecord.BuildStatus.QUEUED || record.getStatus() == BuildRecord.BuildStatus.BUILDING) {
            record.setStatus(BuildRecord.BuildStatus.CANCELLED);
            record.setCompletedAt(Instant.now().toString());
            updateBuildInDb(record);
            log.info("Cancelled build {}", buildId);
        }

        return record;
    }

    public BuildRecord getBuild(String buildId) {
        if (buildId == null) return null;
        BuildRecord cached = inMemoryBuilds.get(buildId);
        if (cached != null) return cached;

        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT build_id, project_id, platform, build_target, configuration, status, duration_ms,
                        output_path, artifact_size, errors, validation_evidence, created_at, completed_at
                 FROM build_records WHERE build_id = ?
             """)) {
            stmt.setString(1, buildId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BuildRecord record = new BuildRecord(
                            rs.getString("build_id"),
                            rs.getString("project_id"),
                            rs.getString("platform"),
                            rs.getString("build_target"),
                            rs.getString("configuration"),
                            BuildRecord.BuildStatus.valueOf(rs.getString("status")),
                            rs.getString("created_at")
                    );
                    record.setDurationMs(rs.getLong("duration_ms"));
                    record.setOutputPath(rs.getString("output_path"));
                    record.setArtifactSize(rs.getLong("artifact_size"));
                    record.setErrors(rs.getString("errors"));
                    record.setValidationEvidence(rs.getString("validation_evidence"));
                    record.setCompletedAt(rs.getString("completed_at"));
                    inMemoryBuilds.put(buildId, record);
                    return record;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load build_record {}: {}", buildId, e.getMessage());
        }
        return null;
    }

    public List<BuildRecord> getBuildsForProject(String projectId) {
        List<BuildRecord> list = new ArrayList<>();
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT build_id FROM build_records WHERE project_id = ? ORDER BY created_at DESC
             """)) {
            stmt.setString(1, projectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BuildRecord r = getBuild(rs.getString("build_id"));
                    if (r != null) list.add(r);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query builds for project {}: {}", projectId, e.getMessage());
        }
        return list;
    }

    private void updateBuildInDb(BuildRecord r) {
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 UPDATE build_records SET status = ?, duration_ms = ?, artifact_size = ?,
                                         errors = ?, validation_evidence = ?, completed_at = ?
                 WHERE build_id = ?
             """)) {
            stmt.setString(1, r.getStatus().name());
            stmt.setLong(2, r.getDurationMs());
            stmt.setLong(3, r.getArtifactSize());
            stmt.setString(4, r.getErrors());
            stmt.setString(5, r.getValidationEvidence());
            stmt.setString(6, r.getCompletedAt());
            stmt.setString(7, r.getBuildId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update build_record {}: {}", r.getBuildId(), e.getMessage());
        }
    }

    private long getDirectorySize(File dir) {
        long length = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) length += file.length();
                else length += getDirectorySize(file);
            }
        }
        return length;
    }
}
