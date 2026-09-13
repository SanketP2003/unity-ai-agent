package com.unityagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.event.AgentEvent;
import com.unityagent.agent.event.AgentEventListener;
import com.unityagent.agent.memory.ConversationMemory;
import com.unityagent.agent.model.*;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.provider.AIProviderException;
import com.unityagent.agent.security.ScriptCheckpointRegistry;
import com.unityagent.agent.security.ScriptSafetyValidator;
import com.unityagent.agent.service.AgentRunState;
import com.unityagent.agent.service.AgentService;
import com.unityagent.agent.service.SessionConflictException;
import com.unityagent.tools.*;
import com.unityagent.unity.UnityCommandExecutor;
import com.unityagent.unity.UnityMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("Phase 6 Autonomous Recovery Loop & Scenarios")
class Phase6RecoveryLoopTest {

    private ToolRegistry toolRegistry;
    private UnityCommandExecutor mockUnityExecutor;
    private ConversationMemory memory;
    private ObjectMapper mapper;
    private ScriptCheckpointRegistry checkpointRegistry;
    private AgentLimits limits;

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

        @Override public String name() { return name; }
        @Override public String description() { return description; }
        @Override public String validate(Map<String, Object> parameters) { return validator != null ? validator.apply(parameters) : null; }
        @Override public ToolPermission permission() { return permission; }
        @Override public Set<ToolMode> allowedModes() { return allowedModes; }
    }

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        memory = new ConversationMemory();
        checkpointRegistry = new ScriptCheckpointRegistry();
        mockUnityExecutor = Mockito.mock(UnityCommandExecutor.class);
        limits = new AgentLimits(30, 5, 3, 30, 600, 0.7, 4096, 3, 2, 2, 30, 20, 120);

        ScriptSafetyValidator safetyValidator = new ScriptSafetyValidator();
        List<Tool> tools = new ArrayList<>();
        tools.add(new SimpleTestTool("get_project_info", "Get project info", ToolPermission.SAFE, Set.of(ToolMode.BOTH), p -> null));
        tools.add(new SimpleTestTool("get_scene_hierarchy", "Get hierarchy", ToolPermission.SAFE, Set.of(ToolMode.BOTH), p -> null));
        tools.add(new SimpleTestTool("create_primitive", "Create primitive", ToolPermission.SAFE, Set.of(ToolMode.BOTH), p -> null));
        tools.add(new SimpleTestTool("create_script", "Create script", ToolPermission.SUPERVISED, Set.of(ToolMode.EDITOR, ToolMode.BOTH), p -> {
            if (p != null && p.containsKey("path") && p.containsKey("content")) {
                var res = safetyValidator.validate(String.valueOf(p.get("path")), String.valueOf(p.get("content")));
                return res.isValid() ? null : res.getViolationMessage();
            }
            return null;
        }));
        tools.add(new SimpleTestTool("read_script", "Read script", ToolPermission.SAFE, Set.of(ToolMode.BOTH), p -> null));
        tools.add(new SimpleTestTool("update_script", "Update script", ToolPermission.SUPERVISED, Set.of(ToolMode.EDITOR, ToolMode.BOTH), p -> null));
        tools.add(new SimpleTestTool("compile_project", "Compile project", ToolPermission.SAFE, Set.of(ToolMode.EDITOR, ToolMode.BOTH), p -> null));
        tools.add(new SimpleTestTool("enter_play_mode", "Enter play mode", ToolPermission.SAFE, Set.of(ToolMode.EDITOR, ToolMode.BOTH), p -> null));
        tools.add(new SimpleTestTool("run_game_test", "Run game test", ToolPermission.SAFE, Set.of(ToolMode.PLAY_MODE, ToolMode.BOTH), p -> null));
        tools.add(new SimpleTestTool("exit_play_mode", "Exit play mode", ToolPermission.SAFE, Set.of(ToolMode.PLAY_MODE, ToolMode.BOTH), p -> null));
        tools.add(new SimpleTestTool("validate_game_state", "Validate game state", ToolPermission.SAFE, Set.of(ToolMode.BOTH), p -> null));

        toolRegistry = new ToolRegistry(tools);
    }

    @Test
    @DisplayName("Scenario A: Goal -> Inspect -> Build -> Validate -> Complete")
    void testScenarioA_HappyPath() throws Exception {
        when(mockUnityExecutor.execute(anyString(), eq("get_project_info"), any()))
                .thenReturn(UnityMessage.toolResponse("op1", true, Map.of("unityVersion", "6000.4.7f1", "activeScene", "SampleScene")));

        when(mockUnityExecutor.execute(anyString(), eq("create_primitive"), any()))
                .thenReturn(UnityMessage.toolResponse("op2", true, Map.of("name", "Player", "objectId", "obj_player")));

        when(mockUnityExecutor.execute(anyString(), eq("validate_game_state"), any()))
                .thenReturn(UnityMessage.toolResponse("op3", true, Map.of("allRequirementsSatisfied", true, "satisfiedCount", 2, "totalCount", 2)));

        List<String> eventLog = new ArrayList<>();
        AgentEventListener testListener = new AgentEventListener() {
            @Override public void onRunStarted(String s, String r, String m) { eventLog.add("RUN_STARTED"); }
            @Override public void onGoalCreated(String s, String r, String d) { eventLog.add("GOAL_CREATED"); }
            @Override public void onValidationStarted(String s, String r, String v) { eventLog.add("VALIDATION_STARTED"); }
            @Override public void onValidationCompleted(String s, String r, boolean ok, Object d) { eventLog.add("VALIDATION_COMPLETED"); }
            @Override public void onGoalCompleted(String s, String r, Object res) { eventLog.add("GOAL_COMPLETED"); }
            @Override public void onRunCompleted(String s, String r, AgentRunResult res) { eventLog.add("RUN_COMPLETED"); }
            @Override public void onActivity(String r, String a) {}
            @Override public void onToolStarted(String r, String id, String t, Map<String, Object> a) {}
            @Override public void onToolCompleted(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onToolFailed(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onRunFailed(String s, String r, String e) {}
            @Override public void onRunCancelled(String s, String r) {}
        };

        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;
            @Override public String getProviderName() { return "fake"; }
            @Override public boolean isConfigured() { return true; }
            @Override public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_1", "get_project_info", Map.of(), "{}")));
                } else if (turn == 2) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_2", "create_primitive", Map.of("type", "Sphere", "name", "Player"), "{}")));
                } else if (turn == 3) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_3", "validate_game_state", Map.of(), "{}")));
                } else {
                    return AgentCompletion.text("Built Player and verified successfully.");
                }
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, limits, memory, mapper, checkpointRegistry);
        AgentRunResult result = loop.run("sess_A", "run_A", "Build me a small platform game", ToolMode.BOTH, new CancellationToken(), testListener);

        assertTrue(result.isSuccess());
        assertEquals("Built Player and verified successfully.", result.getResponse());
        assertTrue(eventLog.contains("GOAL_CREATED"));
        assertTrue(eventLog.contains("VALIDATION_STARTED"));
        assertTrue(eventLog.contains("VALIDATION_COMPLETED"));
        assertTrue(eventLog.contains("GOAL_COMPLETED"));
    }

    @Test
    @DisplayName("Scenario B: Create Script -> Compile Failure -> Diagnose -> Repair -> Compile Success -> Validate -> Complete")
    void testScenarioB_CompileRecoveryLoop() throws Exception {
        when(mockUnityExecutor.execute(anyString(), eq("create_script"), any()))
                .thenReturn(UnityMessage.toolResponse("op1", true, Map.of("path", "Assets/Scripts/PlayerController.cs", "hash", "h1")));

        // First compile: returns FAILED with CS0103 diagnostic
        when(mockUnityExecutor.execute(anyString(), eq("compile_project"), any()))
                .thenReturn(UnityMessage.toolResponse("op2", false, Map.of(
                        "compilationState", "FAILED",
                        "errorCount", 1,
                        "errors", List.of(Map.of("errorCode", "CS0103", "file", "Assets/Scripts/PlayerController.cs", "line", 15, "message", "The name 'speed' does not exist"))
                )))
                // Second compile: returns COMPILED cleanly
                .thenReturn(UnityMessage.toolResponse("op4", true, Map.of(
                        "compilationState", "COMPILED",
                        "errorCount", 0
                )));

        when(mockUnityExecutor.execute(anyString(), eq("read_script"), any()))
                .thenReturn(UnityMessage.toolResponse("op3", true, Map.of("content", "public class PlayerController {}", "hash", "h1")));

        when(mockUnityExecutor.execute(anyString(), eq("update_script"), any()))
                .thenReturn(UnityMessage.toolResponse("op4", true, Map.of("path", "Assets/Scripts/PlayerController.cs", "newHash", "h2", "changed", true)));

        when(mockUnityExecutor.execute(anyString(), eq("validate_game_state"), any()))
                .thenReturn(UnityMessage.toolResponse("op5", true, Map.of("allRequirementsSatisfied", true)));

        List<String> eventLog = new ArrayList<>();
        AgentEventListener testListener = new AgentEventListener() {
            @Override public void onCompilationStarted(String s, String r) { eventLog.add("COMPILATION_STARTED"); }
            @Override public void onCompilationCompleted(String s, String r, boolean ok, Object d) { eventLog.add(ok ? "COMPILE_OK" : "COMPILE_FAIL"); }
            @Override public void onDiagnosisStarted(String s, String r, String d) { eventLog.add("DIAGNOSIS_STARTED"); }
            @Override public void onRepairStarted(String s, String r, String f) { eventLog.add("REPAIR_STARTED"); }
            @Override public void onGoalCompleted(String s, String r, Object res) { eventLog.add("GOAL_COMPLETED"); }
            @Override public void onRunStarted(String s, String r, String m) {}
            @Override public void onActivity(String r, String a) {}
            @Override public void onToolStarted(String r, String id, String t, Map<String, Object> a) {}
            @Override public void onToolCompleted(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onToolFailed(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onRunCompleted(String s, String r, AgentRunResult res) {}
            @Override public void onRunFailed(String s, String r, String e) {}
            @Override public void onRunCancelled(String s, String r) {}
        };

        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;
            @Override public String getProviderName() { return "fake"; }
            @Override public boolean isConfigured() { return true; }
            @Override public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_1", "create_script", Map.of("path", "Assets/Scripts/PlayerController.cs", "content", "public class PlayerController {}"), "{}")));
                } else if (turn == 2) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_2", "compile_project", Map.of(), "{}")));
                } else if (turn == 3) {
                    // Diagnose: read script
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_3", "read_script", Map.of("path", "Assets/Scripts/PlayerController.cs"), "{}")));
                } else if (turn == 4) {
                    // Repair script
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_4", "update_script", Map.of("path", "Assets/Scripts/PlayerController.cs", "previousHash", "h1", "content", "public class PlayerController { public float speed = 5f; }"), "{}")));
                } else if (turn == 5) {
                    // Recompile
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_5", "compile_project", Map.of(), "{}")));
                } else if (turn == 6) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_6", "validate_game_state", Map.of(), "{}")));
                } else {
                    return AgentCompletion.text("Repaired CS0103 and validated successfully.");
                }
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, limits, memory, mapper, checkpointRegistry);
        AgentRunResult result = loop.run("sess_B", "run_B", "Create player controller", ToolMode.BOTH, new CancellationToken(), testListener);

        assertTrue(result.isSuccess());
        assertTrue(eventLog.contains("COMPILATION_STARTED"));
        assertTrue(eventLog.contains("COMPILE_FAIL"));
        assertTrue(eventLog.contains("DIAGNOSIS_STARTED"));
        assertTrue(eventLog.contains("REPAIR_STARTED"));
        assertTrue(eventLog.contains("COMPILE_OK"));
        assertTrue(eventLog.contains("GOAL_COMPLETED"));
    }

    @Test
    @DisplayName("Scenario C: Repeated Compile Failures -> Recovery Limit -> FAILED")
    void testScenarioC_RepeatedCompileFailures() throws Exception {
        // Compile always fails
        when(mockUnityExecutor.execute(anyString(), eq("compile_project"), any()))
                .thenReturn(UnityMessage.toolResponse("op1", false, Map.of("compilationState", "FAILED", "errorCount", 1)));

        List<String> eventLog = new ArrayList<>();
        AgentEventListener testListener = new AgentEventListener() {
            @Override public void onRecoveryLimitReached(String s, String r, String l) { eventLog.add("RECOVERY_LIMIT_REACHED"); }
            @Override public void onGoalFailed(String s, String r, String reason) { eventLog.add("GOAL_FAILED"); }
            @Override public void onRunStarted(String s, String r, String m) {}
            @Override public void onActivity(String r, String a) {}
            @Override public void onToolStarted(String r, String id, String t, Map<String, Object> a) {}
            @Override public void onToolCompleted(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onToolFailed(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onRunCompleted(String s, String r, AgentRunResult res) {}
            @Override public void onRunFailed(String s, String r, String e) {}
            @Override public void onRunCancelled(String s, String r) {}
        };

        AIProvider fakeProvider = new AIProvider() {
            @Override public String getProviderName() { return "fake"; }
            @Override public boolean isConfigured() { return true; }
            @Override public AgentCompletion generate(AgentPrompt prompt) {
                // LLM keeps calling compile_project
                return AgentCompletion.toolCalls(List.of(new ToolCall(UUID.randomUUID().toString(), "compile_project", Map.of(), "{}")));
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, limits, memory, mapper, checkpointRegistry);
        AgentRunResult result = loop.run("sess_C", "run_C", "Compile repeatedly", ToolMode.BOTH, new CancellationToken(), testListener);

        assertFalse(result.isSuccess());
        assertEquals(ErrorType.COMPILE_ERROR, result.getErrorType());
        assertTrue(eventLog.contains("RECOVERY_LIMIT_REACHED"));
        assertTrue(eventLog.contains("GOAL_FAILED"));
    }

    @Test
    @DisplayName("Scenario D: Active Build -> Cancellation -> CANCELLED")
    void testScenarioD_Cancellation() {
        CancellationToken token = new CancellationToken();

        List<String> eventLog = new ArrayList<>();
        AgentEventListener testListener = new AgentEventListener() {
            @Override public void onRunCancelled(String s, String r) { eventLog.add("RUN_CANCELLED"); }
            @Override public void onRunStarted(String s, String r, String m) {}
            @Override public void onActivity(String r, String a) {}
            @Override public void onToolStarted(String r, String id, String t, Map<String, Object> a) {}
            @Override public void onToolCompleted(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onToolFailed(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onRunCompleted(String s, String r, AgentRunResult res) {}
            @Override public void onRunFailed(String s, String r, String e) {}
        };

        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;
            @Override public String getProviderName() { return "fake"; }
            @Override public boolean isConfigured() { return true; }
            @Override public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    token.cancel(); // Cancel immediately after turn 1
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_1", "create_primitive", Map.of("type", "Cube"), "{}")));
                }
                return AgentCompletion.text("Finished");
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, limits, memory, mapper, checkpointRegistry);
        AgentRunResult result = loop.run("sess_D", "run_D", "Build scene", ToolMode.BOTH, token, testListener);

        assertFalse(result.isSuccess());
        assertEquals(ErrorType.CANCELLED, result.getErrorType());
        assertTrue(eventLog.contains("RUN_CANCELLED"));
    }

    @Test
    @DisplayName("Scenario E: Autonomous Lifecycle Simulation (Inspect -> Build -> Script -> Fail Compile -> Diagnose -> Repair -> Pass Compile -> Play -> Test -> Validate -> Complete)")
    void testScenarioE_AutonomousLifecycleSimulation() throws Exception {
        when(mockUnityExecutor.execute(anyString(), eq("get_project_info"), any()))
                .thenReturn(UnityMessage.toolResponse("op1", true, Map.of("unityVersion", "6000.4.7f1", "activeScene", "PlatformScene")));

        when(mockUnityExecutor.execute(anyString(), eq("get_scene_hierarchy"), any()))
                .thenReturn(UnityMessage.toolResponse("op2", true, Map.of("roots", List.of("Main Camera", "Directional Light"))));

        when(mockUnityExecutor.execute(anyString(), eq("create_primitive"), any()))
                .thenReturn(UnityMessage.toolResponse("op3", true, Map.of("name", "CreatedObject", "id", "obj_1")));

        when(mockUnityExecutor.execute(anyString(), eq("create_script"), any()))
                .thenReturn(UnityMessage.toolResponse("op4", true, Map.of("path", "Assets/Scripts/PlayerController.cs", "hash", "h_buggy")));

        // First compile: returns FAILED with CS0103
        when(mockUnityExecutor.execute(anyString(), eq("compile_project"), any()))
                .thenReturn(UnityMessage.toolResponse("op5_fail", false, Map.of(
                        "compilationState", "FAILED",
                        "errorCount", 1,
                        "errors", List.of(Map.of("errorCode", "CS0103", "file", "Assets/Scripts/PlayerController.cs", "line", 14, "message", "The name 'speed' does not exist in the current context"))
                )))
                // Second compile: returns COMPILED
                .thenReturn(UnityMessage.toolResponse("op5_ok", true, Map.of(
                        "compilationState", "COMPILED",
                        "errorCount", 0
                )));

        when(mockUnityExecutor.execute(anyString(), eq("read_script"), any()))
                .thenReturn(UnityMessage.toolResponse("op6", true, Map.of("path", "Assets/Scripts/PlayerController.cs", "content", "public class PlayerController : MonoBehaviour {}", "hash", "h_buggy")));

        when(mockUnityExecutor.execute(anyString(), eq("update_script"), any()))
                .thenReturn(UnityMessage.toolResponse("op7", true, Map.of("path", "Assets/Scripts/PlayerController.cs", "newHash", "h_fixed", "changed", true)));

        when(mockUnityExecutor.execute(anyString(), eq("enter_play_mode"), any()))
                .thenReturn(UnityMessage.toolResponse("op8", true, Map.of("playMode", true)));

        when(mockUnityExecutor.execute(anyString(), eq("run_game_test"), any()))
                .thenReturn(UnityMessage.toolResponse("op9", true, Map.of("success", true, "positionChanged", true, "distanceMoved", 5.2)));

        when(mockUnityExecutor.execute(anyString(), eq("exit_play_mode"), any()))
                .thenReturn(UnityMessage.toolResponse("op10", true, Map.of("playMode", false)));

        when(mockUnityExecutor.execute(anyString(), eq("validate_game_state"), any()))
                .thenReturn(UnityMessage.toolResponse("op11", true, Map.of("allRequirementsSatisfied", true, "satisfiedCount", 8, "totalCount", 8)));

        List<String> eventLog = new ArrayList<>();
        AgentEventListener testListener = new AgentEventListener() {
            @Override public void onGoalCreated(String s, String r, String d) { eventLog.add("GOAL_CREATED"); }
            @Override public void onCompilationStarted(String s, String r) { eventLog.add("COMPILATION_STARTED"); }
            @Override public void onCompilationCompleted(String s, String r, boolean ok, Object d) { eventLog.add(ok ? "COMPILE_OK" : "COMPILE_FAIL"); }
            @Override public void onDiagnosisStarted(String s, String r, String d) { eventLog.add("DIAGNOSIS_STARTED"); }
            @Override public void onRepairStarted(String s, String r, String f) { eventLog.add("REPAIR_STARTED"); }
            @Override public void onRuntimeTestStarted(String s, String r, String t) { eventLog.add("RUNTIME_TEST_STARTED"); }
            @Override public void onRuntimeTestCompleted(String s, String r, boolean ok, Object d) { eventLog.add("RUNTIME_TEST_COMPLETED"); }
            @Override public void onValidationStarted(String s, String r, String v) { eventLog.add("VALIDATION_STARTED"); }
            @Override public void onValidationCompleted(String s, String r, boolean ok, Object d) { eventLog.add("VALIDATION_COMPLETED"); }
            @Override public void onGoalCompleted(String s, String r, Object res) { eventLog.add("GOAL_COMPLETED"); }
            @Override public void onRunStarted(String s, String r, String m) {}
            @Override public void onActivity(String r, String a) {}
            @Override public void onToolStarted(String r, String id, String t, Map<String, Object> a) {}
            @Override public void onToolCompleted(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onToolFailed(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onRunCompleted(String s, String r, AgentRunResult res) {}
            @Override public void onRunFailed(String s, String r, String e) {}
            @Override public void onRunCancelled(String s, String r) {}
        };

        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;
            @Override public String getProviderName() { return "fake"; }
            @Override public boolean isConfigured() { return true; }
            @Override public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                return switch (turn) {
                    case 1 -> AgentCompletion.toolCalls(List.of(new ToolCall("c1", "get_project_info", Map.of(), "{}")));
                    case 2 -> AgentCompletion.toolCalls(List.of(new ToolCall("c2", "get_scene_hierarchy", Map.of(), "{}")));
                    case 3 -> AgentCompletion.toolCalls(List.of(new ToolCall("c3", "create_primitive", Map.of("type", "Sphere", "name", "Player"), "{}")));
                    case 4 -> AgentCompletion.toolCalls(List.of(new ToolCall("c4", "create_script", Map.of("path", "Assets/Scripts/PlayerController.cs", "content", "public class PlayerController : MonoBehaviour { void Update() { transform.Translate(Vector3.forward * speed); } }"), "{}")));
                    case 5 -> AgentCompletion.toolCalls(List.of(new ToolCall("c5", "compile_project", Map.of(), "{}")));
                    case 6 -> AgentCompletion.toolCalls(List.of(new ToolCall("c6", "read_script", Map.of("path", "Assets/Scripts/PlayerController.cs"), "{}")));
                    case 7 -> AgentCompletion.toolCalls(List.of(new ToolCall("c7", "update_script", Map.of("path", "Assets/Scripts/PlayerController.cs", "previousHash", "h_buggy", "content", "public class PlayerController : MonoBehaviour { public float speed = 5.0f; void Update() { transform.Translate(Vector3.forward * speed); } }"), "{}")));
                    case 8 -> AgentCompletion.toolCalls(List.of(new ToolCall("c8", "compile_project", Map.of(), "{}")));
                    case 9 -> AgentCompletion.toolCalls(List.of(new ToolCall("c9", "create_primitive", Map.of("type", "Cube", "name", "Ground"), "{}")));
                    case 10 -> AgentCompletion.toolCalls(List.of(new ToolCall("c10", "enter_play_mode", Map.of(), "{}")));
                    case 11 -> AgentCompletion.toolCalls(List.of(new ToolCall("c11", "run_game_test", Map.of("targetObject", "Player", "input", "Vertical", "duration", 1.0), "{}")));
                    case 12 -> AgentCompletion.toolCalls(List.of(new ToolCall("c12", "exit_play_mode", Map.of(), "{}")));
                    case 13 -> AgentCompletion.toolCalls(List.of(new ToolCall("c13", "validate_game_state", Map.of(), "{}")));
                    default -> AgentCompletion.text("3D platform game built, repaired, tested, and verified successfully.");
                };
            }
        };

        AgentLimits scenarioLimits = new AgentLimits(30, 5, 3, 30, 600, 0.7, 4096, 5, 3, 5, 30, 50, 120);
        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, scenarioLimits, memory, mapper, checkpointRegistry);
        AgentRunResult result = loop.run("sess_E", "run_E", "Build me a small playable 3D platform game with player, ground, platforms", ToolMode.BOTH, new CancellationToken(), testListener);

        assertTrue(result.isSuccess());
        assertTrue(eventLog.contains("GOAL_CREATED"));
        assertTrue(eventLog.contains("COMPILATION_STARTED"));
        assertTrue(eventLog.contains("COMPILE_FAIL"));
        assertTrue(eventLog.contains("DIAGNOSIS_STARTED"));
        assertTrue(eventLog.contains("REPAIR_STARTED"));
        assertTrue(eventLog.contains("COMPILE_OK"));
        assertTrue(eventLog.contains("RUNTIME_TEST_STARTED"));
        assertTrue(eventLog.contains("RUNTIME_TEST_COMPLETED"));
        assertTrue(eventLog.contains("VALIDATION_STARTED"));
        assertTrue(eventLog.contains("VALIDATION_COMPLETED"));
        assertTrue(eventLog.contains("GOAL_COMPLETED"));
        assertTrue(result.getIterations() <= scenarioLimits.getMaxIterations());
        assertTrue(result.getTotalToolCalls() <= scenarioLimits.getMaxTotalToolCalls());
    }

    @Test
    @DisplayName("Scenario F: Runtime Test Failure & Recovery (Compile -> Play -> Test Fail -> Read -> Update -> Compile -> Play -> Test Pass -> Exit -> Validate -> Complete)")
    void testScenarioF_RuntimeTestFailureAndRecovery() throws Exception {
        when(mockUnityExecutor.execute(anyString(), eq("compile_project"), any()))
                .thenReturn(UnityMessage.toolResponse("c_ok", true, Map.of("compilationState", "COMPILED", "errorCount", 0)));

        when(mockUnityExecutor.execute(anyString(), eq("enter_play_mode"), any()))
                .thenReturn(UnityMessage.toolResponse("pm_on", true, Map.of("playMode", true)));

        // First runtime test fails, second passes
        when(mockUnityExecutor.execute(anyString(), eq("run_game_test"), any()))
                .thenReturn(UnityMessage.toolResponse("rt_fail", false, Map.of("success", false, "positionChanged", false, "error", "Player did not move")))
                .thenReturn(UnityMessage.toolResponse("rt_pass", true, Map.of("success", true, "positionChanged", true, "distanceMoved", 3.5)));

        when(mockUnityExecutor.execute(anyString(), eq("read_script"), any()))
                .thenReturn(UnityMessage.toolResponse("rs", true, Map.of("path", "Assets/Scripts/PlayerController.cs", "content", "public class PlayerController : MonoBehaviour {}", "hash", "h_orig")));

        when(mockUnityExecutor.execute(anyString(), eq("update_script"), any()))
                .thenReturn(UnityMessage.toolResponse("us", true, Map.of("path", "Assets/Scripts/PlayerController.cs", "newHash", "h_fix", "changed", true)));

        when(mockUnityExecutor.execute(anyString(), eq("exit_play_mode"), any()))
                .thenReturn(UnityMessage.toolResponse("pm_off", true, Map.of("playMode", false)));

        when(mockUnityExecutor.execute(anyString(), eq("validate_game_state"), any()))
                .thenReturn(UnityMessage.toolResponse("v_ok", true, Map.of("allRequirementsSatisfied", true, "satisfiedCount", 5, "totalCount", 5)));

        List<String> eventLog = new ArrayList<>();
        AgentEventListener testListener = new AgentEventListener() {
            @Override public void onRuntimeTestStarted(String s, String r, String t) { eventLog.add("RUNTIME_TEST_STARTED"); }
            @Override public void onRuntimeTestCompleted(String s, String r, boolean ok, Object d) { eventLog.add(ok ? "RUNTIME_TEST_OK" : "RUNTIME_TEST_FAIL"); }
            @Override public void onRepairStarted(String s, String r, String f) { eventLog.add("REPAIR_STARTED"); }
            @Override public void onCompilationCompleted(String s, String r, boolean ok, Object d) { eventLog.add(ok ? "COMPILE_OK" : "COMPILE_FAIL"); }
            @Override public void onValidationCompleted(String s, String r, boolean ok, Object d) { eventLog.add(ok ? "VALIDATION_OK" : "VALIDATION_FAIL"); }
            @Override public void onGoalCompleted(String s, String r, Object res) { eventLog.add("GOAL_COMPLETED"); }
            @Override public void onRunStarted(String s, String r, String m) {}
            @Override public void onActivity(String r, String a) {}
            @Override public void onToolStarted(String r, String id, String t, Map<String, Object> a) {}
            @Override public void onToolCompleted(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onToolFailed(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onRunCompleted(String s, String r, AgentRunResult res) {}
            @Override public void onRunFailed(String s, String r, String e) {}
            @Override public void onRunCancelled(String s, String r) {}
        };

        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;
            @Override public String getProviderName() { return "fake"; }
            @Override public boolean isConfigured() { return true; }
            @Override public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                return switch (turn) {
                    case 1 -> AgentCompletion.toolCalls(List.of(new ToolCall("c1", "compile_project", Map.of(), "{}")));
                    case 2 -> AgentCompletion.toolCalls(List.of(new ToolCall("c2", "enter_play_mode", Map.of(), "{}")));
                    case 3 -> AgentCompletion.toolCalls(List.of(new ToolCall("c3", "run_game_test", Map.of("targetObject", "Player"), "{}")));
                    case 4 -> AgentCompletion.toolCalls(List.of(new ToolCall("c4", "read_script", Map.of("path", "Assets/Scripts/PlayerController.cs"), "{}")));
                    case 5 -> AgentCompletion.toolCalls(List.of(new ToolCall("c5", "update_script", Map.of("path", "Assets/Scripts/PlayerController.cs", "content", "public class PlayerController : MonoBehaviour { public float speed = 10f; }"), "{}")));
                    case 6 -> AgentCompletion.toolCalls(List.of(new ToolCall("c6", "compile_project", Map.of(), "{}")));
                    case 7 -> AgentCompletion.toolCalls(List.of(new ToolCall("c7", "enter_play_mode", Map.of(), "{}")));
                    case 8 -> AgentCompletion.toolCalls(List.of(new ToolCall("c8", "run_game_test", Map.of("targetObject", "Player"), "{}")));
                    case 9 -> AgentCompletion.toolCalls(List.of(new ToolCall("c9", "exit_play_mode", Map.of(), "{}")));
                    case 10 -> AgentCompletion.toolCalls(List.of(new ToolCall("c10", "validate_game_state", Map.of(), "{}")));
                    default -> AgentCompletion.text("Runtime movement test repaired and validated successfully.");
                };
            }
        };

        AgentLimits scenarioLimits = new AgentLimits(30, 5, 3, 30, 600, 0.7, 4096, 5, 3, 5, 30, 50, 120);
        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, scenarioLimits, memory, mapper, checkpointRegistry);
        AgentRunResult result = loop.run("sess_F", "run_F", "Test player movement runtime", ToolMode.BOTH, new CancellationToken(), testListener);

        assertTrue(result.isSuccess());
        assertTrue(eventLog.contains("RUNTIME_TEST_FAIL"));
        assertTrue(eventLog.contains("REPAIR_STARTED"));
        assertTrue(eventLog.contains("RUNTIME_TEST_OK"));
        assertTrue(eventLog.contains("VALIDATION_OK"));
        assertTrue(eventLog.contains("GOAL_COMPLETED"));
    }

    @Test
    @DisplayName("Scenario G: Script Safety Rejection (Unsafe script -> VALIDATION_ERROR -> Safe script -> Compile -> Validate -> Complete)")
    void testScenarioG_ScriptSafetyRejection() throws Exception {
        when(mockUnityExecutor.execute(anyString(), eq("create_script"), any()))
                .thenReturn(UnityMessage.toolResponse("cs_ok", true, Map.of("path", "Assets/Scripts/PlayerController.cs", "hash", "h_safe")));

        when(mockUnityExecutor.execute(anyString(), eq("compile_project"), any()))
                .thenReturn(UnityMessage.toolResponse("comp_ok", true, Map.of("compilationState", "COMPILED", "errorCount", 0)));

        when(mockUnityExecutor.execute(anyString(), eq("validate_game_state"), any()))
                .thenReturn(UnityMessage.toolResponse("val_ok", true, Map.of("allRequirementsSatisfied", true)));

        List<String> eventLog = new ArrayList<>();
        AgentEventListener testListener = new AgentEventListener() {
            @Override public void onGoalCompleted(String s, String r, Object res) { eventLog.add("GOAL_COMPLETED"); }
            @Override public void onRunStarted(String s, String r, String m) {}
            @Override public void onActivity(String r, String a) {}
            @Override public void onToolStarted(String r, String id, String t, Map<String, Object> a) {}
            @Override public void onToolCompleted(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onToolFailed(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onRunCompleted(String s, String r, AgentRunResult res) {}
            @Override public void onRunFailed(String s, String r, String e) {}
            @Override public void onRunCancelled(String s, String r) {}
        };

        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;
            @Override public String getProviderName() { return "fake"; }
            @Override public boolean isConfigured() { return true; }
            @Override public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    // Attempt dangerous script with System.Diagnostics.Process.Start("cmd.exe");
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_unsafe", "create_script", Map.of(
                            "path", "Assets/Scripts/Exploit.cs",
                            "content", "using UnityEngine;\npublic class Exploit : MonoBehaviour { void Start() { System.Diagnostics.Process.Start(\"cmd.exe\"); } }"
                    ), "{}")));
                } else if (turn == 2) {
                    // Recover: generate clean safe script
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_safe", "create_script", Map.of(
                            "path", "Assets/Scripts/PlayerController.cs",
                            "content", "using UnityEngine;\npublic class PlayerController : MonoBehaviour { public float speed = 5.0f; }"
                    ), "{}")));
                } else if (turn == 3) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_comp", "compile_project", Map.of(), "{}")));
                } else if (turn == 4) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("call_val", "validate_game_state", Map.of(), "{}")));
                } else {
                    return AgentCompletion.text("Recovered from safety rejection and built safely.");
                }
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, limits, memory, mapper, checkpointRegistry);
        AgentRunResult result = loop.run("sess_G", "run_G", "Create player controller", ToolMode.BOTH, new CancellationToken(), testListener);

        assertTrue(result.isSuccess());
        // Verify that the unsafe tool call failed with VALIDATION_ERROR and mentions prohibited API
        ToolExecutionResult unsafeResult = result.getToolExecutions().get(0);
        assertFalse(unsafeResult.isSuccess());
        assertEquals(ErrorType.VALIDATION_ERROR, unsafeResult.getErrorType());
        assertTrue(unsafeResult.getErrorMessage().contains("prohibited API"), "Error message should mention prohibited API: " + unsafeResult.getErrorMessage());

        // Verify that the safe script succeeded
        ToolExecutionResult safeResult = result.getToolExecutions().get(1);
        assertTrue(safeResult.isSuccess());

        assertTrue(eventLog.contains("GOAL_COMPLETED"));
    }

    @Test
    @DisplayName("Scenario H: Verification Gate Enforcement (Premature completion blocked -> Forced validate_game_state -> Completion)")
    void testScenarioH_VerificationGateEnforcement() throws Exception {
        when(mockUnityExecutor.execute(anyString(), eq("create_primitive"), any()))
                .thenReturn(UnityMessage.toolResponse("op1", true, Map.of("name", "Player")));

        when(mockUnityExecutor.execute(anyString(), eq("create_script"), any()))
                .thenReturn(UnityMessage.toolResponse("op2", true, Map.of("path", "Assets/Scripts/Player.cs", "hash", "h1")));

        when(mockUnityExecutor.execute(anyString(), eq("compile_project"), any()))
                .thenReturn(UnityMessage.toolResponse("op3", true, Map.of("compilationState", "COMPILED", "errorCount", 0)));

        when(mockUnityExecutor.execute(anyString(), eq("validate_game_state"), any()))
                .thenReturn(UnityMessage.toolResponse("op4", true, Map.of("allRequirementsSatisfied", true, "satisfiedCount", 4, "totalCount", 4)));

        List<String> eventLog = new ArrayList<>();
        AgentEventListener testListener = new AgentEventListener() {
            @Override public void onValidationStarted(String s, String r, String v) { eventLog.add("VALIDATION_STARTED"); }
            @Override public void onValidationCompleted(String s, String r, boolean ok, Object d) { eventLog.add("VALIDATION_COMPLETED"); }
            @Override public void onGoalCompleted(String s, String r, Object res) { eventLog.add("GOAL_COMPLETED"); }
            @Override public void onRunStarted(String s, String r, String m) {}
            @Override public void onActivity(String r, String a) {}
            @Override public void onToolStarted(String r, String id, String t, Map<String, Object> a) {}
            @Override public void onToolCompleted(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onToolFailed(String r, String id, String t, ToolExecutionResult res) {}
            @Override public void onRunCompleted(String s, String r, AgentRunResult res) {}
            @Override public void onRunFailed(String s, String r, String e) {}
            @Override public void onRunCancelled(String s, String r) {}
        };

        AIProvider fakeProvider = new AIProvider() {
            private int turn = 0;
            @Override public String getProviderName() { return "fake"; }
            @Override public boolean isConfigured() { return true; }
            @Override public AgentCompletion generate(AgentPrompt prompt) {
                turn++;
                if (turn == 1) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("c1", "create_primitive", Map.of("type", "Cube", "name", "Player"), "{}")));
                } else if (turn == 2) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("c2", "create_script", Map.of("path", "Assets/Scripts/Player.cs", "content", "public class Player : MonoBehaviour {}"), "{}")));
                } else if (turn == 3) {
                    return AgentCompletion.toolCalls(List.of(new ToolCall("c3", "compile_project", Map.of(), "{}")));
                } else if (turn == 4) {
                    // Premature conclusion attempt WITHOUT validate_game_state
                    return AgentCompletion.text("I am all done building the game!");
                } else if (turn == 5) {
                    // The verification gate should have prompted the agent to call validate_game_state
                    return AgentCompletion.toolCalls(List.of(new ToolCall("c4", "validate_game_state", Map.of(), "{}")));
                } else {
                    return AgentCompletion.text("Verified and all requirements objectively satisfied.");
                }
            }
        };

        AgentLoop loop = new AgentLoop(fakeProvider, toolRegistry, mockUnityExecutor, limits, memory, mapper, checkpointRegistry);
        // Message contains "platform" to trigger verification requirement
        AgentRunResult result = loop.run("sess_H", "run_H", "Build me a small playable 3D platform game", ToolMode.BOTH, new CancellationToken(), testListener);

        assertTrue(result.isSuccess());
        // Verify turn 4 premature completion was rejected and gate prompted verification
        boolean hasVerificationPrompt = memory.getHistory("sess_H").stream()
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("Objective verification required"));
        assertTrue(hasVerificationPrompt, "Conversation memory should contain injected verification prompt");

        assertTrue(eventLog.contains("VALIDATION_STARTED"));
        assertTrue(eventLog.contains("VALIDATION_COMPLETED"));
        assertTrue(eventLog.contains("GOAL_COMPLETED"));
    }

    @Test
    @DisplayName("Scenario I: Concurrent Session Conflict (Run B rejected while Run A is active)")
    void testScenarioI_ConcurrentSessionConflict() throws Exception {
        CountDownLatch runAStartedLatch = new CountDownLatch(1);
        CountDownLatch runABlockLatch = new CountDownLatch(1);

        AgentLoop mockLoop = Mockito.mock(AgentLoop.class);
        when(mockLoop.run(eq("sess_concurrent"), eq("run_A"), anyString(), any(ToolMode.class), any(CancellationToken.class), any(AgentEventListener.class)))
                .thenAnswer(inv -> {
                    runAStartedLatch.countDown();
                    runABlockLatch.await(5, TimeUnit.SECONDS);
                    AgentEventListener listener = inv.getArgument(5);
                    AgentRunResult res = AgentRunResult.success("sess_concurrent", "run_A", "Run A finished", 2, 2, List.of());
                    listener.onRunCompleted("sess_concurrent", "run_A", res);
                    return res;
                });

        AgentService service = new AgentService(mockLoop, memory);

        // Start Run A
        AgentRunState stateA = service.startRunAsync("sess_concurrent", "run_A", "Build game A", ToolMode.BOTH);
        assertNotNull(stateA);
        assertTrue(runAStartedLatch.await(2, TimeUnit.SECONDS));

        // While Run A is running on sess_concurrent, Run B on the SAME session MUST be rejected
        assertThrows(SessionConflictException.class, () ->
                service.startRunAsync("sess_concurrent", "run_B", "Build game B", ToolMode.BOTH)
        );

        // Synchronous start on same session must also be rejected
        assertThrows(SessionConflictException.class, () ->
                service.runSync("sess_concurrent", "run_B_sync", "Build game B sync", ToolMode.BOTH)
        );

        // Unblock Run A
        runABlockLatch.countDown();

        // Wait for Run A to finish
        long timeoutMs = 3000;
        long startMs = System.currentTimeMillis();
        while (stateA.getStatus() != AgentRunState.Status.COMPLETED && (System.currentTimeMillis() - startMs) < timeoutMs) {
            Thread.sleep(50);
        }

        assertEquals(AgentRunState.Status.COMPLETED, stateA.getStatus());
        assertNotNull(stateA.getResult());
        assertTrue(stateA.getResult().isSuccess());
        assertEquals("Run A finished", stateA.getResult().getResponse());

        // Run B never ran or corrupted state
        assertTrue(service.getRunState("run_B").isEmpty());
    }
}
