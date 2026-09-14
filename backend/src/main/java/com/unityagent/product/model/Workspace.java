package com.unityagent.product.model;

import java.time.Instant;

/**
 * Workspace grouping projects, local filesystem roots, and studio defaults.
 */
public class Workspace {
    private String workspaceId;
    private String name;
    private String rootPath;
    private Instant createdAt;
    private Instant updatedAt;

    public Workspace() {}

    public Workspace(String workspaceId, String name, String rootPath, Instant createdAt, Instant updatedAt) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.rootPath = rootPath;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
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
