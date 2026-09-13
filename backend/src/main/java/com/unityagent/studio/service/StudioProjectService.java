package com.unityagent.studio.service;

import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.persistence.AutonomousRunRecord;
import com.unityagent.agent.persistence.RunPersistenceService;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.studio.model.ProjectActivity;
import com.unityagent.studio.model.ProjectStatus;
import com.unityagent.studio.model.StudioProject;
import com.unityagent.studio.model.StudioProjectMetadata;
import com.unityagent.unity.UnityConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * Studio product/workspace projection service.
 *
 * <p>Strict architectural rule: this service is NOT the authority for project state.
 * It aggregates and projects project state from authoritative sources:
 * <ul>
 *   <li>Core Project Identity: {@link MemoryRepository} (authoritative projects table)</li>
 *   <li>Live Engine State: {@link UnityConnection} (authoritative Unity reality)</li>
 *   <li>Autonomous Run State: {@link AutonomousRunController} &amp; {@link RunPersistenceService}</li>
 *   <li>Workspace Metadata: studio_project_metadata linked extension table</li>
 * </ul>
 */
@Service
public class StudioProjectService {

    private static final Logger log = LoggerFactory.getLogger(StudioProjectService.class);

    private final MemoryDatabase db;
    private final MemoryRepository memoryRepository;
    private final UnityConnection unityConnection;
    private final AutonomousRunController runController;
    private final RunPersistenceService runPersistenceService;
    private final AIProvider aiProvider;

    @Autowired
    public StudioProjectService(MemoryDatabase db,
                                MemoryRepository memoryRepository,
                                @Autowired(required = false) UnityConnection unityConnection,
                                @Autowired(required = false) AutonomousRunController runController,
                                @Autowired(required = false) RunPersistenceService runPersistenceService,
                                @Autowired(required = false) AIProvider aiProvider) {
        this.db = db;
        this.memoryRepository = memoryRepository;
        this.unityConnection = unityConnection;
        this.runController = runController;
        this.runPersistenceService = runPersistenceService;
        this.aiProvider = aiProvider;
    }

    /**
     * Lists all studio projects as aggregated projections.
     */
    public List<StudioProject> listProjects() {
        List<ProjectMemory> baseProjects = memoryRepository != null ? memoryRepository.findAllProjects() : List.of();
        List<StudioProject> result = new ArrayList<>();

        for (ProjectMemory pm : baseProjects) {
            result.add(projectToStudioProject(pm));
        }

        // If Unity is currently connected with a project not yet in the list, project it dynamically
        if (unityConnection != null && unityConnection.getConnectedProjects() != null) {
            for (UnityConnection.ProjectConnectionInfo pci : unityConnection.getConnectedProjects()) {
                String activePid = pci.getProjectId();
                boolean found = result.stream().anyMatch(p -> p.getProjectId().equals(activePid));
                if (!found) {
                    ProjectMemory livePm = new ProjectMemory(activePid, activePid, pci.getUnityVersion(), null, null, pci.getExtensionVersion(), null);
                    result.add(0, projectToStudioProject(livePm));
                }
            }
        }

        return result;
    }

