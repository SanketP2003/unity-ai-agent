package com.unityagent.memory.model;

import java.time.Instant;

/**
 * Persistent project identity and metadata.
 * Plain POJO — no JPA annotations.
 */
public class ProjectMemory {

    private String projectId;
    private String projectName;
    private String unityVersion;
    private String platform;
    private String renderPipeline;
    private String extensionVersion;
    private String projectFingerprint;
    private Instant createdAt;
    private Instant lastConnectedAt;
    private String lastAgentRunId;

    public ProjectMemory() {
        this.createdAt = Instant.now();
    }

    public ProjectMemory(String projectId, String projectName, String unityVersion,
                          String platform, String renderPipeline, String extensionVersion,
                          String projectFingerprint) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.unityVersion = unityVersion;
        this.platform = platform;
        this.renderPipeline = renderPipeline;
        this.extensionVersion = extensionVersion;
        this.projectFingerprint = projectFingerprint;
        this.createdAt = Instant.now();
        this.lastConnectedAt = Instant.now();
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

    public String getExtensionVersion() { return extensionVersion; }
    public void setExtensionVersion(String extensionVersion) { this.extensionVersion = extensionVersion; }

    public String getProjectFingerprint() { return projectFingerprint; }
    public void setProjectFingerprint(String projectFingerprint) { this.projectFingerprint = projectFingerprint; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastConnectedAt() { return lastConnectedAt; }
    public void setLastConnectedAt(Instant lastConnectedAt) { this.lastConnectedAt = lastConnectedAt; }

    public String getLastAgentRunId() { return lastAgentRunId; }
    public void setLastAgentRunId(String lastAgentRunId) { this.lastAgentRunId = lastAgentRunId; }
}
