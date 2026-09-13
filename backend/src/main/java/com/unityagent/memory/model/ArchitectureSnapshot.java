package com.unityagent.memory.model;

import java.time.Instant;

/**
 * Versioned game architecture snapshot.
 * Stores a JSON tree of the game's structure after a successful build/validation.
 * Previous versions are never silently overwritten.
 */
public class ArchitectureSnapshot {

    private long id;
    private String projectId;
    private int snapshotVersion;
    private String architectureJson;
    private Instant createdAt;

    public ArchitectureSnapshot() {
        this.createdAt = Instant.now();
    }

    public ArchitectureSnapshot(String projectId, int snapshotVersion, String architectureJson) {
        this.projectId = projectId;
        this.snapshotVersion = snapshotVersion;
        this.architectureJson = architectureJson;
        this.createdAt = Instant.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public int getSnapshotVersion() { return snapshotVersion; }
    public void setSnapshotVersion(int snapshotVersion) { this.snapshotVersion = snapshotVersion; }

    public String getArchitectureJson() { return architectureJson; }
    public void setArchitectureJson(String architectureJson) { this.architectureJson = architectureJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
