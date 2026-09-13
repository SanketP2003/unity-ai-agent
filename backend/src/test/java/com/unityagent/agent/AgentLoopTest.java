package com.unityagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.memory.ConversationMemory;
import com.unityagent.agent.model.*;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.provider.AIProviderException;
import com.unityagent.tools.*;
import com.unityagent.unity.UnityCommandExecutor;
import com.unityagent.unity.UnityMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("AgentLoop Autonomous Execution Tests")
class AgentLoopTest {

    private ToolRegistry toolRegistry;
    private UnityCommandExecutor mockUnityExecutor;
    private ConversationMemory memory;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        memory = new ConversationMemory();
        mockUnityExecutor = Mockito.mock(UnityCommandExecutor.class);

        // Set up registry with mockable tools
        List<Tool> initialTools = new ArrayList<>();

        initialTools.add(new SimpleTestTool("get_scene_hierarchy", "Get scene hierarchy", ToolPermission.SAFE, Set.of(ToolMode.BOTH), params -> null));
        initialTools.add(new SimpleTestTool("set_material_color", "Set material color", ToolPermission.SAFE, Set.of(ToolMode.BOTH), params -> {
            if (params == null || !params.containsKey("color")) {
                return "Missing required parameter 'color'";
            }
            return null;
        }));
        initialTools.add(new SimpleTestTool("create_primitive", "Create primitive", ToolPermission.SAFE, Set.of(ToolMode.BOTH), params -> null));
        initialTools.add(new SimpleTestTool("blocked_tool", "Blocked tool", ToolPermission.BLOCKED, Set.of(ToolMode.BOTH), params -> null));
        initialTools.add(new SimpleTestTool("play_only_tool", "Play only tool", ToolPermission.SAFE, Set.of(ToolMode.PLAY_MODE), params -> null));

