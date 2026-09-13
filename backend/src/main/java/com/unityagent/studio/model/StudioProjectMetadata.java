package com.unityagent.studio.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Studio product/workspace metadata linked to a core Unity project record.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudioProjectMetadata {

    private String projectId;
    private String description;
    private String tags;
    private boolean favorite;
    private int targetFps;
    private String activeBuildProfile;
    private Instant createdAt;
    private Instant updatedAt;

    public StudioProjectMetadata() {
        this.favorite = false;
        this.targetFps = 60;
        this.activeBuildProfile = "Development";
        this.createdAt = Instant.now();
    }

    public StudioProjectMetadata(String projectId, String description, String tags, boolean favorite,
                                 int targetFps, String activeBuildProfile, Instant createdAt, Instant updatedAt) {
        this.projectId = projectId;
        this.description = description;
        this.tags = tags;
        this.favorite = favorite;
        this.targetFps = targetFps;
        this.activeBuildProfile = activeBuildProfile != null ? activeBuildProfile : "Development";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public int getTargetFps() { return targetFps; }
    public void setTargetFps(int targetFps) { this.targetFps = targetFps; }

    public String getActiveBuildProfile() { return activeBuildProfile; }
    public void setActiveBuildProfile(String activeBuildProfile) { this.activeBuildProfile = activeBuildProfile; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
