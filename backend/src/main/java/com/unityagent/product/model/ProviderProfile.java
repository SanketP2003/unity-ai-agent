package com.unityagent.product.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider configuration profile.
 * Credentials and API keys must NEVER be stored in this profile;
 * they are referenced externally (env vars or secure runtime credential store).
 */
public class ProviderProfile {
    private String profileId;
    private String name;
    private String providerType; // openai-compatible, openai, ollama, vllm
    private String baseUrl;
    private String defaultModel;
    private int timeoutSeconds = 60;
    private int maxRetries = 3;
    private Map<String, Object> extraProperties = new LinkedHashMap<>();

    public ProviderProfile() {}

    public ProviderProfile(String profileId, String name, String providerType, String baseUrl, String defaultModel) {
        this.profileId = profileId;
        this.name = name;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Map<String, Object> getExtraProperties() {
        return extraProperties;
    }

    public void setExtraProperties(Map<String, Object> extraProperties) {
        this.extraProperties = extraProperties;
    }
}
