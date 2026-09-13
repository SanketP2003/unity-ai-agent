package com.unityagent.memory.model;

import java.time.Instant;

/**
 * User preference storage. Non-sensitive project or global preferences.
 * Never stores API keys, tokens, passwords, or credentials.
 */
public class UserPreference {

    private long id;
    private String projectId;      // nullable — global if null
    private String preferenceKey;
    private String preferenceValue;
    private Instant createdAt;
    private Instant updatedAt;

    public UserPreference() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UserPreference(String projectId, String preferenceKey, String preferenceValue) {
        this.projectId = projectId;
        this.preferenceKey = preferenceKey;
        this.preferenceValue = preferenceValue;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getPreferenceKey() { return preferenceKey; }
    public void setPreferenceKey(String preferenceKey) { this.preferenceKey = preferenceKey; }

    public String getPreferenceValue() { return preferenceValue; }
    public void setPreferenceValue(String preferenceValue) { this.preferenceValue = preferenceValue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
