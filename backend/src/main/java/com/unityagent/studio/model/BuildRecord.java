package com.unityagent.studio.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Record of a project build execution and its artifact validation evidence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuildRecord {

    public enum BuildStatus {
        QUEUED,
        BUILDING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    private String buildId;
    private String projectId;
    private String platform;
    private String buildTarget;
    private String configuration;
    private BuildStatus status;
    private long durationMs;
    private String outputPath;
    private long artifactSize;
    private String errors;
    private String validationEvidence;
    private String createdAt;
    private String completedAt;

    public BuildRecord() {}

    public BuildRecord(String buildId, String projectId, String platform, String buildTarget,
                       String configuration, BuildStatus status, String createdAt) {
        this.buildId = buildId;
        this.projectId = projectId;
        this.platform = platform;
        this.buildTarget = buildTarget;
        this.configuration = configuration;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getBuildTarget() { return buildTarget; }
    public void setBuildTarget(String buildTarget) { this.buildTarget = buildTarget; }

    public String getConfiguration() { return configuration; }
    public void setConfiguration(String configuration) { this.configuration = configuration; }

    public BuildStatus getStatus() { return status; }
    public void setStatus(BuildStatus status) { this.status = status; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getOutputPath() { return outputPath; }
    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }

    public long getArtifactSize() { return artifactSize; }
    public void setArtifactSize(long artifactSize) { this.artifactSize = artifactSize; }

    public String getErrors() { return errors; }
    public void setErrors(String errors) { this.errors = errors; }

    public String getValidationEvidence() { return validationEvidence; }
    public void setValidationEvidence(String validationEvidence) { this.validationEvidence = validationEvidence; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
}
