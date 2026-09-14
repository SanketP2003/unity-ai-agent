package com.unityagent.product.model;

import java.time.Instant;

/**
 * Relationship entity linking an authoritative project to a workspace
 * and tracking its workspace lifecycle state.
 */
public class WorkspaceProject {
    private String projectId;
    private String workspaceId;
    private ProjectLifecycleState lifecycleState;
    private Instant createdAt;
    private Instant updatedAt;

    public WorkspaceProject() {}

    public WorkspaceProject(String projectId, String workspaceId, ProjectLifecycleState lifecycleState, Instant createdAt, Instant updatedAt) {
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.lifecycleState = lifecycleState;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public ProjectLifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(ProjectLifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
