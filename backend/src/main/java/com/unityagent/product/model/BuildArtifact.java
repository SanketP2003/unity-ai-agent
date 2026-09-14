package com.unityagent.product.model;

import java.time.Instant;

/**
 * Physical build artifact registered with cryptographic SHA-256 hash
 * and verified disk existence.
 */
public class BuildArtifact {
    private String artifactId;
    private String projectId;
    private String releaseId;
    private String platform;
    private String architecture;
    private String relativePath;
    private long sizeBytes;
    private String sha256Checksum;
    private ArtifactStatus status = ArtifactStatus.PENDING;
    private Instant createdAt;

    public BuildArtifact() {}

    public BuildArtifact(String artifactId, String projectId, String releaseId, String platform,
                         String architecture, String relativePath, long sizeBytes,
                         String sha256Checksum, ArtifactStatus status, Instant createdAt) {
        this.artifactId = artifactId;
        this.projectId = projectId;
        this.releaseId = releaseId;
        this.platform = platform;
        this.architecture = architecture;
        this.relativePath = relativePath;
        this.sizeBytes = sizeBytes;
        this.sha256Checksum = sha256Checksum;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public ArtifactStatus getStatus() {
        return status;
    }

    public void setStatus(ArtifactStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
