package com.unityagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.model.*;
import com.unityagent.agent.provider.AIProviderException;
import com.unityagent.agent.provider.openai.OpenAICompatibleProvider;
import com.unityagent.agent.provider.openai.OpenAIToolMapper;
import com.unityagent.tools.ToolDefinition;
import com.unityagent.tools.ToolMode;
import com.unityagent.tools.ToolPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OpenAICompatibleProvider Unit Tests")
class OpenAICompatibleProviderTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should detect missing configuration items correctly")
    void testMissingConfiguration() {
        // Missing baseURL
        OpenAICompatibleProvider p1 = new OpenAICompatibleProvider("", "key123", "nemotron-4-340b", 30, null, mapper);
        assertFalse(p1.isConfigured());
        AIProviderException ex1 = assertThrows(AIProviderException.class, () ->
                p1.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));
        assertEquals(400, ex1.getStatusCode());
        assertTrue(ex1.getMessage().contains("base URL is missing"));

        // Missing model
        OpenAICompatibleProvider p2 = new OpenAICompatibleProvider("https://api.example.com/v1", "key123", "", 30, null, mapper);
        assertFalse(p2.isConfigured());
        AIProviderException ex2 = assertThrows(AIProviderException.class, () ->
                p2.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));
        assertEquals(400, ex2.getStatusCode());
        assertTrue(ex2.getMessage().contains("model is missing"));

        // Missing API key
        OpenAICompatibleProvider p3 = new OpenAICompatibleProvider("https://api.example.com/v1", "", "nemotron-4-340b", 30, null, mapper);
        assertFalse(p3.isConfigured());
        AIProviderException ex3 = assertThrows(AIProviderException.class, () ->
                p3.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));
        assertEquals(401, ex3.getStatusCode());
        assertTrue(ex3.getMessage().contains("API key is missing"));

        // Fully configured
        OpenAICompatibleProvider p4 = new OpenAICompatibleProvider("https://api.example.com/v1", "key123", "nemotron-4-340b", 30, null, mapper);
        assertTrue(p4.isConfigured());
        assertEquals("openai-compatible", p4.getProviderName());
    }

    @Test
    @DisplayName("Should normalize endpoint URL and serialize request properly")
    @SuppressWarnings("unchecked")
    void testEndpointNormalizationAndRequestSerialization() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);

        String successJson = """
            {
              "id": "chatcmpl-comp-1",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "I can help with Unity."
                  },
                  "finish_reason": "stop"
                }
              ]
            }
            """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(successJson);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        // Test with trailing slash in base URL
        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "https://integrate.api.nvidia.com/v1/", "nvapi-secret-key", "nvidia/nemotron-4-340b-instruct", 45, mockClient, mapper);

        ToolDefinition toolDef = new ToolDefinition(
                "get_scene_hierarchy",
                "Inspect scene",
                Map.of("type", "object", "properties", Map.of()),
                ToolPermission.SAFE,
                Set.of(ToolMode.BOTH),
                false,
                30
        );

        AgentPrompt prompt = new AgentPrompt(
                List.of(ChatMessage.system("System prompt"), ChatMessage.user("Inspect scene")),
                List.of(toolDef),
                0.3,
                1024
        );

        AgentCompletion completion = provider.generate(prompt);

        assertNotNull(completion);
        assertEquals("I can help with Unity.", completion.getContent());
        assertFalse(completion.hasToolCalls());

        // Verify request sent to normalized endpoint with correct headers
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest sentRequest = requestCaptor.getValue();
        assertEquals("https://integrate.api.nvidia.com/v1/chat/completions", sentRequest.uri().toString());
        assertEquals("Bearer nvapi-secret-key", sentRequest.headers().firstValue("Authorization").orElse(null));
        assertEquals("application/json", sentRequest.headers().firstValue("Content-Type").orElse(null));
    }

    @Test
    @DisplayName("Should parse tool calls with string or object arguments from OpenAI-compatible endpoint")
    @SuppressWarnings("unchecked")
    void testToolCallParsing() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);

        String toolCallJson = """
            {
              "id": "chatcmpl-comp-tools",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": null,
                    "tool_calls": [
                      {
                        "id": "call_nem_01",
                        "type": "function",
                        "function": {
                          "name": "create_primitive",
                          "arguments": "{\\"type\\":\\"Plane\\",\\"name\\":\\"Ground\\"}"
                        }
                      },
                      {
                        "id": "call_nem_02",
                        "type": "function",
                        "function": {
                          "name": "set_material_color",
                          "arguments": {
                            "objectId": "obj_1",
                            "color": "#00FF00"
                          }
                        }
                      }
                    ]
                  },
                  "finish_reason": "tool_calls"
                }
              ],
              "usage": {
                "prompt_tokens": 120,
                "completion_tokens": 40,
                "total_tokens": 160
              }
            }
            """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(toolCallJson);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "https://api.together.xyz/v1", "test-key", "meta-llama/Llama-3-70b-chat", 30, mockClient, mapper);

        AgentCompletion completion = provider.generate(AgentPrompt.of(List.of(ChatMessage.user("Create green ground"))));

        assertTrue(completion.hasToolCalls());
        assertEquals(2, completion.getToolCalls().size());

        ToolCall tc1 = completion.getToolCalls().get(0);
        assertEquals("call_nem_01", tc1.getId());
        assertEquals("create_primitive", tc1.getName());
        assertEquals("Plane", tc1.getArguments().get("type"));

        ToolCall tc2 = completion.getToolCalls().get(1);
        assertEquals("call_nem_02", tc2.getId());
        assertEquals("set_material_color", tc2.getName());
        assertEquals("obj_1", tc2.getArguments().get("objectId"));
        assertEquals("#00FF00", tc2.getArguments().get("color"));

        assertEquals(120, completion.getUsage().promptTokens());
        assertEquals(40, completion.getUsage().completionTokens());
        assertEquals(160, completion.getUsage().totalTokens());
    }

    @Test
    @DisplayName("Should detect when model does not support tool calling (Requirement 12)")
    @SuppressWarnings("unchecked")
    void testUnsupportedToolCallingError() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("{\"error\": {\"message\": \"tools parameter is not supported for this model\"}}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "https://api.example.com/v1", "test-key", "unsupported-model", 30, mockClient, mapper);

        AIProviderException ex = assertThrows(AIProviderException.class, () ->
                provider.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Selected model does not provide compatible tool-calling support"));
    }

    @Test
    @DisplayName("Should handle HTTP 401 Authentication Failure")
    @SuppressWarnings("unchecked")
    void testAuthenticationFailure() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(401);
        when(mockResponse.body()).thenReturn("{\"error\": {\"message\": \"Invalid API key\"}}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "https://api.example.com/v1", "bad-key", "nemotron", 30, mockClient, mapper);

        AIProviderException ex = assertThrows(AIProviderException.class, () ->
                provider.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));

        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Authentication failed"));
        assertTrue(ex.getMessage().contains("AI_API_KEY"));
    }

    @Test
    @DisplayName("Should handle HTTP 429 Rate Limit")
    @SuppressWarnings("unchecked")
    void testRateLimit() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(429);
        when(mockResponse.body()).thenReturn("{\"error\": {\"message\": \"Quota exceeded\"}}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "https://api.example.com/v1", "test-key", "nemotron", 30, mockClient, mapper);

        AIProviderException ex = assertThrows(AIProviderException.class, () ->
                provider.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));

        assertEquals(429, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Rate limit exceeded"));
    }

    @Test
    @DisplayName("Should handle HTTP 500 Server Error")
    @SuppressWarnings("unchecked")
    void testServerError() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal server error");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "https://api.example.com/v1", "test-key", "nemotron", 30, mockClient, mapper);

        AIProviderException ex = assertThrows(AIProviderException.class, () ->
                provider.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));

        assertEquals(500, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    @DisplayName("Should handle timeout error")
    @SuppressWarnings("unchecked")
    void testTimeoutHandling() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("Connection timed out"));

        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "https://api.example.com/v1", "test-key", "nemotron", 10, mockClient, mapper);

        AIProviderException ex = assertThrows(AIProviderException.class, () ->
                provider.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));

        assertEquals(408, ex.getStatusCode());
        assertEquals(ErrorType.TIMEOUT, ex.getErrorType());
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    @DisplayName("Should handle provider unavailable (connection refused)")
    @SuppressWarnings("unchecked")
    void testConnectionRefused() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new ConnectException("Connection refused: no further information"));

        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "http://localhost:11434/v1", "test-key", "llama3", 10, mockClient, mapper);

        AIProviderException ex = assertThrows(AIProviderException.class, () ->
                provider.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));

        assertEquals(503, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("connection refused"));
    }

    @Test
    @DisplayName("Should handle malformed or empty provider response")
    @SuppressWarnings("unchecked")
    void testMalformedResponseHandling() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("   ");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                "https://api.example.com/v1", "test-key", "nemotron", 30, mockClient, mapper);

        AIProviderException ex = assertThrows(AIProviderException.class, () ->
                provider.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));

        assertEquals(502, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Malformed or empty response"));
    }

    @Test
    @DisplayName("Should scrub Bearer, API key, sk-*, and nvapi-* secrets using precompiled patterns")
    void testSecretScrubbingWithPrecompiledPatterns() {
        String input = "Error: Bearer my_secret_bearer_token_123 failed; api_key=\"super_secret_key_456\"; " +
                "key1=sk-abcdefghijklmnopqrstuvwxyz and key2=nvapi-1234567890abcdef12345";
        String scrubbed = OpenAICompatibleProvider.scrub(input);

        assertFalse(scrubbed.contains("my_secret_bearer_token_123"));
        assertFalse(scrubbed.contains("super_secret_key_456"));
        assertFalse(scrubbed.contains("sk-abcdefghijklmnopqrstuvwxyz"));
        assertFalse(scrubbed.contains("nvapi-1234567890abcdef12345"));

        assertTrue(scrubbed.contains("Bearer [SCRUBBED]"));
        assertTrue(scrubbed.contains("api_key=\"[SCRUBBED]\"") || scrubbed.contains("[SCRUBBED]"));
        assertTrue(scrubbed.contains("sk-[SCRUBBED]"));
        assertTrue(scrubbed.contains("nvapi-[SCRUBBED]"));
    }

    @Test
    @DisplayName("Should sanitize control tokens and parse repaired arguments in OpenAIToolMapper")
    void testToolMapperSanitizationAndRepairedArguments() throws Exception {
        // Test sanitizeContent
        String rawContent = "<|im_start|>to=functions.create_cube\n<|action|>Valid Content<|im_end|>";
        String cleanContent = OpenAIToolMapper.sanitizeContent(rawContent);
        assertEquals("Valid Content", cleanContent);

        // Test sanitizeName
        String cleanName = OpenAIToolMapper.sanitizeName("<|special|>functions.create_primitivecommentary");
        assertEquals("create_primitive", cleanName);

        // Test sanitizeId
        String cleanId = OpenAIToolMapper.sanitizeId("call_<|tok|>123@#$");
        assertEquals("call_123", cleanId);

        // Test parseResponseBody with markdown code fences and numeric argument repair
        String responseWithCodeFence = """
            {
              "id": "chatcmpl-test",
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": null,
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "create_cube",
                          "arguments": "```json\\n{\\"size\\": 5, \\"name\\": \\"BigCube\\"}\\n```"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """;
        OpenAIToolMapper toolMapper = new OpenAIToolMapper(mapper);
        var completion = toolMapper.parseResponseBody(responseWithCodeFence);
        assertEquals(1, completion.getToolCalls().size());
        assertEquals("create_cube", completion.getToolCalls().get(0).getName());
        assertEquals(5, completion.getToolCalls().get(0).getArguments().get("size"));
        assertEquals("BigCube", completion.getToolCalls().get(0).getArguments().get("name"));
    }
}
