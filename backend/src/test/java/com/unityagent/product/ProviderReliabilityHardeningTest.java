package com.unityagent.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.model.AgentPrompt;
import com.unityagent.agent.model.ErrorType;
import com.unityagent.agent.provider.AIProviderException;
import com.unityagent.agent.provider.openai.OpenAICompatibleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProviderReliabilityHardeningTest {

    private HttpClient mockHttpClient;
    private ObjectMapper mapper;
    private OpenAICompatibleProvider provider;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        mapper = new ObjectMapper();
        provider = new OpenAICompatibleProvider(
                "https://api.testprovider.com/v1",
                "test-api-key-xyz",
                "test-model",
                5,
                mockHttpClient,
                mapper
        );
    }

    private com.unityagent.agent.model.AgentPrompt createSimplePrompt() {
        return com.unityagent.agent.model.AgentPrompt.of(
                java.util.List.of(com.unityagent.agent.model.ChatMessage.user("Test prompt"))
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAuthenticationFailure401() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(401);
        when(mockResp.body()).thenReturn("Invalid API Key");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResp);

        AIProviderException ex = assertThrows(AIProviderException.class, () -> provider.generate(createSimplePrompt()));
        assertEquals(401, ex.getStatusCode());
        assertEquals(ErrorType.PROVIDER_ERROR, ex.getErrorType());
        assertTrue(ex.getMessage().contains("Authentication failed"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEndpointOrModelNotFound404() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(404);
        when(mockResp.body()).thenReturn("Model 'test-model' not found");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResp);

        AIProviderException ex = assertThrows(AIProviderException.class, () -> provider.generate(createSimplePrompt()));
        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Endpoint or model not found"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRateLimitExceeded429() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(429);
        when(mockResp.body()).thenReturn("Rate limit exceeded for requests per minute");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResp);

        AIProviderException ex = assertThrows(AIProviderException.class, () -> provider.generate(createSimplePrompt()));
        assertEquals(429, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Rate limit exceeded"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testUnsupportedToolCalling400() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(400);
        when(mockResp.body()).thenReturn("Tools are not supported by this model architecture");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResp);

        AIProviderException ex = assertThrows(AIProviderException.class, () -> provider.generate(createSimplePrompt()));
        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("tool-calling support"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProviderUnavailable503() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(503);
        when(mockResp.body()).thenReturn("Service Temporarily Unavailable");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResp);

        AIProviderException ex = assertThrows(AIProviderException.class, () -> provider.generate(createSimplePrompt()));
        assertEquals(503, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("provider unavailable (503)"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testConnectExceptionConnectionRefused() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new ConnectException("Connection refused to 127.0.0.1:8000"));

        AIProviderException ex = assertThrows(AIProviderException.class, () -> provider.generate(createSimplePrompt()));
        assertEquals(503, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("connection refused"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testHttpTimeout() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("Request timed out"));

        AIProviderException ex = assertThrows(AIProviderException.class, () -> provider.generate(createSimplePrompt()));
        assertEquals(408, ex.getStatusCode());
        assertEquals(ErrorType.TIMEOUT, ex.getErrorType());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSecretScrubbingInErrorPayloads() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(400);
        when(mockResp.body()).thenReturn("Error for Bearer nvapi-1234567890abcdef123456 with api_key=sk-1234567890abcdef123456");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResp);

        AIProviderException ex = assertThrows(AIProviderException.class, () -> provider.generate(createSimplePrompt()));

        assertFalse(ex.getMessage().contains("nvapi-1234567890abcdef123456"));
        assertFalse(ex.getMessage().contains("sk-1234567890abcdef123456"));
        assertTrue(ex.getMessage().contains("[SCRUBBED]"));
    }

    @Test
    void testStaticScrubberDirectly() {
        String input = "Failed request with Bearer secret-tok-123456 and api-key=abc-12345-def and sk-abcdefghijklmnop123";
        String scrubbed = OpenAICompatibleProvider.scrub(input);

        assertFalse(scrubbed.contains("secret-tok-123456"));
        assertFalse(scrubbed.contains("abc-12345-def"));
        assertFalse(scrubbed.contains("sk-abcdefghijklmnop123"));
        assertTrue(scrubbed.contains("[SCRUBBED]"));
    }
}
