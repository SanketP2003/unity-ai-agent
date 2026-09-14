package com.unityagent.product.service;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ProjectLifecycleState;
import com.unityagent.product.model.Workspace;
import com.unityagent.product.model.WorkspaceProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing workspaces and project lifecycles.
 * Maintains the existing projects table as the authoritative Single Source of Truth.
 */
@Service
public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);
    private final MemoryDatabase memoryDb;

    public WorkspaceManager(MemoryDatabase memoryDb) {
        this.memoryDb = memoryDb;
    }

    public Workspace createWorkspace(String workspaceId, String name, String rootPath) {
        Instant now = Instant.now();
        Workspace ws = new Workspace(workspaceId, name, rootPath, now, now);
        String sql = "INSERT INTO workspaces (workspace_id, name, root_path, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ws.getWorkspaceId());
            ps.setString(2, ws.getName());
            ps.setString(3, ws.getRootPath());
            ps.setString(4, ws.getCreatedAt().toString());
            ps.setString(5, ws.getUpdatedAt().toString());
            ps.executeUpdate();
            log.info("Created workspace: id={}, name={}", workspaceId, name);
        } catch (Exception e) {
            log.error("Failed to create workspace {}: {}", workspaceId, e.getMessage());
            throw new RuntimeException("Failed to create workspace: " + e.getMessage(), e);
        }
        return ws;
    }

    public Optional<Workspace> getWorkspace(String workspaceId) {
        String sql = "SELECT workspace_id, name, root_path, created_at, updated_at FROM workspaces WHERE workspace_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Workspace(
                            rs.getString("workspace_id"),
                            rs.getString("name"),
                            rs.getString("root_path"),
                            Instant.parse(rs.getString("created_at")),
                            rs.getString("updated_at") != null ? Instant.parse(rs.getString("updated_at")) : null
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get workspace {}: {}", workspaceId, e.getMessage());
        }
        return Optional.empty();
    }

    public List<Workspace> listWorkspaces() {
        List<Workspace> list = new ArrayList<>();
        String sql = "SELECT workspace_id, name, root_path, created_at, updated_at FROM workspaces ORDER BY created_at DESC";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Workspace(
                        rs.getString("workspace_id"),
                        rs.getString("name"),
                        rs.getString("root_path"),
                        Instant.parse(rs.getString("created_at")),
                        rs.getString("updated_at") != null ? Instant.parse(rs.getString("updated_at")) : null
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to list workspaces: {}", e.getMessage());
        }
        return list;
    }

    /**
     * Links a project to a workspace.
     * Enforces that the project must exist in the authoritative 'projects' table.
     */
    public WorkspaceProject addProjectToWorkspace(String workspaceId, String projectId) {
        if (!projectExistsInMaster(projectId)) {
            throw new IllegalArgumentException("Project " + projectId + " does not exist in authoritative projects table");
        }

        Instant now = Instant.now();
        WorkspaceProject wp = new WorkspaceProject(projectId, workspaceId, ProjectLifecycleState.CREATED, now, now);
        String sql = "INSERT OR REPLACE INTO workspace_projects (project_id, workspace_id, lifecycle_state, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, wp.getProjectId());
            ps.setString(2, wp.getWorkspaceId());
            ps.setString(3, wp.getLifecycleState().name());
            ps.setString(4, wp.getCreatedAt().toString());
            ps.setString(5, wp.getUpdatedAt().toString());
            ps.executeUpdate();
            log.info("Added project {} to workspace {} with state CREATED", projectId, workspaceId);
        } catch (Exception e) {
            log.error("Failed to link project {} to workspace {}: {}", projectId, workspaceId, e.getMessage());
            throw new RuntimeException("Failed to link project: " + e.getMessage(), e);
        }
        return wp;
    }

    public Optional<ProjectLifecycleState> getProjectState(String projectId) {
        String sql = "SELECT lifecycle_state FROM workspace_projects WHERE project_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(ProjectLifecycleState.valueOf(rs.getString("lifecycle_state")));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query project lifecycle state {}: {}", projectId, e.getMessage());
        }
        return Optional.empty();
    }

    public void setProjectState(String projectId, ProjectLifecycleState state) {
        String sql = "UPDATE workspace_projects SET lifecycle_state = ?, updated_at = ? WHERE project_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, state.name());
            ps.setString(2, Instant.now().toString());
            ps.setString(3, projectId);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to update lifecycle state for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Failed to update lifecycle state: " + e.getMessage(), e);
        }
    }

    public void openProject(String projectId) {
        Optional<ProjectLifecycleState> stateOpt = getProjectState(projectId);
        if (stateOpt.isEmpty()) {
            throw new IllegalArgumentException("Project " + projectId + " is not registered in a workspace");
        }
        ProjectLifecycleState state = stateOpt.get();
        if (state == ProjectLifecycleState.ARCHIVED) {
            throw new IllegalStateException("Cannot open ARCHIVED project " + projectId + "; restore it first");
        }
        if (state == ProjectLifecycleState.DELETED) {
            throw new IllegalStateException("Cannot open DELETED project " + projectId);
        }

        setProjectState(projectId, ProjectLifecycleState.ACTIVE);
        log.info("Project {} transitioned to ACTIVE state", projectId);
    }

    public void closeProject(String projectId) {
        Optional<ProjectLifecycleState> stateOpt = getProjectState(projectId);
        if (stateOpt.isPresent() && stateOpt.get() == ProjectLifecycleState.ACTIVE) {
            setProjectState(projectId, ProjectLifecycleState.READY);
            log.info("Project {} transitioned from ACTIVE to READY state", projectId);
        }
    }

    public void archiveProject(String projectId) {
        Optional<ProjectLifecycleState> stateOpt = getProjectState(projectId);
        if (stateOpt.isPresent() && stateOpt.get() == ProjectLifecycleState.ACTIVE) {
            closeProject(projectId);
        }
        setProjectState(projectId, ProjectLifecycleState.ARCHIVED);
        log.info("Project {} ARCHIVED", projectId);
    }

    public void restoreProject(String projectId) {
        Optional<ProjectLifecycleState> stateOpt = getProjectState(projectId);
        if (stateOpt.isPresent() && stateOpt.get() == ProjectLifecycleState.ARCHIVED) {
            setProjectState(projectId, ProjectLifecycleState.READY);
            log.info("Project {} restored from ARCHIVED to READY", projectId);
        }
    }

    public void deleteProject(String projectId, String confirmToken) {
        if (!"DELETE".equalsIgnoreCase(confirmToken)) {
            throw new IllegalArgumentException("Project deletion requires explicit 'DELETE' confirmation token");
        }
        deleteProject(projectId, true);
    }

    public void deleteProject(String projectId, boolean explicitConfirm) {
        if (!explicitConfirm) {
            throw new IllegalStateException("Cannot delete project without explicit confirmation");
        }
        Optional<ProjectLifecycleState> stateOpt = getProjectState(projectId);
        if (stateOpt.isPresent() && stateOpt.get() == ProjectLifecycleState.ACTIVE) {
            throw new IllegalStateException("Cannot delete an ACTIVE project. Close it before deleting.");
        }
        setProjectState(projectId, ProjectLifecycleState.DELETED);
        log.info("Project {} marked DELETED", projectId);
    }

    public boolean canRunAutonomy(String projectId) {
        Optional<ProjectLifecycleState> stateOpt = getProjectState(projectId);
        if (stateOpt.isEmpty()) return true; // standalone project without workspace
        ProjectLifecycleState s = stateOpt.get();
        return s != ProjectLifecycleState.ARCHIVED && s != ProjectLifecycleState.DELETED;
    }

    public List<WorkspaceProject> listWorkspaceProjects(String workspaceId) {
        List<WorkspaceProject> list = new ArrayList<>();
        String sql = "SELECT project_id, workspace_id, lifecycle_state, created_at, updated_at FROM workspace_projects WHERE workspace_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new WorkspaceProject(
                            rs.getString("project_id"),
                            rs.getString("workspace_id"),
                            ProjectLifecycleState.valueOf(rs.getString("lifecycle_state")),
                            Instant.parse(rs.getString("created_at")),
                            rs.getString("updated_at") != null ? Instant.parse(rs.getString("updated_at")) : null
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list projects for workspace {}: {}", workspaceId, e.getMessage());
        }
        return list;
    }

    private boolean projectExistsInMaster(String projectId) {
        String sql = "SELECT 1 FROM projects WHERE project_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
