package com.unityagent.product.model;

import java.time.Instant;

/**
 * Manifest included in signed project backup archives.
 * Excludes any credentials or secrets.
 */
public class BackupManifest {
    private String backupId;
    private String projectId;
    private String unityVersion;
    private int schemaVersion;
    private String checksum;
    private long fileCount;
    private long totalBytes;
    private Instant createdAt;

    public BackupManifest() {}

    public BackupManifest(String backupId, String projectId, String unityVersion, int schemaVersion,
                          String checksum, long fileCount, long totalBytes, Instant createdAt) {
        this.backupId = backupId;
        this.projectId = projectId;
        this.unityVersion = unityVersion;
        this.schemaVersion = schemaVersion;
        this.checksum = checksum;
        this.fileCount = fileCount;
        this.totalBytes = totalBytes;
        this.createdAt = createdAt;
    }

    public String getBackupId() {
        return backupId;
    }

    public void setBackupId(String backupId) {
        this.backupId = backupId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getUnityVersion() {
        return unityVersion;
    }

    public void setUnityVersion(String unityVersion) {
        this.unityVersion = unityVersion;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public long getFileCount() {
        return fileCount;
    }

    public void setFileCount(long fileCount) {
        this.fileCount = fileCount;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
