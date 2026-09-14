package com.unityagent.product.model;

import java.time.Instant;

/**
 * Game release candidate or published release entity.
 * Once published, becomes strictly immutable.
 */
public class Release {
    private String releaseId;
    private String projectId;
    private String versionString;
    private ReleaseChannel channel = ReleaseChannel.DEVELOPMENT;
    private ReleaseStatus status = ReleaseStatus.DRAFT;
    private String buildId;
    private String validationReportJson;
    private String approvalRecordJson;
    private String changelog;
    private boolean immutable;
    private Instant createdAt;
    private Instant publishedAt;

    public Release() {}

    public Release(String releaseId, String projectId, String versionString, ReleaseChannel channel,
                   ReleaseStatus status, String buildId, String changelog, Instant createdAt) {
        this.releaseId = releaseId;
        this.projectId = projectId;
        this.versionString = versionString;
        this.channel = channel;
        this.status = status;
        this.buildId = buildId;
        this.changelog = changelog;
        this.createdAt = createdAt;
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

    public String getVersionString() {
        return versionString;
    }

    public void setVersionString(String versionString) {
        if (this.immutable) {
            throw new IllegalStateException("Cannot mutate version of an immutable published release");
        }
        this.versionString = versionString;
    }

    public ReleaseChannel getChannel() {
        return channel;
    }

    public void setChannel(ReleaseChannel channel) {
        this.channel = channel;
    }

    public ReleaseStatus getStatus() {
        return status;
    }

    public void setStatus(ReleaseStatus status) {
        if (this.immutable && status != ReleaseStatus.ROLLED_BACK && status != ReleaseStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot change status of an immutable published release except to ROLLED_BACK or ARCHIVED");
        }
        this.status = status;
    }

    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }

    public String getValidationReportJson() {
        return validationReportJson;
    }

    public void setValidationReportJson(String validationReportJson) {
        this.validationReportJson = validationReportJson;
    }

    public String getApprovalRecordJson() {
        return approvalRecordJson;
    }

    public void setApprovalRecordJson(String approvalRecordJson) {
        this.approvalRecordJson = approvalRecordJson;
    }

    public String getChangelog() {
        return changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
