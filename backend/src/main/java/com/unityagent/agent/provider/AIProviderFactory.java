package com.unityagent.agent.provider;

import com.unityagent.agent.provider.openai.OpenAICompatibleProvider;
import com.unityagent.agent.provider.openai.OpenAIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for resolving and selecting the active AIProvider.
 * Decouples provider selection from the core AgentLoop.
 */
@Component
public class AIProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(AIProviderFactory.class);

    private volatile String activeProviderName;
    private final Map<String, AIProvider> providers = new ConcurrentHashMap<>();

    @Autowired
    public AIProviderFactory(
            @Value("${agent.ai.provider:openai-compatible}") String configuredProvider,
            OpenAIProvider openAIProvider,
            OpenAICompatibleProvider openAICompatibleProvider) {
        this.activeProviderName = configuredProvider != null ? configuredProvider.trim().toLowerCase() : "openai-compatible";

        registerProvider("openai", openAIProvider);
        registerProvider("openai-compatible", openAICompatibleProvider);
        registerProvider("openaicompatible", openAICompatibleProvider);
        registerProvider("compatible", openAICompatibleProvider);

        log.info("AIProviderFactory initialized. Active provider: '{}'", this.activeProviderName);
    }

    /**
     * Testing constructor allowing custom provider registrations.
     */
    public AIProviderFactory(String configuredProvider, Map<String, AIProvider> providerMap) {
        this.activeProviderName = configuredProvider != null ? configuredProvider.trim().toLowerCase() : "openai-compatible";
        if (providerMap != null) {
            for (Map.Entry<String, AIProvider> entry : providerMap.entrySet()) {
                registerProvider(entry.getKey(), entry.getValue());
            }
        }
    }

    public void registerProvider(String name, AIProvider provider) {
        if (name != null && provider != null) {
            providers.put(name.trim().toLowerCase(), provider);
        }
    }

    public AIProvider getProvider() {
        return getProvider(activeProviderName);
    }

    public AIProvider getProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            providerName = "openai-compatible";
        }
        String key = providerName.trim().toLowerCase();
        AIProvider provider = providers.get(key);
        if (provider != null) {
            return provider;
        }

        throw new IllegalArgumentException("Unknown AI provider: '" + providerName +
                "'. Available providers: " + providers.keySet());
    }

    public void setActiveProvider(String providerName) {
        if (providerName != null && !providerName.isBlank()) {
            String key = providerName.trim().toLowerCase();
            if (providers.containsKey(key)) {
                this.activeProviderName = key;
                log.info("Active AI provider switched to: '{}'", this.activeProviderName);
            } else {
                throw new IllegalArgumentException("Cannot set unknown active provider: '" + providerName + "'");
            }
        }
    }

    /**
     * Dynamically configures a provider with user-provided settings without persisting secrets to disk.
     */
    public void configureProvider(String providerName, String baseUrl, String apiKey, String model) {
        configureProvider(providerName, baseUrl, apiKey, model, null);
    }

    public void configureProvider(String providerName, String baseUrl, String apiKey, String model, ProviderCapabilities capabilities) {
        if (providerName == null || providerName.isBlank()) {
            providerName = activeProviderName;
        }
        String key = providerName.trim().toLowerCase();
        AIProvider provider = providers.get(key);
        if (provider == null) {
            throw new IllegalArgumentException("Cannot configure unknown provider: '" + providerName + "'");
        }

        if (provider instanceof OpenAICompatibleProvider oacp) {
            oacp.updateConfig(baseUrl, apiKey, model, capabilities);
        } else if (provider instanceof OpenAIProvider op) {
            op.updateConfig(apiKey, model, baseUrl);
        }

        this.activeProviderName = key;
        log.info("Configured and activated AI provider: '{}'", key);
    }

    public String getConfiguredProviderName() {
        return activeProviderName;
    }

    public boolean hasProvider(String providerName) {
        return providerName != null && providers.containsKey(providerName.trim().toLowerCase());
    }

    public String getMaskedApiKey(String providerName) {
        AIProvider provider = providers.get(providerName != null ? providerName.trim().toLowerCase() : activeProviderName);
        if (provider == null) {
            return "";
        }
        String key = null;
        if (provider instanceof OpenAICompatibleProvider oacp) {
            key = oacp.getApiKey();
        } else if (provider instanceof OpenAIProvider op) {
            key = op.getApiKey();
        }
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 8) {
            return "••••••••";
        }
        int dashIdx = key.indexOf('-');
        String prefix = (dashIdx > 0 && dashIdx <= 4) ? key.substring(0, dashIdx + 1) : key.substring(0, 3);
        return prefix + "..." + key.substring(key.length() - 4);
    }

    public Map<String, Object> getCurrentProviderInfo() {
        AIProvider current = getProvider();
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("provider", current.getProviderName());
        info.put("configured", current.isConfigured());
        info.put("hasApiKey", current.isConfigured());
        info.put("capabilities", current.getCapabilities());
        info.put("maskedApiKey", getMaskedApiKey(activeProviderName));

        if (current instanceof OpenAICompatibleProvider oacp) {
            info.put("model", oacp.getModel());
            info.put("baseUrl", oacp.getBaseUrl());
        } else if (current instanceof OpenAIProvider op) {
            info.put("model", op.getModel());
            info.put("baseUrl", op.getBaseUrl());
        }
        return info;
    }
}
