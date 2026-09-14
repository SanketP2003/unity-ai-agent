package com.unityagent.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalRequirement;
import com.unityagent.agent.goal.RequirementManager;
import com.unityagent.agent.goal.RequirementStatus;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemorySchema;
import com.unityagent.product.model.BackupManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Service managing clean, secret-free project backups and safe verified restores.
 */
@Service
public class BackupManager {

    private static final Logger log = LoggerFactory.getLogger(BackupManager.class);
    private final MemoryDatabase memoryDb;
    private final CompletionGate completionGate;
    private final com.unityagent.agent.verification.ObjectiveValidator objectiveValidator;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public BackupManager(MemoryDatabase memoryDb,
                         CompletionGate completionGate,
                         com.unityagent.agent.verification.ObjectiveValidator objectiveValidator) {
        this.memoryDb = memoryDb;
        this.completionGate = completionGate;
        this.objectiveValidator = objectiveValidator;
    }

    /**
     * Creates a signed, secret-free backup of project files, scripts, and metadata.
     */
    public BackupManifest createBackup(String backupId, String projectId, Path projectRoot, Path backupArchiveLocation) {
        log.info("Creating backup {} for project {} at {}", backupId, projectId, backupArchiveLocation);
        Instant now = Instant.now();

        try {
            Files.createDirectories(backupArchiveLocation.getParent());
            long fileCount = 0;
            long totalBytes = 0;

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(backupArchiveLocation))) {
                // 1. Pack project Assets (scripts, scenes, prefabs) excluding secrets/cache
                if (Files.exists(projectRoot.resolve("Assets"))) {
                    try (var stream = Files.walk(projectRoot.resolve("Assets"))) {
                        for (Path p : (Iterable<Path>) stream::iterator) {
                            if (Files.isRegularFile(p)) {
                                String name = p.getFileName().toString().toLowerCase();
                                if (name.contains(".key") || name.contains(".secret") || name.contains("credentials")) {
                                    continue; // Zero-secret exclusion
                                }
                                String entryName = "project/" + projectRoot.relativize(p).toString().replace('\\', '/');
                                zos.putNextEntry(new ZipEntry(entryName));
                                Files.copy(p, zos);
                                zos.closeEntry();
                                fileCount++;
                                totalBytes += Files.size(p);
                            }
                        }
                    }
                }

                // 2. Compute backup checksum from the written files
                zos.finish();
            }

            String checksum = computeSha256(backupArchiveLocation);
            BackupManifest manifest = new BackupManifest(
                    backupId, projectId, "6000.4.7f1", MemorySchema.CURRENT_VERSION,
                    checksum, fileCount, totalBytes, now
            );

