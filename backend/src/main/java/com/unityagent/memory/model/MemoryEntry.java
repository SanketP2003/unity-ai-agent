package com.unityagent.memory.model;

import java.time.Instant;

/**
 * Structured key-value memory fact with source tracking, confidence, and freshness.
 *
 * Categories: PROJECT, ARCHITECTURE, SCRIPT, ASSET, FAILURE, VALIDATION, PREFERENCE
 * Sources: UNITY_INSPECTION, AGENT_RESULT, USER_STATEMENT, VALIDATION
 */
public class MemoryEntry {

    private long id;
    private String projectId;
    private String category;
    private String key;
    private String value;
    private String source;
    private double confidence;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastVerifiedAt;

    public MemoryEntry() {
        this.confidence = 1.0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public MemoryEntry(String projectId, String category, String key, String value,
                        String source, double confidence) {
        this.projectId = projectId;
        this.category = category;
        this.key = key;
        this.value = value;
        this.source = source;
        this.confidence = confidence;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Determine freshness based on lastVerifiedAt and a threshold.
     */
    public MemoryFreshness getFreshness(long staleThresholdHours) {
        if (lastVerifiedAt == null) {
            return MemoryFreshness.UNKNOWN;
        }
        long hoursSinceVerified = java.time.Duration.between(lastVerifiedAt, Instant.now()).toHours();
        return hoursSinceVerified <= staleThresholdHours ? MemoryFreshness.FRESH : MemoryFreshness.STALE;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(Instant lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }
}
