package com.unityagent.product.model;

import java.time.Instant;

/**
 * Model representing a project registered in the authoritative 'projects' SQLite table.
 */
public class ProjectRecord {
    private String projectId;
    private String projectName;
    private String projectPath;
    private String unityVersion;
    private String platform;
    private String renderPipeline;
    private String extensionVersion;
    private String projectFingerprint;
    private Instant createdAt;
    private Instant lastConnectedAt;
    private String lastAgentRunId;

    public ProjectRecord() {}

    public ProjectRecord(String projectId, String projectName, String projectPath, String unityVersion,
                         String platform, String renderPipeline, String extensionVersion,
                         String projectFingerprint, Instant createdAt, Instant lastConnectedAt,
                         String lastAgentRunId) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.unityVersion = unityVersion;
        this.platform = platform;
        this.renderPipeline = renderPipeline;
        this.extensionVersion = extensionVersion;
        this.projectFingerprint = projectFingerprint;
        this.createdAt = createdAt;
        this.lastConnectedAt = lastConnectedAt;
        this.lastAgentRunId = lastAgentRunId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getUnityVersion() {
        return unityVersion;
    }

    public void setUnityVersion(String unityVersion) {
        this.unityVersion = unityVersion;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getRenderPipeline() {
        return renderPipeline;
    }

    public void setRenderPipeline(String renderPipeline) {
        this.renderPipeline = renderPipeline;
    }

    public String getExtensionVersion() {
        return extensionVersion;
    }

    public void setExtensionVersion(String extensionVersion) {
        this.extensionVersion = extensionVersion;
    }

    public String getProjectFingerprint() {
        return projectFingerprint;
    }

    public void setProjectFingerprint(String projectFingerprint) {
        this.projectFingerprint = projectFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastConnectedAt() {
        return lastConnectedAt;
    }

    public void setLastConnectedAt(Instant lastConnectedAt) {
        this.lastConnectedAt = lastConnectedAt;
    }

    public String getLastAgentRunId() {
        return lastAgentRunId;
    }

    public void setLastAgentRunId(String lastAgentRunId) {
        this.lastAgentRunId = lastAgentRunId;
    }
}
