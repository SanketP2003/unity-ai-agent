package com.unityagent.memory.model;

import java.time.Instant;

/**
 * Script metadata memory. Tracks path, class name, content hash, and attachments.
 * The Unity project remains the source of truth for actual script content.
 */
public class ScriptMemory {

    private long id;
    private String projectId;
    private String scriptPath;
    private String className;
    private String contentHash;
    private String attachedObjects;  // JSON array of object names
    private String dependencies;     // JSON array of dependency names
    private Instant lastModifiedAt;
    private String source;           // UNITY_INSPECTION | AGENT_RESULT
    private double confidence;

    public ScriptMemory() {
        this.confidence = 1.0;
        this.lastModifiedAt = Instant.now();
    }

    public ScriptMemory(String projectId, String scriptPath, String className,
                         String contentHash, String attachedObjects, String dependencies) {
        this.projectId = projectId;
        this.scriptPath = scriptPath;
        this.className = className;
        this.contentHash = contentHash;
        this.attachedObjects = attachedObjects;
        this.dependencies = dependencies;
        this.lastModifiedAt = Instant.now();
        this.source = "AGENT_RESULT";
        this.confidence = 1.0;
    }

    /**
     * Returns true if the stored hash differs from the given current hash.
     */
    public boolean isHashStale(String currentHash) {
        if (contentHash == null || currentHash == null) return true;
        return !contentHash.equals(currentHash);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getScriptPath() { return scriptPath; }
    public void setScriptPath(String scriptPath) { this.scriptPath = scriptPath; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getAttachedObjects() { return attachedObjects; }
    public void setAttachedObjects(String attachedObjects) { this.attachedObjects = attachedObjects; }

    public String getDependencies() { return dependencies; }
    public void setDependencies(String dependencies) { this.dependencies = dependencies; }

    public Instant getLastModifiedAt() { return lastModifiedAt; }
    public void setLastModifiedAt(Instant lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}