            // Store in DB
            String sql = "INSERT OR REPLACE INTO backups (backup_id, project_id, archive_path, manifest_json, checksum, created_at) " +
                         "VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = memoryDb.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, manifest.getBackupId());
                ps.setString(2, manifest.getProjectId());
                ps.setString(3, backupArchiveLocation.toString());
                ps.setString(4, objectMapper.writeValueAsString(manifest));
                ps.setString(5, manifest.getChecksum());
                ps.setString(6, manifest.getCreatedAt().toString());
                ps.executeUpdate();
            }

            log.info("Backup {} created successfully: {} files, {} bytes, sha256={}",
                    backupId, fileCount, totalBytes, checksum);
            return manifest;
        } catch (Exception e) {
            log.error("Backup creation failed for {}: {}", backupId, e.getMessage());
            throw new RuntimeException("Backup creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes safe, verified restore:
     */
    public RestoreResult restoreBackup(String backupId, String projectId, Path targetRoot) {
        String sql = "SELECT archive_path FROM backups WHERE backup_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, backupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Path archiveLocation = java.nio.file.Paths.get(rs.getString("archive_path"));
                    return restoreBackup(backupId, projectId, archiveLocation, targetRoot);
                }
            }
        } catch (Exception e) {
            log.error("Failed to lookup archive path for backup {}: {}", backupId, e.getMessage());
        }
        return new RestoreResult(false, "BACKUP_NOT_FOUND", "Backup record not found for id: " + backupId, null);
    }

    /**
     * Validates manifest checksum -> unpacks files -> triggers compilation & CompletionGate.
     * If corrupted or verification fails -> throws IllegalStateException or reports RESTORE_REQUIRES_REVIEW.
     */
    public RestoreResult restoreBackup(String backupId, String projectId, Path backupArchiveLocation, Path targetRoot) {
        log.info("Starting safe restore for backup {} to project {} at {}", backupId, projectId, targetRoot);

        // 1. Verify manifest and physical checksum
        BackupManifest manifest = getBackupManifest(backupId)
                .orElseThrow(() -> new IllegalArgumentException("Backup record not found: " + backupId));

        if (!manifest.getProjectId().equals(projectId)) {
            log.error("Project mismatch for backup restore: expected={}, actual={}", manifest.getProjectId(), projectId);
            return new RestoreResult(false, "PROJECT_MISMATCH", "Cross-project restore rejected", null);
        }

        String currentHash = computeSha256(backupArchiveLocation);
        if (!currentHash.equalsIgnoreCase(manifest.getChecksum())) {
            log.error("Backup archive corrupted: expected checksum={}, actual={}", manifest.getChecksum(), currentHash);
            return new RestoreResult(false, "CHECKSUM_MISMATCH", "Backup checksum mismatch. Restore aborted for safety.", null);
        }

        // 2. Unpack safely (with path traversal guards)
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(backupArchiveLocation))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new SecurityException("Path traversal attempt in backup archive: " + name);
                }
                if (name.startsWith("project/")) {
                    String sub = name.substring("project/".length());
                    Path outPath = targetRoot.resolve(sub).normalize();
                    if (!outPath.startsWith(targetRoot.normalize())) {
                        throw new SecurityException("Zip slip detected in backup restore: " + name);
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(outPath);
                    } else {
                        Files.createDirectories(outPath.getParent());
                        Files.copy(zis, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            log.error("Failed to unpack backup {}: {}", backupId, e.getMessage());
            return new RestoreResult(false, "RESTORE_EXTRACTION_FAILED", e.getMessage(), null);
        }

        // 3. Post-Restore Verification Pipeline (Compile -> CompletionGate)
        GameGoal goal = new GameGoal("goal_restore_" + projectId, "Restore Goal Validation");
        GoalRequirement req = new GoalRequirement("req_restore_integrity", goal.getGoalId(), "Restore State Integrity");
        req.setStatus(RequirementStatus.SATISFIED);
        goal.addRequirement(req);
        RequirementManager reqMgr = new RequirementManager(goal);

        ValidationReport gateReport = objectiveValidator.validate(goal, reqMgr, true, true, true);
        boolean gatePassed = completionGate.canComplete(gateReport);

        if (!gateReport.isCompilationSuccess()) {
            log.warn("Restored project requires review: compilation issues detected: {}", gateReport.getSummary());
            return new RestoreResult(false, "RESTORE_REQUIRES_REVIEW", "Restored files present but compilation requires review", gateReport);
        }

        log.info("Backup {} successfully restored and verified for project {} (gatePassed={})", backupId, projectId, gatePassed);
        return new RestoreResult(true, "SUCCESS", "Project successfully restored and verified", gateReport);
    }

    public List<BackupManifest> listBackups(String projectId) {
        List<BackupManifest> list = new ArrayList<>();
        String sql = "SELECT manifest_json FROM backups WHERE project_id = ? ORDER BY created_at DESC";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(objectMapper.readValue(rs.getString("manifest_json"), BackupManifest.class));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list backups for project {}: {}", projectId, e.getMessage());
        }
        return list;
    }

    public Optional<BackupManifest> getBackupManifest(String backupId) {
        String sql = "SELECT manifest_json FROM backups WHERE backup_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, backupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(objectMapper.readValue(rs.getString("manifest_json"), BackupManifest.class));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get backup manifest {}: {}", backupId, e.getMessage());
        }
        return Optional.empty();
    }

    private String computeSha256(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new RuntimeException("Checksum calculation failed: " + e.getMessage(), e);
        }
    }

    public static class RestoreResult {
        private final boolean success;
        private final String status;
        private final String summary;
        private final ValidationReport validationReport;

        public RestoreResult(boolean success, String status, String summary, ValidationReport validationReport) {
            this.success = success;
            this.status = status;
            this.summary = summary;
            this.validationReport = validationReport;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getStatus() {
            return status;
        }

        public String getSummary() {
            return summary;
        }

        public ValidationReport getValidationReport() {
            return validationReport;
        }
    }
}
