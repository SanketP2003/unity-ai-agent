package com.unityagent.product.model;

import java.time.Instant;

/**
 * Record of an artifact deployment to a target environment.
 */
public class DeploymentRecord {
    private String deploymentId;
    private String releaseId;
    private String projectId;
    private String targetName;
    private String providerType; // LOCAL, FILESYSTEM, CLOUD
    private DeploymentStatus status = DeploymentStatus.QUEUED;
    private String targetLocation;
    private Instant createdAt;
    private Instant completedAt;

    public DeploymentRecord() {}

    public DeploymentRecord(String deploymentId, String releaseId, String projectId,
                            String targetName, String providerType, DeploymentStatus status,
                            String targetLocation, Instant createdAt) {
        this.deploymentId = deploymentId;
        this.releaseId = releaseId;
        this.projectId = projectId;
        this.targetName = targetName;
        this.providerType = providerType;
        this.status = status;
        this.targetLocation = targetLocation;
        this.createdAt = createdAt;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public void setStatus(DeploymentStatus status) {
        this.status = status;
    }

    public String getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(String targetLocation) {
        this.targetLocation = targetLocation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
