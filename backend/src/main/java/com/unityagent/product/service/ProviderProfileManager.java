package com.unityagent.product.service;

import com.unityagent.product.model.ProviderProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service managing provider profiles.
 * Enforces zero-credential persistence: API keys are referenced externally via env vars or runtime vault.
 */
@Service
public class ProviderProfileManager {

    private static final Logger log = LoggerFactory.getLogger(ProviderProfileManager.class);
    private final Map<String, ProviderProfile> profiles = new LinkedHashMap<>();

    public ProviderProfileManager() {
        initDefaultProfiles();
    }

    private void initDefaultProfiles() {
        profiles.put("nvidia", new ProviderProfile(
                "nvidia", "NVIDIA NIM (OpenAI-Compatible)", "openai-compatible",
                "https://integrate.api.nvidia.com/v1", "meta/llama-3.1-70b-instruct"
        ));
        profiles.put("openai", new ProviderProfile(
                "openai", "OpenAI (Official)", "openai",
                "https://api.openai.com/v1", "gpt-4o"
        ));
        profiles.put("ollama", new ProviderProfile(
                "ollama", "Ollama (Local)", "openai-compatible",
                "http://localhost:11434/v1", "llama3.1"
        ));
        profiles.put("vllm", new ProviderProfile(
                "vllm", "vLLM (Self-Hosted)", "openai-compatible",
                "http://localhost:8000/v1", "meta-llama/Meta-Llama-3-70B-Instruct"
        ));
        profiles.put("custom", new ProviderProfile(
                "custom", "Custom OpenAI-Compatible", "openai-compatible",
                "http://localhost:8080/v1", "default-model"
        ));
    }

    public List<ProviderProfile> listProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public Optional<ProviderProfile> getProfile(String profileId) {
        return Optional.ofNullable(profiles.get(profileId));
    }

    public void registerProfile(ProviderProfile profile) {
        if (profile == null || profile.getProfileId() == null) {
            throw new IllegalArgumentException("Profile and profileId cannot be null");
        }
        profiles.put(profile.getProfileId(), profile);
        log.info("Registered provider profile: {}", profile.getProfileId());
    }

    /**
     * Resolves the API key externally (env var, system prop) without storing in profile or DB.
     */
    public Optional<String> resolveExternalApiKey(String profileId) {
        String envName = profileId.toUpperCase() + "_API_KEY";
        String val = System.getenv(envName);
        if (val == null || val.isBlank()) {
            val = System.getenv("UNITY_AGENT_API_KEY");
        }
        if (val == null || val.isBlank()) {
            val = System.getProperty(envName.toLowerCase().replace('_', '.'));
        }
        return Optional.ofNullable(val);
    }
}
