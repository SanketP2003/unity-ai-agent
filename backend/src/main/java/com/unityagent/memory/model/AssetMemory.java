package com.unityagent.memory.model;

import java.time.Instant;

/**
 * Asset metadata memory. Tracks scenes, prefabs, materials, textures, audio, animations.
 * The Unity project remains the source of truth for actual asset content.
 */
public class AssetMemory {

    private long id;
    private String projectId;
    private String assetPath;
    private String assetType;    // SCENE | PREFAB | MATERIAL | TEXTURE | AUDIO | ANIMATION | SCRIPT
    private String assetGuid;
    private String metadata;     // JSON
    private Instant lastModifiedAt;

    public AssetMemory() {
        this.lastModifiedAt = Instant.now();
    }

    public AssetMemory(String projectId, String assetPath, String assetType,
                        String assetGuid, String metadata) {
        this.projectId = projectId;
        this.assetPath = assetPath;
        this.assetType = assetType;
        this.assetGuid = assetGuid;
        this.metadata = metadata;
        this.lastModifiedAt = Instant.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getAssetPath() { return assetPath; }
    public void setAssetPath(String assetPath) { this.assetPath = assetPath; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getAssetGuid() { return assetGuid; }
    public void setAssetGuid(String assetGuid) { this.assetGuid = assetGuid; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getLastModifiedAt() { return lastModifiedAt; }
    public void setLastModifiedAt(Instant lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }
}
