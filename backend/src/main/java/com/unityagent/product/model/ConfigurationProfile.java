package com.unityagent.product.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Project configuration profile defining non-secret environment settings.
 * Secrets/credentials are strictly forbidden in configuration profiles.
 */
public class ConfigurationProfile {
    private String profileId;
    private String projectId;
    private String profileName;
    private ConfigEnvironment environment;
    private Map<String, Object> settings = new LinkedHashMap<>();
    private Instant createdAt;

    public ConfigurationProfile() {}

    public ConfigurationProfile(String profileId, String projectId, String profileName,
                                ConfigEnvironment environment, Map<String, Object> settings, Instant createdAt) {
        this.profileId = profileId;
        this.projectId = projectId;
        this.profileName = profileName;
        this.environment = environment;
        if (settings != null) this.settings = settings;
        this.createdAt = createdAt;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public ConfigEnvironment getEnvironment() {
        return environment;
    }

    public void setEnvironment(ConfigEnvironment environment) {
        this.environment = environment;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