    /**
     * Gets a specific studio project projection.
     */
    public Optional<StudioProject> getProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }

        Optional<ProjectMemory> base = memoryRepository != null ? memoryRepository.findProject(projectId) : Optional.empty();
        if (base.isPresent()) {
            return Optional.of(projectToStudioProject(base.get()));
        }

        // Check if currently connected in Unity
        if (unityConnection != null) {
            UnityConnection.ProjectConnectionInfo info = unityConnection.getProjectConnection(projectId);
            if (info != null) {
                ProjectMemory livePm = new ProjectMemory(projectId, projectId, info.getUnityVersion(), null, null, info.getExtensionVersion(), null);
                return Optional.of(projectToStudioProject(livePm));
            }
        }

        return Optional.empty();
    }

    /**
     * Saves workspace-level metadata for a project.
     */
    public StudioProjectMetadata saveMetadata(String projectId, StudioProjectMetadata metadata) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId cannot be null or blank");
        }
        if (metadata == null) {
            metadata = new StudioProjectMetadata();
        }
        metadata.setProjectId(projectId);
        metadata.setUpdatedAt(Instant.now());

        String sql = """
                INSERT INTO studio_project_metadata (project_id, description, tags, favorite, target_fps, active_build_profile, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(project_id) DO UPDATE SET
                    description = excluded.description,
                    tags = excluded.tags,
                    favorite = excluded.favorite,
                    target_fps = excluded.target_fps,
                    active_build_profile = excluded.active_build_profile,
                    updated_at = excluded.updated_at
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, metadata.getDescription());
            ps.setString(3, metadata.getTags());
            ps.setInt(4, metadata.isFavorite() ? 1 : 0);
            ps.setInt(5, metadata.getTargetFps());
            ps.setString(6, metadata.getActiveBuildProfile());
            ps.setString(7, metadata.getCreatedAt() != null ? metadata.getCreatedAt().toString() : Instant.now().toString());
            ps.setString(8, metadata.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save studio project metadata for {}: {}", projectId, e.getMessage());
        }

        return metadata;
    }

    /**
     * Retrieves workspace-level metadata for a project.
     */
    public Optional<StudioProjectMetadata> getMetadata(String projectId) {
        String sql = "SELECT * FROM studio_project_metadata WHERE project_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StudioProjectMetadata m = new StudioProjectMetadata();
                    m.setProjectId(rs.getString("project_id"));
                    m.setDescription(rs.getString("description"));
                    m.setTags(rs.getString("tags"));
                    m.setFavorite(rs.getInt("favorite") == 1);
                    m.setTargetFps(rs.getInt("target_fps"));
                    m.setActiveBuildProfile(rs.getString("active_build_profile"));
                    String cAt = rs.getString("created_at");
                    if (cAt != null) m.setCreatedAt(Instant.parse(cAt));
                    String uAt = rs.getString("updated_at");
                    if (uAt != null) m.setUpdatedAt(Instant.parse(uAt));
                    return Optional.of(m);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query studio project metadata for {}: {}", projectId, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Synthesizes recent project activity.
     */
    public List<ProjectActivity> getProjectActivity(String projectId, int limit) {
        List<ProjectActivity> activities = new ArrayList<>();
        int max = limit > 0 ? limit : 20;

        // Query audit events
        String sql = "SELECT * FROM audit_events WHERE project_id = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setInt(2, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String action = rs.getString("action");
                    String desc = rs.getString("details");
                    String result = rs.getString("result");
                    activities.add(new ProjectActivity(
                            rs.getString("event_id"),
                            projectId,
                            action,
                            action.replace('_', ' '),
                            desc != null ? desc : ("Action: " + action + ", result: " + result),
                            "SUCCESS".equalsIgnoreCase(result) ? "SUCCESS" : "WARNING"
                    ));
                }
            }
        } catch (SQLException e) {
            log.debug("No audit events found or table uninitialized: {}", e.getMessage());
        }

        // If no audit events yet, synthesize from recent autonomous runs
        if (activities.isEmpty() && runPersistenceService != null) {
            List<AutonomousRunRecord> runs = runPersistenceService.getRunsByProject(projectId);
            for (AutonomousRunRecord r : runs) {
                activities.add(new ProjectActivity(
                        "act_" + r.getRunId(),
                        projectId,
                        "RUN_" + r.getStatus(),
                        "Run: " + r.getRunId(),
                        r.getGoalText() != null ? r.getGoalText() : "Autonomous run",
                        "FAILED".equalsIgnoreCase(r.getStatus()) ? "ERROR" : "INFO"
                ));
            }
        }

        return activities;
    }

    /**
     * Builds the aggregated StudioProject projection from core sources.
     */
    private StudioProject projectToStudioProject(ProjectMemory pm) {
        StudioProject sp = new StudioProject();
        sp.setProjectId(pm.getProjectId());
        sp.setProjectName(pm.getProjectName() != null ? pm.getProjectName() : pm.getProjectId());
        sp.setUnityVersion(pm.getUnityVersion());
        sp.setPlatform(pm.getPlatform() != null ? pm.getPlatform() : "Windows");
        sp.setRenderPipeline(pm.getRenderPipeline());
        sp.setCreatedAt(pm.getCreatedAt());
        sp.setLastConnectedAt(pm.getLastConnectedAt());

        // Check Unity live connection
        boolean isConnected = false;
        if (unityConnection != null && unityConnection.isReady()) {
            UnityConnection.ProjectConnectionInfo info = unityConnection.getProjectConnection(pm.getProjectId());
            isConnected = (info != null);
        }
        sp.setUnityConnected(isConnected);

        // Check AI Provider readiness
        boolean providerOk = aiProvider != null && aiProvider.isConfigured();
        sp.setProviderConfigured(providerOk);

        // Check active run
        String currentRunId = null;
        String currentGoal = null;
        ProjectStatus computedStatus = isConnected ? ProjectStatus.READY : ProjectStatus.DISCONNECTED;

        if (runController != null && isConnected) {
            Optional<AutonomousRunState> activeRun = runController.getActiveRunForProject(pm.getProjectId());
            if (activeRun.isPresent()) {
                AutonomousRunState s = activeRun.get();
                currentRunId = s.getRunId();
                currentGoal = s.getGoal() != null ? s.getGoal().getDescription() : null;
                sp.setLastRunAt(Instant.now());

                if (s.getStatus() == AutonomousRunState.RunStatus.RUNNING) {
                    computedStatus = ProjectStatus.BUILDING;
                } else if (s.getStatus() == AutonomousRunState.RunStatus.PAUSED || s.getStatus() == AutonomousRunState.RunStatus.AWAITING_INTERVENTION) {
                    computedStatus = ProjectStatus.PAUSED;
                } else if (s.getStatus() == AutonomousRunState.RunStatus.FAILED || s.getStatus() == AutonomousRunState.RunStatus.CANCELLED) {
                    computedStatus = ProjectStatus.FAILED;
                } else if (s.getStatus() == AutonomousRunState.RunStatus.COMPLETED) {
                    computedStatus = ProjectStatus.COMPLETED;
                }
            }
        }

        if (computedStatus == ProjectStatus.READY && !providerOk) {
            computedStatus = ProjectStatus.WAITING_FOR_PROVIDER;
        }

        sp.setCurrentRunId(currentRunId);
        sp.setCurrentGoal(currentGoal);
        sp.setStatus(computedStatus);
        sp.setHealth(computedStatus == ProjectStatus.FAILED ? "DEGRADED" : "HEALTHY");

        // Attach workspace metadata
        getMetadata(pm.getProjectId()).ifPresent(sp::setMetadata);

        return sp;
    }
}