        toolRegistry = new ToolRegistry(initialTools);
    }

    /**
     * Requirement 14: Mandatory deterministic AgentLoop test.
     * Simulates:
     * Turn 1: LLM calls get_scene_hierarchy (call_001) -> Unity returns Ground object.
     * Turn 2: LLM receives SYSTEM, USER, ASSISTANT tool_call(call_001), TOOL result(call_001).
     *         LLM calls set_material_color (call_002) -> Unity returns success.
     * Turn 3: LLM receives complete conversation history up to TOOL result(call_002) and returns final text.
     */
    @Test
    @DisplayName("Mandatory Deterministic 3-Iteration AgentLoop Test")
    void testMandatoryDeterministicThreeIterationSequence() throws Exception {
        // Mock Unity WebSocket responses
        when(mockUnityExecutor.execute(anyString(), eq("get_scene_hierarchy"), any()))
                .thenAnswer(inv -> UnityMessage.toolResponse(
                        inv.getArgument(0),
                        true,
                        Map.of("objects", List.of(Map.of("objectId", "obj_101", "name", "Ground", "instanceId", 101)))
                ));

        when(mockUnityExecutor.execute(anyString(), eq("set_material_color"), any()))
                .thenAnswer(inv -> UnityMessage.toolResponse(
                        inv.getArgument(0),
                        true,
                        Map.of("updated", true, "objectId", "obj_101", "color", "#0000FF")
                ));

        List<AgentPrompt> capturedPrompts = new ArrayList<>();

        // Fake AI Provider that simulates the exact 3 iterations
        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;

            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) throws AIProviderException {
                capturedPrompts.add(prompt);
                turn++;
                if (turn == 1) {
                    // Turn 1: Call get_scene_hierarchy
                    ToolCall call1 = new ToolCall("call_001", "get_scene_hierarchy", Map.of(), "{}");
                    return AgentCompletion.toolCalls(List.of(call1));
                } else if (turn == 2) {
                    // Turn 2: Call set_material_color
                    ToolCall call2 = new ToolCall("call_002", "set_material_color",
                            Map.of("objectId", "obj_101", "color", "#0000FF"),
                            "{\"objectId\":\"obj_101\",\"color\":\"#0000FF\"}");
                    return AgentCompletion.toolCalls(List.of(call2));
                } else if (turn == 3) {
                    // Turn 3: Final response
                    return AgentCompletion.text("I have found the Ground object and changed its color to blue.");
                }
                throw new IllegalStateException("Unexpected turn: " + turn);
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, AgentLimits.defaultLimits(), memory, mapper);

        CancellationToken token = new CancellationToken();
        String sessionId = "sess_deterministic";
        String agentRunId = "run_001";
        String userRequest = "Change the ground to blue.";

        AgentRunResult result = loop.run(sessionId, agentRunId, userRequest, token);

        // Verify Run Outcome
        assertTrue(result.isSuccess(), "Agent run should succeed");
        assertEquals("I have found the Ground object and changed its color to blue.", result.getResponse());
        assertEquals(3, result.getIterations());
        assertEquals(2, result.getTotalToolCalls());
        assertEquals(2, result.getToolExecutions().size());

        // Verify Iteration 1 captured prompt
        AgentPrompt prompt1 = capturedPrompts.get(0);
        assertEquals(2, prompt1.getMessages().size());
        assertEquals(ChatMessage.Role.SYSTEM, prompt1.getMessages().get(0).getRole());
        assertEquals(ChatMessage.Role.USER, prompt1.getMessages().get(1).getRole());
        assertEquals("Change the ground to blue.", prompt1.getMessages().get(1).getContent());

        // Verify Iteration 2 captured prompt - Exact semantic conversation continuity
        AgentPrompt prompt2 = capturedPrompts.get(1);
        assertEquals(4, prompt2.getMessages().size(), "Iteration 2 must receive SYSTEM, USER, ASSISTANT tool call, and TOOL result");
        assertEquals(ChatMessage.Role.SYSTEM, prompt2.getMessages().get(0).getRole());
        assertEquals(ChatMessage.Role.USER, prompt2.getMessages().get(1).getRole());

        ChatMessage asstMsg1 = prompt2.getMessages().get(2);
        assertEquals(ChatMessage.Role.ASSISTANT, asstMsg1.getRole());
        assertTrue(asstMsg1.hasToolCalls());
        assertEquals("call_001", asstMsg1.getToolCalls().get(0).getId());
        assertEquals("get_scene_hierarchy", asstMsg1.getToolCalls().get(0).getName());

        ChatMessage toolMsg1 = prompt2.getMessages().get(3);
        assertEquals(ChatMessage.Role.TOOL, toolMsg1.getRole());
        assertEquals("call_001", toolMsg1.getToolCallId(), "TOOL result MUST preserve matching toolCallId");
        assertTrue(toolMsg1.getContent().contains("obj_101"));
        assertTrue(toolMsg1.getContent().contains("Ground"));

        // Verify Iteration 3 captured prompt
        AgentPrompt prompt3 = capturedPrompts.get(2);
        assertEquals(6, prompt3.getMessages().size(), "Iteration 3 must receive 6 messages maintaining full conversation integrity");
        ChatMessage toolMsg2 = prompt3.getMessages().get(5);
        assertEquals(ChatMessage.Role.TOOL, toolMsg2.getRole());
        assertEquals("call_002", toolMsg2.getToolCallId());
        assertTrue(toolMsg2.getContent().contains("updated"));

        // Verify conversation history in memory
        List<ChatMessage> history = memory.getHistory(sessionId);
        assertEquals(7, history.size(), "Memory must have SYSTEM + USER + ASSISTANT(1) + TOOL(1) + ASSISTANT(2) + TOOL(2) + ASSISTANT(final)");
        assertEquals(ChatMessage.Role.ASSISTANT, history.get(6).getRole());
        assertEquals("I have found the Ground object and changed its color to blue.", history.get(6).getContent());
    }

    @Test
    @DisplayName("Should handle final response without tools (single turn)")
    void testFinalResponseWithoutTools() {
        AIProvider fakeProvider = new AIProvider() {
            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                return AgentCompletion.text("Hello! Unity scene is ready.");
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, AgentLimits.defaultLimits(), memory, mapper);
        AgentRunResult result = loop.run("sess_direct", "run_direct", "Hello", new CancellationToken());

        assertTrue(result.isSuccess());
        assertEquals("Hello! Unity scene is ready.", result.getResponse());
        assertEquals(1, result.getIterations());
        assertEquals(0, result.getTotalToolCalls());
        assertTrue(result.getToolExecutions().isEmpty());
    }

    @Test
    @DisplayName("Should execute multiple sequential tool calls in a single turn")
    void testMultipleSequentialToolCallsInSingleTurn() throws Exception {
        when(mockUnityExecutor.execute(anyString(), eq("create_primitive"), any()))
                .thenAnswer(inv -> UnityMessage.toolResponse(
                        inv.getArgument(0),
                        true,
                        Map.of("created", true)
                ));

        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;

            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    ToolCall call1 = new ToolCall("call_plane", "create_primitive", Map.of("type", "Plane"), "{}");
                    ToolCall call2 = new ToolCall("call_sphere", "create_primitive", Map.of("type", "Sphere"), "{}");
                    return AgentCompletion.toolCalls(List.of(call1, call2));
                }
                return AgentCompletion.text("Created plane and sphere.");
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, AgentLimits.defaultLimits(), memory, mapper);
        AgentRunResult result = loop.run("sess_multi", "run_multi", "Create plane and sphere", new CancellationToken());

        assertTrue(result.isSuccess());
        assertEquals(2, result.getIterations());
        assertEquals(2, result.getTotalToolCalls());
        assertEquals(2, result.getToolExecutions().size());

        assertEquals("call_plane", result.getToolExecutions().get(0).getToolCallId());
        assertEquals("call_sphere", result.getToolExecutions().get(1).getToolCallId());
    }

    @Test
    @DisplayName("Should handle unknown tool with structured UNKNOWN_TOOL error and allow LLM recovery")
    void testUnknownToolHandling() {
        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;

            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_unk", "nonexistent_tool", Map.of(), "{}")));
                }
                // Verify that prompt contains the structured error
                ChatMessage lastMsg = prompt.getMessages().get(prompt.getMessages().size() - 1);
                assertTrue(lastMsg.getContent().contains("UNKNOWN_TOOL"));
                return AgentCompletion.text("Recovered from unknown tool error.");
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, AgentLimits.defaultLimits(), memory, mapper);
        AgentRunResult result = loop.run("sess_unk", "run_unk", "Use nonexistent", new CancellationToken());

        assertTrue(result.isSuccess());
        assertEquals("Recovered from unknown tool error.", result.getResponse());
        assertEquals(1, result.getToolExecutions().size());
        assertEquals(ErrorType.UNKNOWN_TOOL, result.getToolExecutions().get(0).getErrorType());
    }

    @Test
    @DisplayName("Should handle permission denied for BLOCKED tool")
    void testPermissionDenial() {
        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;

            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_blk", "blocked_tool", Map.of(), "{}")));
                }
                ChatMessage lastMsg = prompt.getMessages().get(prompt.getMessages().size() - 1);
                assertTrue(lastMsg.getContent().contains("PERMISSION_DENIED"));
                return AgentCompletion.text("Tool is blocked by policy.");
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, AgentLimits.defaultLimits(), memory, mapper);
        AgentRunResult result = loop.run("sess_perm", "run_perm", "Run blocked", new CancellationToken());

        assertTrue(result.isSuccess());
        assertEquals(ErrorType.PERMISSION_DENIED, result.getToolExecutions().get(0).getErrorType());
    }

    @Test
    @DisplayName("Should handle mode not allowed error")
    void testModeNotAllowed() {
        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;

            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_mode", "play_only_tool", Map.of(), "{}")));
                }
                ChatMessage lastMsg = prompt.getMessages().get(prompt.getMessages().size() - 1);
                assertTrue(lastMsg.getContent().contains("MODE_NOT_ALLOWED"));
                return AgentCompletion.text("Cannot run play mode tool in editor.");
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, AgentLimits.defaultLimits(), memory, mapper);
        // Force agent to run in EDITOR mode
        AgentRunResult result = loop.run("sess_mode", "run_mode", "Run play tool", ToolMode.EDITOR, new CancellationToken());

        assertTrue(result.isSuccess());
        assertEquals(ErrorType.MODE_NOT_ALLOWED, result.getToolExecutions().get(0).getErrorType());
    }

    @Test
    @DisplayName("Should handle parameter validation error and allow LLM to correct arguments")
    void testParameterValidationErrorRecovery() throws Exception {
        when(mockUnityExecutor.execute(anyString(), eq("set_material_color"), any()))
                .thenAnswer(inv -> UnityMessage.toolResponse(
                        inv.getArgument(0),
                        true,
                        Map.of("updated", true)
                ));

        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;

            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    // Missing required 'color' parameter
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_bad_arg", "set_material_color", Map.of("objectId", "obj_1"), "{}")));
                } else if (turn == 2) {
                    // Check error reported
                    ChatMessage errResult = prompt.getMessages().get(prompt.getMessages().size() - 1);
                    assertTrue(errResult.getContent().contains("VALIDATION_ERROR"));
                    // Provide corrected parameter
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_good_arg", "set_material_color",
                            Map.of("objectId", "obj_1", "color", "#FF0000"), "{}")));
                }
                return AgentCompletion.text("Color updated after fixing parameters.");
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, AgentLimits.defaultLimits(), memory, mapper);
        AgentRunResult result = loop.run("sess_val", "run_val", "Set red color", new CancellationToken());

        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalToolCalls());
        assertEquals(ErrorType.VALIDATION_ERROR, result.getToolExecutions().get(0).getErrorType());
        assertTrue(result.getToolExecutions().get(1).isSuccess());
    }

    @Test
    @DisplayName("Should halt when exceeding max iterations limit")
    void testMaxIterationsLimit() throws Exception {
        AgentLimits limits = new AgentLimits(3, 5, 3, 30, 300);

        AIProvider infiniteProvider = new AIProvider() {
            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                return AgentCompletion.toolCalls(List.of(new ToolCall("call_loop", "get_scene_hierarchy", Map.of(), "{}")));
            }
        };

        when(mockUnityExecutor.execute(anyString(), anyString(), any()))
                .thenAnswer(inv -> UnityMessage.toolResponse(inv.getArgument(0), true, Map.of()));

        AgentLoop loop = new AgentLoop(infiniteProvider, toolRegistry, mockUnityExecutor, limits, memory, mapper);
        AgentRunResult result = loop.run("sess_iter", "run_iter", "Loop forever", new CancellationToken());

        assertFalse(result.isSuccess());
        assertEquals(ErrorType.TIMEOUT, result.getErrorType());
        assertEquals(3, result.getIterations());
        assertTrue(result.getErrorMessage().contains("Exceeded maximum agent iterations"));
    }

    @Test
    @DisplayName("Should halt when exceeding max consecutive tool failures")
    void testMaxConsecutiveToolFailures() {
        AgentLimits limits = new AgentLimits(10, 2, 5, 30, 300);

        AIProvider failingProvider = new AIProvider() {
            private int turn = 0;

            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                return AgentCompletion.toolCalls(List.of(new ToolCall("call_" + turn, "unk_tool_" + turn, Map.of(), "{}")));
            }
        };

        AgentLoop loop = new AgentLoop(failingProvider, toolRegistry, mockUnityExecutor, limits, memory, mapper);
        AgentRunResult result = loop.run("sess_fail", "run_fail", "Fail twice", new CancellationToken());

        assertFalse(result.isSuccess());
        assertEquals(ErrorType.UNITY_EXECUTION_ERROR, result.getErrorType());
        assertTrue(result.getErrorMessage().contains("Exceeded maximum consecutive tool failures"));
    }

    @Test
    @DisplayName("Should halt when exceeding same-tool retry limit")
    void testMaxSameToolRetries() {
        AgentLimits limits = new AgentLimits(10, 5, 2, 30, 300);

        AIProvider retryProvider = new AIProvider() {
            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                return AgentCompletion.toolCalls(List.of(new ToolCall("call_same", "blocked_tool", Map.of(), "{}")));
            }
        };

        AgentLoop loop = new AgentLoop(retryProvider, toolRegistry, mockUnityExecutor, limits, memory, mapper);
        AgentRunResult result = loop.run("sess_retry", "run_retry", "Retry blocked tool", new CancellationToken());

        assertFalse(result.isSuccess());
        assertEquals(ErrorType.UNITY_EXECUTION_ERROR, result.getErrorType());
        assertTrue(result.getErrorMessage().contains("exceeded maximum retry limit"));
    }

    @Test
    @DisplayName("Should stop execution immediately on CancellationToken cancellation")
    void testCancellation() {
        CancellationToken token = new CancellationToken();
        token.cancel();

        AIProvider fakeProvider = new AIProvider() {
            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                return AgentCompletion.text("Should not run");
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, AgentLimits.defaultLimits(), memory, mapper);
        AgentRunResult result = loop.run("sess_cancel", "run_cancel", "Do something", token);

        assertFalse(result.isSuccess());
        assertTrue(result.isCancelled());
        assertEquals(ErrorType.CANCELLED, result.getErrorType());
        assertTrue(result.getErrorMessage().contains("cancelled"));
    }

    @Test
    @DisplayName("Should verify identifier propagation across runs and executions")
    void testIdentifierPropagation() throws Exception {
        when(mockUnityExecutor.execute(anyString(), eq("get_scene_hierarchy"), any()))
                .thenAnswer(inv -> UnityMessage.toolResponse(
                        inv.getArgument(0),
                        true,
                        Map.of("count", 5)
                ));

        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;

            @Override
            public String getProviderName() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_prop_123", "get_scene_hierarchy", Map.of(), "{}")));
                }
                return AgentCompletion.text("Finished");
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, AgentLimits.defaultLimits(), memory, mapper);
        AgentRunResult result = loop.run("sess_prop_99", "run_prop_88", "Inspect scene", new CancellationToken());

        assertEquals("sess_prop_99", result.getSessionId());
        assertEquals("run_prop_88", result.getAgentRunId());
        assertEquals(1, result.getToolExecutions().size());

        ToolExecutionResult exec = result.getToolExecutions().get(0);
        assertEquals("call_prop_123", exec.getToolCallId());
        assertNotNull(exec.getOperationId());
        assertFalse(exec.getOperationId().isBlank());
    }

    // Helper test tool
    private static class SimpleTestTool implements Tool {
        private final String name;
        private final String description;
        private final ToolPermission permission;
        private final Set<ToolMode> allowedModes;
        private final java.util.function.Function<Map<String, Object>, String> validator;

        SimpleTestTool(String name, String description, ToolPermission permission, Set<ToolMode> allowedModes,
                       java.util.function.Function<Map<String, Object>, String> validator) {
            this.name = name;
            this.description = description;
            this.permission = permission;
            this.allowedModes = allowedModes;
            this.validator = validator;
        }

        @Override
        public String name() { return name; }

        @Override
        public String description() { return description; }

        @Override
        public String validate(Map<String, Object> parameters) {
            return validator != null ? validator.apply(parameters) : null;
        }

        @Override
        public ToolPermission permission() { return permission; }

        @Override
        public Set<ToolMode> allowedModes() { return allowedModes; }
    }
}
