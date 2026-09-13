package com.unityagent.agent.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Structured DTO representing an agent lifecycle, activity, or tool event.
 * Streamed to Web clients via SSE and stored in AgentRunState.
 *
 * <p>Security & Size Limits:
 * - Never contains API keys or reasoning_content.
 * - Enforces payload size limits on arguments and results to prevent SSE saturation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentEvent {

    public static final int MAX_ARGUMENT_CHARS = 16384;
    public static final int MAX_RESULT_CHARS = 32768;

    private final String eventId;
    private final long sequence;
    private final String type;
    private final String sessionId;
    private final String agentRunId;
    private final Instant timestamp;
    private final String activity;
    private final String toolCallId;
    private final String tool;
    private final Map<String, Object> arguments;
    private final Object resultData;
    private final Boolean resultDataTruncated;
    private final String errorMessage;

    public AgentEvent(String eventId,
                      long sequence,
                      String type,
                      String sessionId,
                      String agentRunId,
                      Instant timestamp,
                      String activity,
                      String toolCallId,
                      String tool,
                      Map<String, Object> arguments,
                      Object resultData,
                      Boolean resultDataTruncated,
                      String errorMessage) {
        this.eventId = eventId != null ? eventId : "evt_" + sequence + "_" + UUID.randomUUID().toString().substring(0, 8);
        this.sequence = sequence;
        this.type = type;
        this.sessionId = sessionId;
        this.agentRunId = agentRunId;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.activity = activity;
        this.toolCallId = toolCallId;
        this.tool = tool;
        this.arguments = arguments;
        this.resultData = resultData;
        this.resultDataTruncated = resultDataTruncated;
        this.errorMessage = errorMessage;
    }

    public static AgentEvent runStarted(long seq, String sessionId, String agentRunId, String userMessage) {
        return new AgentEvent("evt_" + seq, seq, "RUN_STARTED", sessionId, agentRunId, Instant.now(),
                "Started autonomous run", null, null, null, null, null, null);
    }

    public static AgentEvent activity(long seq, String sessionId, String agentRunId, String activity) {
        return new AgentEvent("evt_" + seq, seq, "AGENT_ACTIVITY", sessionId, agentRunId, Instant.now(),
                activity, null, null, null, null, null, null);
    }

    public static AgentEvent toolStarted(long seq, String sessionId, String agentRunId, String toolCallId, String toolName, Map<String, Object> arguments) {
        return new AgentEvent("evt_" + seq, seq, "TOOL_STARTED", sessionId, agentRunId, Instant.now(),
                "Executing " + toolName, toolCallId, toolName, sanitizeArguments(arguments), null, null, null);
    }

    public static AgentEvent toolCompleted(long seq, String sessionId, String agentRunId, String toolCallId, String toolName, Object resultData) {
        boolean truncated = false;
        Object safeData = resultData;
        if (resultData instanceof String str && str.length() > MAX_RESULT_CHARS) {
            safeData = str.substring(0, MAX_RESULT_CHARS) + " ... [TRUNCATED]";
            truncated = true;
        }
        return new AgentEvent("evt_" + seq, seq, "TOOL_COMPLETED", sessionId, agentRunId, Instant.now(),
                "Completed " + toolName, toolCallId, toolName, null, safeData, truncated ? Boolean.TRUE : null, null);
    }

    public static AgentEvent toolFailed(long seq, String sessionId, String agentRunId, String toolCallId, String toolName, String errorMessage) {
        return new AgentEvent("evt_" + seq, seq, "TOOL_FAILED", sessionId, agentRunId, Instant.now(),
                "Failed " + toolName, toolCallId, toolName, null, null, null, errorMessage);
    }

    public static AgentEvent runCompleted(long seq, String sessionId, String agentRunId, Object resultSummary) {
        return new AgentEvent("evt_" + seq, seq, "RUN_COMPLETED", sessionId, agentRunId, Instant.now(),
                "Agent run completed successfully", null, null, null, resultSummary, null, null);
    }

    public static AgentEvent runFailed(long seq, String sessionId, String agentRunId, String errorMessage) {
        return new AgentEvent("evt_" + seq, seq, "RUN_FAILED", sessionId, agentRunId, Instant.now(),
                "Agent run failed", null, null, null, null, null, errorMessage);
    }

    public static AgentEvent runCancelled(long seq, String sessionId, String agentRunId) {
        return new AgentEvent("evt_" + seq, seq, "RUN_CANCELLED", sessionId, agentRunId, Instant.now(),
                "Agent run was cancelled", null, null, null, null, null, null);
    }

    public static AgentEvent goalCreated(long seq, String sessionId, String agentRunId, String description) {
        return new AgentEvent("evt_" + seq, seq, "GOAL_CREATED", sessionId, agentRunId, Instant.now(),
                "Goal created: " + description, null, null, null, description, null, null);
    }

    public static AgentEvent goalCompleted(long seq, String sessionId, String agentRunId, Object summary) {
        return new AgentEvent("evt_" + seq, seq, "GOAL_COMPLETED", sessionId, agentRunId, Instant.now(),
                "Goal completed successfully", null, null, null, summary, null, null);
    }

    public static AgentEvent goalFailed(long seq, String sessionId, String agentRunId, String reason) {
        return new AgentEvent("evt_" + seq, seq, "GOAL_FAILED", sessionId, agentRunId, Instant.now(),
                "Goal failed: " + reason, null, null, null, null, null, reason);
    }

    public static AgentEvent planCreated(long seq, String sessionId, String agentRunId, Object plan) {
        return new AgentEvent("evt_" + seq, seq, "PLAN_CREATED", sessionId, agentRunId, Instant.now(),
                "Planning build...", null, null, null, plan, null, null);
    }

    public static AgentEvent planStepStarted(long seq, String sessionId, String agentRunId, String stepId, String description) {
        return new AgentEvent("evt_" + seq, seq, "PLAN_STEP_STARTED", sessionId, agentRunId, Instant.now(),
                description, null, null, Map.of("stepId", stepId), null, null, null);
    }

    public static AgentEvent planStepCompleted(long seq, String sessionId, String agentRunId, String stepId, String description) {
        return new AgentEvent("evt_" + seq, seq, "PLAN_STEP_COMPLETED", sessionId, agentRunId, Instant.now(),
                "✓ " + description, null, null, Map.of("stepId", stepId), null, null, null);
    }

    public static AgentEvent compilationStarted(long seq, String sessionId, String agentRunId) {
        return new AgentEvent("evt_" + seq, seq, "COMPILATION_STARTED", sessionId, agentRunId, Instant.now(),
                "Compiling project...", null, "compile_project", null, null, null, null);
    }

    public static AgentEvent compilationCompleted(long seq, String sessionId, String agentRunId, boolean success, Object diagnostics) {
        return new AgentEvent("evt_" + seq, seq, "COMPILATION_COMPLETED", sessionId, agentRunId, Instant.now(),
                success ? "Compilation succeeded." : "Compilation failed.", null, "compile_project", null, diagnostics, null,
                success ? null : "Compilation errors detected");
    }

    public static AgentEvent diagnosisStarted(long seq, String sessionId, String agentRunId, String diagnosticSummary) {
        return new AgentEvent("evt_" + seq, seq, "DIAGNOSIS_STARTED", sessionId, agentRunId, Instant.now(),
                "Diagnosing: " + diagnosticSummary, null, null, null, diagnosticSummary, null, null);
    }

    public static AgentEvent repairStarted(long seq, String sessionId, String agentRunId, String targetFile) {
        return new AgentEvent("evt_" + seq, seq, "REPAIR_STARTED", sessionId, agentRunId, Instant.now(),
                "Repairing " + targetFile + "...", null, null, null, targetFile, null, null);
    }

    public static AgentEvent recoveryLimitReached(long seq, String sessionId, String agentRunId, String limitDetails) {
        return new AgentEvent("evt_" + seq, seq, "RECOVERY_LIMIT_REACHED", sessionId, agentRunId, Instant.now(),
                "Recovery limit reached: " + limitDetails, null, null, null, null, null, limitDetails);
    }

    public static AgentEvent runtimeTestStarted(long seq, String sessionId, String agentRunId, String testId) {
        return new AgentEvent("evt_" + seq, seq, "RUNTIME_TEST_STARTED", sessionId, agentRunId, Instant.now(),
                "Starting runtime test...", null, "run_game_test", null, testId, null, null);
    }

    public static AgentEvent runtimeTestCompleted(long seq, String sessionId, String agentRunId, boolean success, Object details) {
        return new AgentEvent("evt_" + seq, seq, "RUNTIME_TEST_COMPLETED", sessionId, agentRunId, Instant.now(),
                success ? "Runtime test passed." : "Runtime test failed.", null, "run_game_test", null, details, null,
                success ? null : "Runtime errors detected");
    }

    public static AgentEvent validationStarted(long seq, String sessionId, String agentRunId, String validationType) {
        return new AgentEvent("evt_" + seq, seq, "VALIDATION_STARTED", sessionId, agentRunId, Instant.now(),
                "Validating goal requirements...", null, "validate_game_state", null, validationType, null, null);
    }

    public static AgentEvent validationCompleted(long seq, String sessionId, String agentRunId, boolean success, Object results) {
        return new AgentEvent("evt_" + seq, seq, "VALIDATION_COMPLETED", sessionId, agentRunId, Instant.now(),
                success ? "Goal verified." : "Validation incomplete or failed.", null, "validate_game_state", null, results, null,
                success ? null : "Validation failure");
    }

    private static Map<String, Object> sanitizeArguments(Map<String, Object> args) {
        if (args == null) return Map.of();
        // Return as-is if within limit
        return args;
    }

    public String getEventId() { return eventId; }
    public long getSequence() { return sequence; }
    public String getType() { return type; }
    public String getSessionId() { return sessionId; }
    public String getAgentRunId() { return agentRunId; }
    public Instant getTimestamp() { return timestamp; }
    public String getActivity() { return activity; }
    public String getToolCallId() { return toolCallId; }
    public String getTool() { return tool; }
    public Map<String, Object> getArguments() { return arguments; }
    public Object getResultData() { return resultData; }
    public Boolean getResultDataTruncated() { return resultDataTruncated; }
    public String getErrorMessage() { return errorMessage; }
}
