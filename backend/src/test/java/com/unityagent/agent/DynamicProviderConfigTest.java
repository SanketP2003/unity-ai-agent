package com.unityagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.provider.AIProviderConfig;
import com.unityagent.agent.provider.AIProviderFactory;
import com.unityagent.agent.provider.openai.OpenAICompatibleProvider;
import com.unityagent.agent.provider.openai.OpenAIProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Dynamic Provider Configuration and Key Masking Tests")
class DynamicProviderConfigTest {

    private ObjectMapper mapper;
    private OpenAIProvider openAIProvider;
    private OpenAICompatibleProvider openAICompatibleProvider;
    private AIProviderFactory factory;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        openAIProvider = new OpenAIProvider("sk-initial-secret-key-12345", "gpt-4o", "https://api.openai.com/v1", 30, mapper);
        openAICompatibleProvider = new OpenAICompatibleProvider(
                "https://integrate.api.nvidia.com/v1",
                "nv-initial-secret-key-67890",
                "meta/llama-3.1-70b-instruct",
                30,
                null,
                mapper
        );
        factory = new AIProviderFactory("openai-compatible", openAIProvider, openAICompatibleProvider);
    }

    @Test
    @DisplayName("Should mask API keys properly without leaking full secrets")
    void testMaskedApiKey() {
        String masked = factory.getMaskedApiKey("openai");
        assertNotNull(masked);
        assertTrue(masked.startsWith("sk-..."));
        assertTrue(masked.endsWith("2345"));
        assertFalse(masked.contains("secret-key-1"));

        String maskedNv = factory.getMaskedApiKey("openai-compatible");
        assertNotNull(maskedNv);
        assertTrue(maskedNv.startsWith("nv-..."));
        assertTrue(maskedNv.endsWith("7890"));
    }

    @Test
    @DisplayName("Should dynamically reconfigure provider URL and model")
    void testDynamicReconfigureModelAndUrl() {
        factory.configureProvider("openai-compatible", "https://custom.local:8000/v1", null, "mistral-7b");

        AIProvider current = factory.getProvider();
        assertEquals("openai-compatible", current.getProviderName());
        assertEquals("mistral-7b", current.getModelName());

        Map<String, Object> info = factory.getCurrentProviderInfo();
        assertEquals("https://custom.local:8000/v1", info.get("baseUrl"));
        assertEquals("mistral-7b", info.get("model"));
        assertTrue((Boolean) info.get("hasApiKey"));
    }

    @Test
    @DisplayName("Should dynamically update API key when provided, and preserve when null or empty")
    void testUpdateApiKeyOrPreserve() {
        // Update key
        factory.configureProvider("openai", "https://api.openai.com/v1", "sk-brand-new-secret-9999", "gpt-4o-mini");
        assertEquals("gpt-4o-mini", openAIProvider.getModelName());
        assertTrue(factory.getMaskedApiKey("openai").endsWith("9999"));

        // Blank key keeps the existing key
        factory.configureProvider("openai", "https://api.openai.com/v1", "   ", "gpt-4o-mini");
        assertTrue(factory.getMaskedApiKey("openai").endsWith("9999"));
    }

    @Test
    @DisplayName("Should switch active provider dynamically and reflect in activeAIProvider bean")
    void testSwitchActiveProvider() {
        assertEquals("openai-compatible", factory.getConfiguredProviderName());

        AIProviderConfig config = new AIProviderConfig();
        AIProvider dynamicBean = config.activeAIProvider(factory);

        assertEquals("openai-compatible", dynamicBean.getProviderName());

        // Switch to OpenAI
        factory.configureProvider("openai", "https://api.openai.com/v1", "gpt-4o", null);
        assertEquals("openai", factory.getConfiguredProviderName());
        assertEquals("openai", dynamicBean.getProviderName());
        assertEquals("gpt-4o", dynamicBean.getModelName());
    }

    @Test
    @DisplayName("Provider info should return masked keys and not expose raw secrets")
    void testProviderInfoSanitization() {
        Map<String, Object> info = factory.getCurrentProviderInfo();
        assertFalse(info.containsKey("apiKey"));
        assertTrue(info.containsKey("maskedApiKey"));
        assertTrue(info.containsKey("capabilities"));
    }
}
