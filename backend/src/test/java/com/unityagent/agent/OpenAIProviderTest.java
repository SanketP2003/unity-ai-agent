package com.unityagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.model.*;
import com.unityagent.agent.provider.AIProviderException;
import com.unityagent.agent.provider.openai.OpenAIProvider;
import com.unityagent.agent.provider.openai.OpenAIToolMapper;
import com.unityagent.tools.ToolDefinition;
import com.unityagent.tools.ToolMode;
import com.unityagent.tools.ToolPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("OpenAIProvider & ToolMapper Tests")
class OpenAIProviderTest {

    private ObjectMapper mapper;
    private OpenAIToolMapper toolMapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        toolMapper = new OpenAIToolMapper(mapper);
    }

    @Test
    @DisplayName("Should report unconfigured when API key is missing and reject generation")
    void testMissingApiKey() {
        OpenAIProvider provider = new OpenAIProvider("", "gpt-4o", "https://api.openai.com/v1", 30, mapper);

        assertFalse(provider.isConfigured());
        AIProviderException ex = assertThrows(AIProviderException.class, () ->
                provider.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));

        assertEquals(401, ex.getStatusCode());
        assertEquals(ErrorType.PROVIDER_ERROR, ex.getErrorType());
        assertTrue(ex.getMessage().contains("OPENAI_API_KEY"));
    }

    @Test
    @DisplayName("Should map request messages and tools correctly to OpenAI wire format")
    void testRequestMapping() {
        ToolDefinition toolDef = new ToolDefinition(
                "create_cube",
                "Creates a cube",
                Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))),
                ToolPermission.SAFE,
                Set.of(ToolMode.BOTH),
                false,
                30
        );

        List<ChatMessage> messages = List.of(
                ChatMessage.system("System prompt"),
                ChatMessage.user("User prompt"),
                ChatMessage.assistantToolCalls(null, List.of(new ToolCall("call_1", "create_cube", Map.of("name", "Cube1"), "{\"name\":\"Cube1\"}"))),
                ChatMessage.toolResult("call_1", "create_cube", "{\"success\":true,\"objectId\":\"obj_1\"}")
        );

        AgentPrompt prompt = new AgentPrompt(messages, List.of(toolDef), 0.2, 1000);

        Map<String, Object> body = toolMapper.toRequestBody("gpt-4o", prompt);

        assertEquals("gpt-4o", body.get("model"));
        assertEquals(0.2, (Double) body.get("temperature"), 0.001);
        assertEquals(1000, body.get("max_tokens"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messagesList = (List<Map<String, Object>>) body.get("messages");
        assertEquals(4, messagesList.size());

        // SYSTEM
        assertEquals("system", messagesList.get(0).get("role"));
        assertEquals("System prompt", messagesList.get(0).get("content"));

        // USER
        assertEquals("user", messagesList.get(1).get("role"));
        assertEquals("User prompt", messagesList.get(1).get("content"));

        // ASSISTANT with tool call
        assertEquals("assistant", messagesList.get(2).get("role"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) messagesList.get(2).get("tool_calls");
        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.size());
        assertEquals("call_1", toolCalls.get(0).get("id"));
        assertEquals("function", toolCalls.get(0).get("type"));

        // TOOL result
        assertEquals("tool", messagesList.get(3).get("role"));
        assertEquals("call_1", messagesList.get(3).get("tool_call_id"));
        assertEquals("{\"success\":true,\"objectId\":\"obj_1\"}", messagesList.get(3).get("content"));

        // TOOLS array
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertEquals(1, tools.size());
        assertEquals("function", tools.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> func = (Map<String, Object>) tools.get(0).get("function");
        assertEquals("create_cube", func.get("name"));
    }

    @Test
    @DisplayName("Should parse tool call from OpenAI completion response")
    void testToolCallParsing() throws Exception {
        String responseJson = """
            {
              "id": "chatcmpl-123",
              "object": "chat.completion",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": null,
                    "tool_calls": [
                      {
                        "id": "call_abc123",
                        "type": "function",
                        "function": {
                          "name": "get_scene_hierarchy",
                          "arguments": "{\\"includeInactive\\":true}"
                        }
                      }
                    ]
                  },
                  "finish_reason": "tool_calls"
                }
              ],
              "usage": {
                "prompt_tokens": 150,
                "completion_tokens": 25,
                "total_tokens": 175
              }
            }
            """;

        AgentCompletion completion = toolMapper.parseResponseBody(responseJson);

        assertTrue(completion.hasToolCalls());
        assertEquals(1, completion.getToolCalls().size());
        assertEquals("tool_calls", completion.getFinishReason());

        ToolCall call = completion.getToolCalls().get(0);
        assertEquals("call_abc123", call.getId());
        assertEquals("get_scene_hierarchy", call.getName());
        assertEquals(Boolean.TRUE, call.getArguments().get("includeInactive"));

        assertEquals(150, completion.getUsage().promptTokens());
        assertEquals(25, completion.getUsage().completionTokens());
        assertEquals(175, completion.getUsage().totalTokens());
    }

    @Test
    @DisplayName("Should parse multiple tool calls from OpenAI completion response")
    void testMultipleToolCallsParsing() throws Exception {
        String responseJson = """
            {
              "id": "chatcmpl-multi",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Creating objects...",
                    "tool_calls": [
                      {
                        "id": "call_001",
                        "type": "function",
                        "function": {
                          "name": "create_primitive",
                          "arguments": "{\\"type\\":\\"Plane\\",\\"name\\":\\"Ground\\"}"
                        }
                      },
                      {
                        "id": "call_002",
                        "type": "function",
                        "function": {
                          "name": "create_primitive",
                          "arguments": "{\\"type\\":\\"Sphere\\",\\"name\\":\\"Ball1\\"}"
                        }
                      }
                    ]
                  },
                  "finish_reason": "tool_calls"
                }
              ],
              "usage": {
                "prompt_tokens": 200,
                "completion_tokens": 50,
                "total_tokens": 250
              }
            }
            """;

        AgentCompletion completion = toolMapper.parseResponseBody(responseJson);

        assertTrue(completion.hasToolCalls());
        assertEquals(2, completion.getToolCalls().size());
        assertEquals("Creating objects...", completion.getContent());

        ToolCall tc1 = completion.getToolCalls().get(0);
        assertEquals("call_001", tc1.getId());
        assertEquals("create_primitive", tc1.getName());
        assertEquals("Plane", tc1.getArguments().get("type"));

        ToolCall tc2 = completion.getToolCalls().get(1);
        assertEquals("call_002", tc2.getId());
        assertEquals("create_primitive", tc2.getName());
        assertEquals("Sphere", tc2.getArguments().get("type"));
    }

    @Test
    @DisplayName("Should handle HTTP error from OpenAI API")
    @SuppressWarnings("unchecked")
    void testHttpErrorHandling() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(429);
        when(mockResponse.body()).thenReturn("{\"error\": {\"message\": \"Rate limit reached\"}}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAIProvider provider = new OpenAIProvider("test-key", "gpt-4o", "https://api.openai.com/v1", 30, mockClient, mapper);

        AIProviderException ex = assertThrows(AIProviderException.class, () ->
                provider.generate(AgentPrompt.of(List.of(ChatMessage.user("Hello")))));

        assertEquals(429, ex.getStatusCode());
        assertEquals(ErrorType.PROVIDER_ERROR, ex.getErrorType());
        assertTrue(ex.getMessage().contains("429"));
    }

    @Test
    @DisplayName("Should handle OpenAI error payload in 200 response")
    void testErrorPayloadParsing() {
        String errorJson = "{\"error\": {\"message\": \"Model not found\", \"type\": \"invalid_request_error\"}}";

        Exception ex = assertThrows(RuntimeException.class, () -> toolMapper.parseResponseBody(errorJson));
        assertTrue(ex.getMessage().contains("Model not found"));
    }
}
