package com.unityagent.agent.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.provider.openai.OpenAICompatibleProvider;
import com.unityagent.agent.provider.openai.OpenAIProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProviderCapabilities and AIProvider Capability Tests")
class ProviderCapabilitiesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("ProviderCapabilities default values")
    void testDefaultCapabilities() {
        ProviderCapabilities caps = ProviderCapabilities.defaults();
        assertTrue(caps.supportsToolCalling());
        assertTrue(caps.supportsStructuredOutput());
        assertTrue(caps.supportsStreaming());
        assertFalse(caps.supportsVision());
    }

    @Test
    @DisplayName("ProviderCapabilities builder custom configuration")
    void testCustomCapabilities() {
        ProviderCapabilities caps = ProviderCapabilities.builder()
                .supportsToolCalling(false)
                .supportsStructuredOutput(true)
                .supportsStreaming(false)
                .supportsVision(true)
                .build();

        assertFalse(caps.supportsToolCalling());
        assertTrue(caps.supportsStructuredOutput());
        assertFalse(caps.supportsStreaming());
        assertTrue(caps.supportsVision());
    }

    @Test
    @DisplayName("OpenAIProvider reports standard vision and tool calling capabilities")
    void testOpenAIProviderCapabilities() {
        OpenAIProvider provider = new OpenAIProvider("sk-test", "gpt-4o", "https://api.openai.com/v1", 30, mapper);
        ProviderCapabilities caps = provider.getCapabilities();

        assertNotNull(caps);
        assertTrue(caps.supportsToolCalling());
        assertTrue(caps.supportsStructuredOutput());
        assertTrue(caps.supportsStreaming());
        assertTrue(caps.supportsVision());
    }

    @Test
    @DisplayName("OpenAICompatibleProvider reports configurable capabilities")
    void testOpenAICompatibleProviderCapabilities() {
        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "https://integrate.api.nvidia.com/v1",
                "nv-test",
                "meta/llama-3.1-70b-instruct",
                30,
                null,
                mapper
        );
        ProviderCapabilities caps = provider.getCapabilities();

        assertNotNull(caps);
        assertTrue(caps.supportsToolCalling());
        assertTrue(caps.supportsStreaming());
        assertFalse(caps.supportsVision());
    }

    @Test
    @DisplayName("OpenAIProvider validation rejects empty model name")
    void testOpenAIProviderValidationFailsOnEmptyModel() {
        OpenAIProvider provider = new OpenAIProvider("sk-test", "", "https://api.openai.com/v1", 30, mapper);
        AIProviderException ex = assertThrows(AIProviderException.class, provider::validateConfiguration);
        assertTrue(ex.getMessage().contains("Model name is required"));
    }

    @Test
    @DisplayName("OpenAIProvider validation rejects invalid URL")
    void testOpenAIProviderValidationFailsOnInvalidUrl() {
        OpenAIProvider provider = new OpenAIProvider("sk-test", "gpt-4o", "invalid-url", 30, mapper);
        AIProviderException ex = assertThrows(AIProviderException.class, provider::validateConfiguration);
        assertTrue(ex.getMessage().contains("Base URL must start with http:// or https://"));
    }

    @Test
    @DisplayName("OpenAICompatibleProvider validation succeeds on valid URL and model")
    void testOpenAICompatibleProviderValidationSucceeds() {
        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "https://integrate.api.nvidia.com/v1",
                "nv-test",
                "meta/llama-3.1-70b-instruct",
                30,
                null,
                mapper
        );
        assertDoesNotThrow(provider::validateConfiguration);
    }
}
