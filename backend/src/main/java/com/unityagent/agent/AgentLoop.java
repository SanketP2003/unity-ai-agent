package com.unityagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.identity.UnityObjectIdentity;
import com.unityagent.agent.memory.ConversationMemory;
import com.unityagent.agent.model.*;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.security.ScriptCheckpointRegistry;
import com.unityagent.agent.security.ScriptSafetyValidator;
import com.unityagent.agent.provider.AIProviderException;
import com.unityagent.tools.Tool;
import com.unityagent.tools.ToolDefinition;
import com.unityagent.tools.ToolMode;
import com.unityagent.tools.ToolPermission;
import com.unityagent.tools.ToolRegistry;
import com.unityagent.unity.UnityCommandExecutor;
import com.unityagent.unity.UnityMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * The core autonomous agent reasoning loop.
 *
 * <p>Executes the complete multi-turn reasoning sequence:
 * User Request -> LLM Tool Call -> ToolRegistry Permission/Mode/Parameter Check ->
 * Unity Execution -> Structured Result -> Correlated Tool Result fed to LLM -> ... -> Final Response.
 *
 * <p>Strictly enforces runtime limits, error classification, cancellation, and identifier propagation
 * (sessionId, agentRunId, operationId, toolCallId).
 */
@Service
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final AIProvider aiProvider;
    private final ToolRegistry toolRegistry;
    private final UnityCommandExecutor unityExecutor;
    private final AgentLimits limits;
    private final ConversationMemory memory;
    private final ObjectMapper mapper;
    private final ScriptCheckpointRegistry checkpointRegistry;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentLoop(AIProvider aiProvider,
                     ToolRegistry toolRegistry,
                     UnityCommandExecutor unityExecutor,
                     AgentLimits limits,
                     ConversationMemory memory,
                     ObjectMapper mapper,
                     ScriptCheckpointRegistry checkpointRegistry) {
        this.aiProvider = aiProvider;
        this.toolRegistry = toolRegistry;
        this.unityExecutor = unityExecutor;
        this.limits = limits != null ? limits : AgentLimits.defaultLimits();
        this.memory = memory != null ? memory : new ConversationMemory();
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.checkpointRegistry = checkpointRegistry != null ? checkpointRegistry : new ScriptCheckpointRegistry();
    }

    public AgentLoop(AIProvider aiProvider,
                     ToolRegistry toolRegistry,
                     UnityCommandExecutor unityExecutor,
                     AgentLimits limits,
                     ConversationMemory memory,
                     ObjectMapper mapper) {
        this(aiProvider, toolRegistry, unityExecutor, limits, memory, mapper, new ScriptCheckpointRegistry());
    }

    /**
     * Executes an autonomous agent run.
     *
     * @param sessionId         persistent conversation session
     * @param agentRunId        unique identifier for this specific autonomous run
     * @param userMessage       the natural language instruction from the user
     * @param cancellationToken cancellation coordinator
     * @return structured outcome of the agent run
     */
    public AgentRunResult run(String sessionId,
                              String agentRunId,
                              String userMessage,
                              CancellationToken cancellationToken) {
        return run(sessionId, agentRunId, userMessage, ToolMode.BOTH, cancellationToken, com.unityagent.agent.event.AgentEventListener.NOOP);
    }

    /**
     * Executes an autonomous agent run with an explicit execution mode constraint.
     */
    public AgentRunResult run(String sessionId,
                              String agentRunId,
                              String userMessage,
                              ToolMode mode,
                              CancellationToken cancellationToken) {
        return run(sessionId, agentRunId, userMessage, mode, cancellationToken, com.unityagent.agent.event.AgentEventListener.NOOP);
    }

    /**
     * Executes an autonomous agent run with mode constraint and event listener.
     */
    public AgentRunResult run(String sessionId,
                              String agentRunId,
                              String userMessage,
                              ToolMode mode,
                              CancellationToken cancellationToken,
                              com.unityagent.agent.event.AgentEventListener listener) {
        return run(sessionId, agentRunId, userMessage, mode, cancellationToken, listener, null);
    }

    /**
     * Executes an autonomous agent run targeted at a specific connected Unity project.
     */
    public AgentRunResult run(String sessionId,
                              String agentRunId,
                              String userMessage,
                              ToolMode mode,
                              CancellationToken cancellationToken,
                              com.unityagent.agent.event.AgentEventListener listener,
                              String projectId) {
        return run(sessionId, agentRunId, userMessage, mode, cancellationToken, listener, projectId, null);
    }

    /**
     * Executes an autonomous agent run targeted at a specific connected Unity project with persistent memory context.
     */
    public AgentRunResult run(String sessionId,
                              String agentRunId,
                              String userMessage,
                              ToolMode mode,
                              CancellationToken cancellationToken,
                              com.unityagent.agent.event.AgentEventListener listener,
                              String projectId,
                              String memoryContext) {

        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(agentRunId, "agentRunId cannot be null");
        Objects.requireNonNull(userMessage, "userMessage cannot be null");
        if (cancellationToken == null) {
            cancellationToken = new CancellationToken();
        }
        if (listener == null) {
            listener = com.unityagent.agent.event.AgentEventListener.NOOP;
        }

        log.info("Starting autonomous agent run: sessionId={}, agentRunId={}, mode={}, projectId={}", sessionId, agentRunId, mode, projectId);
        listener.onRunStarted(sessionId, agentRunId, userMessage);

        // Verify AI provider is configured
        if (!aiProvider.isConfigured()) {
            String err = "AI provider '" + aiProvider.getProviderName() + "' is not configured. Please provide a valid API key (e.g. OPENAI_API_KEY).";
            log.error(err);
            listener.onRunFailed(sessionId, agentRunId, err);
            return AgentRunResult.failure(sessionId, agentRunId, ErrorType.PROVIDER_ERROR, err, 0, 0, List.of(), userMessage);
        }

        // Verify AI provider capabilities (at minimum toolCalling must be true for autonomous operation)
        if (aiProvider.getCapabilities() != null && !aiProvider.getCapabilities().isToolCalling()) {
            String err = "Model/provider '" + aiProvider.getProviderName() + "' does not support tool calling (MODEL_CAPABILITY_UNSUPPORTED). Autonomous execution cannot proceed.";
            log.error(err);
            listener.onRunFailed(sessionId, agentRunId, err);
            return AgentRunResult.failure(sessionId, agentRunId, ErrorType.MODEL_CAPABILITY_UNSUPPORTED, err, 0, 0, List.of(), userMessage);
        }

        // Initialize conversation state
        List<ChatMessage> conversation = new ArrayList<>(memory.getHistory(sessionId));
        if (conversation.isEmpty()) {
            ChatMessage systemMsg = ChatMessage.system(AgentSystemPrompt.getSystemPrompt(memoryContext));
            conversation.add(systemMsg);
            memory.addMessage(sessionId, systemMsg);
        }
        ChatMessage userChatMsg = ChatMessage.user(userMessage);
        conversation.add(userChatMsg);
        memory.addMessage(sessionId, userChatMsg);

        // Available tools filtered by mode and non-blocked permissions
        List<ToolDefinition> availableTools = toolRegistry.listDefinitions().stream()
                .filter(t -> t.getPermission() != ToolPermission.BLOCKED)
                .filter(t -> mode == null || t.isAllowedInMode(mode))
                .toList();

        List<ToolExecutionResult> allExecutions = new ArrayList<>();
        Map<String, Integer> toolRetryCounts = new HashMap<>();
        int consecutiveFailures = 0;
        int totalToolCalls = 0;

        // Phase 6 State & Recovery Tracking
        GameGoal goal = new GameGoal(null, sessionId, userMessage);
        AgentPlan plan = new AgentPlan(null, goal.getGoalId());
        AgentState currentState = AgentState.IDLE;
        listener.onGoalCreated(sessionId, agentRunId, userMessage);

        int compileAttempts = 0;
        Map<String, Integer> fixAttemptsPerError = new HashMap<>();
        int runtimeValidationAttempts = 0;
        boolean goalVerified = false;
        boolean createdScriptOrModifiedScene = false;

        int maxIterations = limits.getMaxIterations();
        int maxToolFailures = limits.getMaxToolFailures();
        int maxSameToolRetries = limits.getMaxSameToolRetries();
        long startTimeMs = System.currentTimeMillis();
        long maxDurationMs = limits.getRunTimeoutSeconds() * 1000L;

        // Autonomous Reasoning Loop
        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            if (cancellationToken.isCancelled()) {
                log.info("Agent run cancelled at iteration {}: agentRunId={}", iteration, agentRunId);
                currentState = AgentState.CANCELLED;
                goal.setStatus(GameGoal.GoalStatus.CANCELLED);
                listener.onRunCancelled(sessionId, agentRunId);
                return AgentRunResult.cancelled(sessionId, agentRunId, iteration, totalToolCalls, allExecutions, userMessage);
            }

            if (System.currentTimeMillis() - startTimeMs > maxDurationMs) {
                if (goalVerified) {
                    log.info("Agent run duration exceeded {} seconds, but goal was ALREADY VERIFIED. Completing run successfully.", limits.getRunTimeoutSeconds());
                    currentState = AgentState.COMPLETED;
                    goal.setStatus(GameGoal.GoalStatus.COMPLETED);
                    listener.onGoalCompleted(sessionId, agentRunId, "All game requirements verified via validate_game_state.");
                    return AgentRunResult.success(sessionId, agentRunId, "Game requirements validated and satisfied.", iteration, totalToolCalls, allExecutions, userMessage);
                }
                String timeoutMsg = "Agent run exceeded total timeout limit of " + limits.getRunTimeoutSeconds() + " seconds.";
                log.warn(timeoutMsg);
                currentState = AgentState.FAILED;
                goal.setStatus(GameGoal.GoalStatus.FAILED);
                listener.onGoalFailed(sessionId, agentRunId, timeoutMsg);
                return AgentRunResult.failure(sessionId, agentRunId, ErrorType.TIMEOUT, timeoutMsg, iteration, totalToolCalls, allExecutions, userMessage);
            }

            log.debug("AgentLoop iteration {}/{}: agentRunId={}, state={}", iteration, maxIterations, agentRunId, currentState);
            if (currentState == AgentState.IDLE) {
                currentState = AgentState.PLANNING;
            }
            listener.onActivity(agentRunId, "Thinking...");

            AgentPrompt prompt = new AgentPrompt(conversation, availableTools, limits.getTemperature(), limits.getMaxTokens());
            AgentCompletion completion;
            try {
                completion = aiProvider.generate(prompt);
            } catch (AIProviderException e) {
                log.error("AIProvider failed during agentRunId {}: {}", agentRunId, e.getMessage());
                currentState = AgentState.FAILED;
                listener.onRunFailed(sessionId, agentRunId, e.getMessage());
                return AgentRunResult.failure(sessionId, agentRunId, e.getErrorType(), e.getMessage(), iteration, totalToolCalls, allExecutions, userMessage);
            }

            if (completion.hasReasoningContent()) {
                log.info("Agent internal reasoning: {}", completion.getReasoningContent());
            }

            // Case 1: Final answer returned (no tool calls)
            if (!completion.hasToolCalls()) {
                String finalText = completion.getContent() != null ? completion.getContent() : "";

                // Verification-driven completion check:
                // When validate_game_state is available and an autonomous game build is requested,
                // objective verification via validate_game_state is strictly required.
                boolean verificationRequired = toolRegistry.hasTool("validate_game_state")
                        && (userMessage.toLowerCase().contains("platform")
                            || (userMessage.toLowerCase().contains("build") && userMessage.toLowerCase().contains("game"))
                            || (goal.getRequirements() != null && !goal.getRequirements().isEmpty()));

                if (verificationRequired && !goalVerified && iteration < maxIterations - 1) {
                    log.info("Agent attempted to conclude without objective validation. Prompting for validate_game_state.");
                    ChatMessage verificationPrompt = ChatMessage.user("Objective verification required: Before reporting completion, you MUST call 'validate_game_state' to objectively verify that all goal requirements, scene state, scripts, and runtime behavior are satisfied in Unity.");
                    conversation.add(verificationPrompt);
                    memory.addMessage(sessionId, verificationPrompt);
                    continue;
                }

                ChatMessage assistantFinal = ChatMessage.assistant(finalText);
                conversation.add(assistantFinal);
                memory.addMessage(sessionId, assistantFinal);

                currentState = AgentState.COMPLETED;
                goal.setStatus(GameGoal.GoalStatus.COMPLETED);
                listener.onGoalCompleted(sessionId, agentRunId, finalText);

                log.info("Agent run completed successfully in {} iterations ({} tool calls): agentRunId={}",
                        iteration, totalToolCalls, agentRunId);
                AgentRunResult finalSuccess = AgentRunResult.success(sessionId, agentRunId, finalText, iteration, totalToolCalls, allExecutions, userMessage);
                listener.onRunCompleted(sessionId, agentRunId, finalSuccess);
                return finalSuccess;
            }

            // Case 2: LLM requested one or more tool calls
            List<ToolCall> toolCalls = completion.getToolCalls();
            ChatMessage assistantToolMsg = ChatMessage.assistantToolCalls(completion.getContent(), toolCalls);
            conversation.add(assistantToolMsg);
            memory.addMessage(sessionId, assistantToolMsg);

            // Execute requested tool calls sequentially
            for (ToolCall call : toolCalls) {
                totalToolCalls++;
                String operationId = UUID.randomUUID().toString();
                String toolCallId = call.getId();
                String toolName = call.getName();

                if (cancellationToken.isCancelled()) {
                    log.info("Agent run cancelled before executing tool '{}': agentRunId={}", toolName, agentRunId);
                    currentState = AgentState.CANCELLED;
                    goal.setStatus(GameGoal.GoalStatus.CANCELLED);
                    listener.onRunCancelled(sessionId, agentRunId);
                    return AgentRunResult.cancelled(sessionId, agentRunId, iteration, totalToolCalls, allExecutions, userMessage);
                }

                // Check total tool calls limit
                if (totalToolCalls > limits.getMaxTotalToolCalls()) {
                    String maxToolCallsMsg = "Exceeded maximum total tool calls (" + limits.getMaxTotalToolCalls() + "). Halting execution.";
                    log.error(maxToolCallsMsg);
                    currentState = AgentState.FAILED;
                    listener.onRecoveryLimitReached(sessionId, agentRunId, maxToolCallsMsg);
                    listener.onGoalFailed(sessionId, agentRunId, maxToolCallsMsg);
                    return AgentRunResult.failure(sessionId, agentRunId, ErrorType.UNITY_EXECUTION_ERROR, maxToolCallsMsg, iteration, totalToolCalls, allExecutions, userMessage);
                }

                // Notify listener of tool execution start & update lifecycle state
                listener.onActivity(agentRunId, "Calling " + toolName + "...");
                listener.onToolStarted(agentRunId, toolCallId, toolName, call.getArguments());

                // State transitions and limit checks per tool
                if ("compile_project".equals(toolName)) {
                    compileAttempts++;
                    currentState = AgentState.COMPILING;
                    listener.onCompilationStarted(sessionId, agentRunId);
                    if (compileAttempts > limits.getMaxCompileAttempts()) {
                        String msg = "Exceeded maximum compilation attempts (" + limits.getMaxCompileAttempts() + "). Halting run.";
                        currentState = AgentState.FAILED;
                        listener.onRecoveryLimitReached(sessionId, agentRunId, msg);
                        listener.onGoalFailed(sessionId, agentRunId, msg);
                        return AgentRunResult.failure(sessionId, agentRunId, ErrorType.COMPILE_ERROR, msg, iteration, totalToolCalls, allExecutions, userMessage);
                    }
                } else if ("update_script".equals(toolName)) {
                    currentState = AgentState.REPAIRING;
                    String targetFile = call.getArguments() != null ? String.valueOf(call.getArguments().get("path")) : "script";
                    listener.onRepairStarted(sessionId, agentRunId, targetFile);
                    int fixes = fixAttemptsPerError.merge(targetFile, 1, Integer::sum);
                    if (fixes > limits.getMaxFixAttemptsPerError()) {
                        String msg = "Exceeded maximum fix attempts (" + limits.getMaxFixAttemptsPerError() + ") for file " + targetFile;
                        currentState = AgentState.FAILED;
                        listener.onRecoveryLimitReached(sessionId, agentRunId, msg);
                        listener.onGoalFailed(sessionId, agentRunId, msg);
                        return AgentRunResult.failure(sessionId, agentRunId, ErrorType.COMPILE_ERROR, msg, iteration, totalToolCalls, allExecutions, userMessage);
                    }
                    createdScriptOrModifiedScene = true;
                } else if ("create_script".equals(toolName)) {
                    currentState = AgentState.EXECUTING;
                    createdScriptOrModifiedScene = true;
                    if (call.getArguments() != null) {
                        String p = String.valueOf(call.getArguments().get("path"));
                        String c = String.valueOf(call.getArguments().get("content"));
                        checkpointRegistry.recordInitial(agentRunId, p, c, ScriptSafetyValidator.computeHash(c));
                    }
                } else if (toolName.startsWith("create_") || toolName.startsWith("set_") || toolName.startsWith("add_")) {
                    currentState = AgentState.EXECUTING;
                    createdScriptOrModifiedScene = true;
                } else if (toolName.startsWith("get_")) {
                    currentState = AgentState.INSPECTING;
                } else if ("enter_play_mode".equals(toolName) || "run_game_test".equals(toolName) || "exit_play_mode".equals(toolName)) {
                    runtimeValidationAttempts++;
                    currentState = AgentState.VALIDATING;
                    listener.onRuntimeTestStarted(sessionId, agentRunId, "rt_" + operationId.substring(0, 8));
                } else if ("validate_game_state".equals(toolName)) {
                    currentState = AgentState.VALIDATING;
                    listener.onValidationStarted(sessionId, agentRunId, "validate_game_state");
                }

                // Check same-tool retry limit
                int retryCount = toolRetryCounts.getOrDefault(toolName, 0);
                if (retryCount >= maxSameToolRetries) {
                    if (goalVerified) {
                        log.info("Tool '{}' reached retry limit but goal was already verified. Concluding run successfully.", toolName);
                        currentState = AgentState.COMPLETED;
                        goal.setStatus(GameGoal.GoalStatus.COMPLETED);
                        String finalMsg = "Game constructed and objectively verified in Unity.";
                        AgentRunResult finalSuccess = AgentRunResult.success(sessionId, agentRunId, finalMsg, iteration, totalToolCalls, allExecutions, userMessage);
                        listener.onGoalCompleted(sessionId, agentRunId, finalMsg);
                        listener.onRunCompleted(sessionId, agentRunId, finalSuccess);
                        return finalSuccess;
                    }
                    String retryError = "Tool '" + toolName + "' exceeded maximum retry limit (" + maxSameToolRetries + "). Halting execution.";
                    log.warn(retryError);
                    ToolExecutionResult failResult = ToolExecutionResult.failure(toolCallId, operationId, toolName, ErrorType.UNITY_EXECUTION_ERROR, retryError);
                    allExecutions.add(failResult);
                    listener.onToolFailed(agentRunId, toolCallId, toolName, failResult);
                    listener.onRunFailed(sessionId, agentRunId, retryError);
                    return AgentRunResult.failure(sessionId, agentRunId, ErrorType.UNITY_EXECUTION_ERROR, retryError, iteration, totalToolCalls, allExecutions, userMessage);
                }

                // Execute with full permission, mode, and parameter validation
                ToolExecutionResult result = executeTool(call, operationId, mode, projectId);
                allExecutions.add(result);

                // Add correlated tool result to conversation history
                ChatMessage toolResultMsg = ChatMessage.toolResult(toolCallId, toolName, result.toLLMResultString());
                conversation.add(toolResultMsg);
                memory.addMessage(sessionId, toolResultMsg);

                // Tool completion and diagnostic event emission
                if ("compile_project".equals(toolName)) {
                    boolean success = result.isSuccess() && (result.getResultData() == null || (!String.valueOf(result.getResultData()).contains("\"compilationState\":\"FAILED\"") && !String.valueOf(result.getResultData()).contains("compilationState=FAILED")));
                    listener.onCompilationCompleted(sessionId, agentRunId, success, result.getResultData());
                    if (success) {
                        compileAttempts = 0;
                    } else {
                        currentState = AgentState.DIAGNOSING;
                        listener.onDiagnosisStarted(sessionId, agentRunId, "Compilation failed with diagnostic errors");
                    }
                } else if ("enter_play_mode".equals(toolName) || "run_game_test".equals(toolName) || "exit_play_mode".equals(toolName)) {
                    boolean success = result.isSuccess();
                    listener.onRuntimeTestCompleted(sessionId, agentRunId, success, result.getResultData());
                    if (!success && runtimeValidationAttempts > limits.getMaxRuntimeValidationAttempts()) {
                        String msg = "Exceeded maximum runtime validation attempts (" + limits.getMaxRuntimeValidationAttempts() + ").";
                        currentState = AgentState.FAILED;
                        listener.onRecoveryLimitReached(sessionId, agentRunId, msg);
                        listener.onGoalFailed(sessionId, agentRunId, msg);
                        return AgentRunResult.failure(sessionId, agentRunId, ErrorType.RUNTIME_ERROR, msg, iteration, totalToolCalls, allExecutions, userMessage);
                    }
                } else if ("validate_game_state".equals(toolName)) {
                    boolean success = result.isSuccess() && (result.getResultData() == null 
                            || String.valueOf(result.getResultData()).contains("\"allRequirementsSatisfied\":true")
                            || String.valueOf(result.getResultData()).contains("allRequirementsSatisfied=true"));
                    listener.onValidationCompleted(sessionId, agentRunId, success, result.getResultData());
                    if (success) {
                        goalVerified = true;
                        goal.setStatus(GameGoal.GoalStatus.COMPLETED);
                    }
                }

                if (result.isSuccess()) {
                    listener.onToolCompleted(agentRunId, toolCallId, toolName, result);
                    consecutiveFailures = 0;
                    toolRetryCounts.remove(toolName);
                } else {
                    listener.onToolFailed(agentRunId, toolCallId, toolName, result);
                    consecutiveFailures++;
                    toolRetryCounts.merge(toolName, 1, Integer::sum);
                    log.warn("Tool '{}' failed (failures={}/{}): {}", toolName, consecutiveFailures, maxToolFailures, result.getErrorMessage());

                    if (consecutiveFailures >= maxToolFailures) {
                        String failMsg = "Exceeded maximum consecutive tool failures (" + maxToolFailures + "). Halting run.";
                        log.error(failMsg);
                        currentState = AgentState.FAILED;
                        listener.onRunFailed(sessionId, agentRunId, failMsg);
                        return AgentRunResult.failure(sessionId, agentRunId, ErrorType.UNITY_EXECUTION_ERROR, failMsg, iteration, totalToolCalls, allExecutions, userMessage);
                    }
                }
            }
        }

        String limitMsg = "Exceeded maximum agent iterations (" + maxIterations + ") without reaching completion.";
        log.warn(limitMsg);
        currentState = AgentState.FAILED;
        goal.setStatus(GameGoal.GoalStatus.FAILED);
        listener.onRunFailed(sessionId, agentRunId, limitMsg);
        return AgentRunResult.failure(sessionId, agentRunId, ErrorType.TIMEOUT, limitMsg, maxIterations, totalToolCalls, allExecutions, userMessage);
    }

    /**
     * Executes a single tool call with permission, mode, parameter, and connection checks.
     */
    private ToolExecutionResult executeTool(ToolCall call, String operationId, ToolMode currentMode, String projectId) {
        String toolName = call.getName();
        String toolCallId = call.getId();
        Map<String, Object> arguments = call.getArguments();

        // 1. Tool existence
        if (!toolRegistry.hasTool(toolName)) {
            return ToolExecutionResult.failure(toolCallId, operationId, toolName, ErrorType.UNKNOWN_TOOL,
                    "Unknown tool: '" + toolName + "'. Check registered tool list.");
        }

        Tool tool = toolRegistry.getTool(toolName);
        ToolDefinition def = tool.definition();

        // 2. Permission check
        if (def.getPermission() == ToolPermission.BLOCKED) {
            return ToolExecutionResult.failure(toolCallId, operationId, toolName, ErrorType.PERMISSION_DENIED,
                    "Tool '" + toolName + "' is BLOCKED by security policy.");
        }

        // 3. Mode check
        if (currentMode != null && !def.isAllowedInMode(currentMode)) {
            return ToolExecutionResult.failure(toolCallId, operationId, toolName, ErrorType.MODE_NOT_ALLOWED,
                    "Tool '" + toolName + "' is not allowed in mode " + currentMode);
        }

        // 4. Parameter validation
        String validationError = tool.validate(arguments);
        if (validationError != null) {
            return ToolExecutionResult.failure(toolCallId, operationId, toolName, ErrorType.VALIDATION_ERROR,
                    "Parameter validation failed: " + validationError);
        }

        // 5. Execution on Unity via WebSocket bridge
        try {
            UnityMessage response = (projectId != null && !projectId.isBlank())
                    ? unityExecutor.execute(projectId, operationId, toolName, arguments)
                    : unityExecutor.execute(operationId, toolName, arguments);
            if (Boolean.TRUE.equals(response.getSuccess())) {
                Object data = response.getData();
                return ToolExecutionResult.success(toolCallId, operationId, toolName, data != null ? data : "Success");
            } else {
                String errMsg = "Unity returned failure";
                if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                    errMsg = response.getErrors().get(0).getMessage();
                }
                return ToolExecutionResult.failure(toolCallId, operationId, toolName, ErrorType.UNITY_EXECUTION_ERROR, errMsg);
            }
        } catch (IllegalStateException e) {
            return ToolExecutionResult.failure(toolCallId, operationId, toolName, ErrorType.UNITY_CONNECTION_ERROR,
                    "Unity connection error: " + e.getMessage());
        } catch (TimeoutException e) {
            return ToolExecutionResult.failure(toolCallId, operationId, toolName, ErrorType.TIMEOUT,
                    "Unity execution timed out for tool: " + toolName);
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolCallId, operationId, toolName, ErrorType.UNITY_EXECUTION_ERROR,
                    "Execution exception: " + e.getMessage());
        }
    }
}
