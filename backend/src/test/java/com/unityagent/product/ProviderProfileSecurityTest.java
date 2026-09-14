package com.unityagent.product;

import com.unityagent.product.model.ProviderProfile;
import com.unityagent.product.service.ConfigurationManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Provider Profile Security & Zero-Secret Boundary Tests")
class ProviderProfileSecurityTest {

    @Test
    @DisplayName("Verify provider profile model safely encapsulates non-secret settings")
    void testProviderProfileSafeFields() {
        ProviderProfile profile = new ProviderProfile(
                "prov_nim_1", "NVIDIA NIM Llama-3.1", "openai-compatible",
                "https://integrate.api.nvidia.com/v1", "meta/llama-3.1-70b-instruct"
        );

        assertEquals("prov_nim_1", profile.getProfileId());
        assertEquals("openai-compatible", profile.getProviderType());
        assertEquals("https://integrate.api.nvidia.com/v1", profile.getBaseUrl());
        assertEquals("meta/llama-3.1-70b-instruct", profile.getDefaultModel());
    }

    @Test
    @DisplayName("Verify zero-secret validation detects and rejects API keys in extraProperties")
    void testZeroSecretRejectionInExtraProperties() {
        ConfigurationManager configMgr = new ConfigurationManager(null);

        Map<String, Object> props = new HashMap<>();
        props.put("apiKey", "sk-proj-1234567890abcdef1234567890");

        SecurityException ex = assertThrows(SecurityException.class, () ->
                configMgr.validateNoSecrets(props));
        assertTrue(ex.getMessage().contains("Security violation"));
    }

    @Test
    @DisplayName("Verify zero-secret validation allows benign configuration properties")
    void testZeroSecretAllowsBenignProperties() {
        ConfigurationManager configMgr = new ConfigurationManager(null);

        Map<String, Object> props = new HashMap<>();
        props.put("temperature", 0.7);
        props.put("max_tokens", 4096);
        props.put("endpoint_region", "us-west-2");

        assertDoesNotThrow(() -> configMgr.validateNoSecrets(props));
    }
}
