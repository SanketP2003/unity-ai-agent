package com.unityagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.provider.AIProviderFactory;
import com.unityagent.agent.provider.openai.OpenAICompatibleProvider;
import com.unityagent.agent.provider.openai.OpenAIProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AIProviderFactory Unit Tests")
class AIProviderFactoryTest {

    private OpenAIProvider openAIProvider;
    private OpenAICompatibleProvider openAICompatibleProvider;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        openAIProvider = new OpenAIProvider("test-openai-key", "gpt-4o", "https://api.openai.com/v1", 30, mapper);
        openAICompatibleProvider = new OpenAICompatibleProvider("https://integrate.api.nvidia.com/v1", "test-nv-key", "nemotron", 30, null, mapper);
    }

    @Test
    @DisplayName("Should select openai-compatible provider by default when configured")
    void testDefaultOpenAICompatibleSelection() {
        AIProviderFactory factory = new AIProviderFactory("openai-compatible", openAIProvider, openAICompatibleProvider);

        assertEquals("openai-compatible", factory.getConfiguredProviderName());
        AIProvider provider = factory.getProvider();

        assertNotNull(provider);
        assertEquals("openai-compatible", provider.getProviderName());
        assertTrue(provider instanceof OpenAICompatibleProvider);
    }

    @Test
    @DisplayName("Should select openai provider when configured")
    void testOpenAISelection() {
        AIProviderFactory factory = new AIProviderFactory("openai", openAIProvider, openAICompatibleProvider);

        assertEquals("openai", factory.getConfiguredProviderName());
        AIProvider provider = factory.getProvider();

        assertNotNull(provider);
        assertEquals("openai", provider.getProviderName());
        assertTrue(provider instanceof OpenAIProvider);
    }

    @Test
    @DisplayName("Should retrieve provider by explicit name case-insensitively")
    void testExplicitProviderLookup() {
        AIProviderFactory factory = new AIProviderFactory("openai-compatible", openAIProvider, openAICompatibleProvider);

        AIProvider p1 = factory.getProvider("OPENAI");
        assertEquals("openai", p1.getProviderName());

        AIProvider p2 = factory.getProvider("OpenAI-Compatible");
        assertEquals("openai-compatible", p2.getProviderName());

        AIProvider p3 = factory.getProvider("compatible");
        assertEquals("openai-compatible", p3.getProviderName());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on unknown provider name")
    void testUnknownProviderLookup() {
        AIProviderFactory factory = new AIProviderFactory("openai-compatible", openAIProvider, openAICompatibleProvider);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                factory.getProvider("unknown-vendor-xyz"));

        assertTrue(ex.getMessage().contains("Unknown AI provider"));
        assertTrue(ex.getMessage().contains("unknown-vendor-xyz"));
    }

    @Test
    @DisplayName("Should check provider presence with hasProvider")
    void testHasProvider() {
        AIProviderFactory factory = new AIProviderFactory("openai-compatible", openAIProvider, openAICompatibleProvider);

        assertTrue(factory.hasProvider("openai"));
        assertTrue(factory.hasProvider("openai-compatible"));
        assertTrue(factory.hasProvider("compatible"));
        assertTrue(factory.hasProvider("nvidia"));
        assertTrue(factory.hasProvider("ollama"));
        assertFalse(factory.hasProvider("anthropic"));
    }

    @Test
    @DisplayName("Should select nvidia provider when configured via environment")
    void testNvidiaSelection() {
        AIProviderFactory factory = new AIProviderFactory("nvidia", openAIProvider, openAICompatibleProvider);

        assertEquals("nvidia", factory.getConfiguredProviderName());
        AIProvider provider = factory.getProvider();

        assertNotNull(provider);
        assertEquals("openai-compatible", provider.getProviderName());
        assertTrue(provider instanceof OpenAICompatibleProvider);
    }
}
