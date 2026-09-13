package com.unityagent.studio.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Aggregated workspace projection of a Unity project.
 *
 * <p>Not a second source of truth: synthesizes authoritative project identity from
 * the persistent database, live Unity connection state, and active autonomous run state.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudioProject {

    private String projectId;
    private String projectName;
    private String unityVersion;
    private String platform;
    private String renderPipeline;
    private ProjectStatus status;
    private String health;
    private boolean unityConnected;
    private boolean providerConfigured;
    private String currentRunId;
    private String currentGoal;
    private Instant createdAt;
    private Instant lastConnectedAt;
    private Instant lastRunAt;
    private Instant lastBuildAt;
    private StudioProjectMetadata metadata;

    public StudioProject() {
        this.status = ProjectStatus.DISCONNECTED;
        this.health = "HEALTHY";
    }

    public StudioProject(String projectId, String projectName, String unityVersion, String platform) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.unityVersion = unityVersion;
        this.platform = platform;
        this.status = ProjectStatus.READY;
        this.health = "HEALTHY";
        this.createdAt = Instant.now();
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getUnityVersion() { return unityVersion; }
    public void setUnityVersion(String unityVersion) { this.unityVersion = unityVersion; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getRenderPipeline() { return renderPipeline; }
    public void setRenderPipeline(String renderPipeline) { this.renderPipeline = renderPipeline; }

    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }

    public String getHealth() { return health; }
    public void setHealth(String health) { this.health = health; }

    public boolean isUnityConnected() { return unityConnected; }
    public void setUnityConnected(boolean unityConnected) { this.unityConnected = unityConnected; }

    public boolean isProviderConfigured() { return providerConfigured; }
    public void setProviderConfigured(boolean providerConfigured) { this.providerConfigured = providerConfigured; }

    public String getCurrentRunId() { return currentRunId; }
    public void setCurrentRunId(String currentRunId) { this.currentRunId = currentRunId; }

    public String getCurrentGoal() { return currentGoal; }
    public void setCurrentGoal(String currentGoal) { this.currentGoal = currentGoal; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastConnectedAt() { return lastConnectedAt; }
    public void setLastConnectedAt(Instant lastConnectedAt) { this.lastConnectedAt = lastConnectedAt; }

    public Instant getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }

    public Instant getLastBuildAt() { return lastBuildAt; }
    public void setLastBuildAt(Instant lastBuildAt) { this.lastBuildAt = lastBuildAt; }

    public StudioProjectMetadata getMetadata() { return metadata; }
    public void setMetadata(StudioProjectMetadata metadata) { this.metadata = metadata; }
}
