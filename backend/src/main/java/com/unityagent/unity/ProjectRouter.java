package com.unityagent.unity;

import com.unityagent.product.model.ProjectLifecycleState;
import com.unityagent.product.model.ProjectRecord;
import com.unityagent.product.service.ProjectRegistrationService;
import com.unityagent.product.service.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Authoritative router for Unity commands across multiple independent projects.
 * Enforces strict tenant isolation, project existence, lifecycle checks,
 * and eliminates silent cross-project command routing.
 */
@Service
public class ProjectRouter {

    private static final Logger log = LoggerFactory.getLogger(ProjectRouter.class);

    private final UnityConnection unityConnection;
    private final ProjectRegistrationService registrationService;
    private final WorkspaceManager workspaceManager;

    public ProjectRouter(UnityConnection unityConnection,
                         ProjectRegistrationService registrationService,
                         WorkspaceManager workspaceManager) {
        this.unityConnection = unityConnection;
        this.registrationService = registrationService;
        this.workspaceManager = workspaceManager;
    }

    /**
     * Routes a Unity command to a specific project after enforcing all security and lifecycle checks.
     *
     * @param projectId the target project ID
     * @param request the command message
     * @return the response from Unity
     */
    public UnityMessage routeCommand(String projectId, UnityMessage request)
            throws TimeoutException, ExecutionException, InterruptedException {

        if (projectId == null || projectId.trim().isEmpty()) {
            throw new IllegalStateException("PROJECT_CONTEXT_REQUIRED: Project context is required for command execution");
        }

        String targetId = projectId.trim();

        // 1. Verify project exists in authoritative projects table
        Optional<ProjectRecord> projectOpt = registrationService.getProject(targetId);
        if (projectOpt.isEmpty()) {
            throw new IllegalArgumentException("PROJECT_NOT_FOUND: Project " + targetId + " is not registered");
        }

        // 2. Verify lifecycle state
        Optional<ProjectLifecycleState> stateOpt = workspaceManager.getProjectState(targetId);
        if (stateOpt.isPresent()) {
            ProjectLifecycleState state = stateOpt.get();
            if (state == ProjectLifecycleState.ARCHIVED || state == ProjectLifecycleState.DELETED) {
                throw new IllegalStateException("PROJECT_ACCESS_DENIED: Cannot route command to project "
                        + targetId + " in state " + state);
            }
        }

        // 3. Verify project connection
        UnityConnection.ProjectConnectionInfo connInfo = unityConnection.getProjectConnection(targetId);
        if (connInfo == null || connInfo.getSession() == null || !connInfo.getSession().isOpen()) {
            throw new IllegalStateException("PROJECT_DISCONNECTED: Project " + targetId + " is not connected to Unity bridge");
        }

        request.setProjectId(targetId);
        log.debug("Routing command '{}' (op={}) strictly to project {}", request.getTool(), request.getOperationId(), targetId);

        return unityConnection.sendToolRequest(targetId, request);
    }

    public boolean isProjectConnected(String projectId) {
        if (projectId == null) return false;
        UnityConnection.ProjectConnectionInfo info = unityConnection.getProjectConnection(projectId.trim());
        return info != null && info.getSession() != null && info.getSession().isOpen();
    }

    public List<UnityConnection.ProjectConnectionInfo> listActiveConnections() {
        return unityConnection.getConnectedProjects();
    }

    public Optional<UnityConnection.ProjectConnectionInfo> getConnection(String projectId) {
        if (projectId == null) return Optional.empty();
        return Optional.ofNullable(unityConnection.getProjectConnection(projectId.trim()));
    }

    public void disconnectProject(String projectId) {
        if (projectId == null) return;
        UnityConnection.ProjectConnectionInfo info = unityConnection.getProjectConnection(projectId.trim());
        if (info != null && info.getSession() != null && info.getSession().isOpen()) {
            try {
                info.getSession().close();
                log.info("Closed session for project {}", projectId);
            } catch (Exception e) {
                log.warn("Error closing session for project {}: {}", projectId, e.getMessage());
            }
        }
    }
}
