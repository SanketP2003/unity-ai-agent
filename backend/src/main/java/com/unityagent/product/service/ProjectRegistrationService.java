package com.unityagent.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ProjectLifecycleState;
import com.unityagent.product.model.ProjectRecord;
import com.unityagent.product.model.UnityProjectScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing registration, lookup, and updates of Unity projects
 * against the authoritative 'projects' SQLite table.
 */
@Service
public class ProjectRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ProjectRegistrationService.class);

    private final MemoryDatabase memoryDb;
    private final UnityProjectDetector projectDetector;
    private final ProjectIdentityService identityService;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProjectRegistrationService(MemoryDatabase memoryDb,
                                      UnityProjectDetector projectDetector,
                                      ProjectIdentityService identityService,
                                      WorkspaceManager workspaceManager) {
        this.memoryDb = memoryDb;
        this.projectDetector = projectDetector;
        this.identityService = identityService;
        this.workspaceManager = workspaceManager;
    }

    /**
     * Registers a Unity project by directory path into the authoritative projects table.
     * Generates or adopts stable project identity and writes identity file into project settings.
     */
    public ProjectRecord registerProject(String projectPath, String preferredName, String workspaceId) {
        UnityProjectScanResult scan = projectDetector.detectProject(projectPath);
        if (!scan.isValid()) {
            String errorMsg = scan.getErrors().isEmpty() ? "Invalid Unity project directory" : String.join("; ", scan.getErrors());
            throw new IllegalArgumentException(errorMsg);
        }

        String normalizedPath = identityService.normalizePath(scan.getProjectPath());
        String finalName = preferredName != null && !preferredName.trim().isEmpty()
                ? preferredName.trim()
                : (scan.getProjectName() != null ? scan.getProjectName() : "UnityProject");

        // Determine projectId
        String projectId = scan.getExistingProjectId();
        if (projectId != null && identityService.isValidProjectId(projectId)) {
            // Check if clone detected
            var cloneCheck = identityService.detectCloneOrMove(projectId, normalizedPath);
            if (cloneCheck.isCloneDetected()) {
                log.info("Project clone detected for {}. Generating fresh projectId for cloned path {}", projectId, normalizedPath);
                projectId = identityService.generateProjectId();
            }
        } else {
            projectId = identityService.generateProjectId();
        }

        Instant now = Instant.now();
        String fingerprint = identityService.computeFingerprint(projectId, normalizedPath, now.toString());

        // Upsert into authoritative 'projects' table
        String sql = "INSERT INTO projects (project_id, project_name, project_path, unity_version, " +
                     "project_fingerprint, created_at, last_connected_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(project_id) DO UPDATE SET " +
                     "project_name = excluded.project_name, " +
                     "project_path = excluded.project_path, " +
                     "unity_version = excluded.unity_version, " +
                     "project_fingerprint = excluded.project_fingerprint";

        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, finalName);
            ps.setString(3, normalizedPath);
            ps.setString(4, scan.getUnityVersion());
            ps.setString(5, fingerprint);
            ps.setString(6, now.toString());
            ps.setString(7, now.toString());
            ps.executeUpdate();
            log.info("Registered project in authoritative projects table: id={}, name={}, path={}",
                    projectId, finalName, normalizedPath);
        } catch (Exception e) {
            log.error("Failed to persist project registration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register project: " + e.getMessage(), e);
        }

        // Link to workspace if provided
        if (workspaceId != null && !workspaceId.trim().isEmpty()) {
            try {
                workspaceManager.addProjectToWorkspace(workspaceId.trim(), projectId);
                workspaceManager.setProjectState(projectId, ProjectLifecycleState.READY);
            } catch (Exception e) {
                log.warn("Could not link project {} to workspace {}: {}", projectId, workspaceId, e.getMessage());
            }
        }

        // Write or sync AutonomousAgentIdentity.json in ProjectSettings/
        syncIdentityFile(normalizedPath, projectId, finalName, scan.getUnityVersion(), fingerprint, now.toString());

        return getProject(projectId).orElseThrow(() -> new IllegalStateException("Failed to read back registered project"));
    }

    public Optional<ProjectRecord> getProject(String projectId) {
        if (projectId == null || projectId.trim().isEmpty()) return Optional.empty();

        String sql = "SELECT project_id, project_name, project_path, unity_version, platform, render_pipeline, " +
                     "extension_version, project_fingerprint, created_at, last_connected_at, last_agent_run_id " +
                     "FROM projects WHERE project_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to query project {}: {}", projectId, e.getMessage());
        }
        return Optional.empty();
    }

    public List<ProjectRecord> listProjects() {
        List<ProjectRecord> list = new ArrayList<>();
        String sql = "SELECT project_id, project_name, project_path, unity_version, platform, render_pipeline, " +
                     "extension_version, project_fingerprint, created_at, last_connected_at, last_agent_run_id " +
                     "FROM projects ORDER BY created_at DESC";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (Exception e) {
            log.error("Failed to list projects: {}", e.getMessage());
        }
        return list;
    }

    public void updateLastConnected(String projectId) {
        if (projectId == null) return;
        String sql = "UPDATE projects SET last_connected_at = ? WHERE project_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, projectId.trim());
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("Failed to update last_connected_at for {}: {}", projectId, e.getMessage());
        }
    }

    public void updateLastAgentRunId(String projectId, String agentRunId) {
        if (projectId == null) return;
        String sql = "UPDATE projects SET last_agent_run_id = ? WHERE project_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agentRunId);
            ps.setString(2, projectId.trim());
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("Failed to update last_agent_run_id for {}: {}", projectId, e.getMessage());
        }
    }

    private void syncIdentityFile(String projectPath, String projectId, String projectName,
                                  String unityVersion, String fingerprint, String createdAt) {
        try {
            Path settingsDir = Paths.get(projectPath, "ProjectSettings");
            if (!Files.exists(settingsDir)) {
                Files.createDirectories(settingsDir);
            }
            Path idFile = settingsDir.resolve("AutonomousAgentIdentity.json");
            ObjectNode root = objectMapper.createObjectNode();
            root.put("projectId", projectId);
            root.put("projectName", projectName);
            root.put("projectPath", projectPath);
            root.put("unityVersion", unityVersion != null ? unityVersion : "");
            root.put("fingerprint", fingerprint != null ? fingerprint : "");
            root.put("createdAt", createdAt);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(idFile.toFile(), root);
            log.info("Synced identity file at {}", idFile);
        } catch (Exception e) {
            log.warn("Could not write AutonomousAgentIdentity.json in {}: {}", projectPath, e.getMessage());
        }
    }

    private ProjectRecord mapResultSet(ResultSet rs) throws Exception {
        return new ProjectRecord(
                rs.getString("project_id"),
                rs.getString("project_name"),
                rs.getString("project_path"),
                rs.getString("unity_version"),
                rs.getString("platform"),
                rs.getString("render_pipeline"),
                rs.getString("extension_version"),
                rs.getString("project_fingerprint"),
                rs.getString("created_at") != null ? Instant.parse(rs.getString("created_at")) : null,
                rs.getString("last_connected_at") != null ? Instant.parse(rs.getString("last_connected_at")) : null,
                rs.getString("last_agent_run_id")
        );
    }
}
