package com.unityagent.agent.event;

import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.model.ToolExecutionResult;

import java.util.Map;

/**
 * Event listener callback interface invoked by AgentLoop during execution.
 * Allows decoupled observers (e.g. AgentService, SSE broadcasters) to track
 * run lifecycle, high-level agent activity, and tool executions.
 *
 * <p>Security & Privacy:
 * Callbacks receive only high-level activity summaries and structured tool data.
 * Hidden chain-of-thought (reasoning_content) and credentials are never passed.
 */
public interface AgentEventListener {

    void onRunStarted(String sessionId, String agentRunId, String userMessage);

    void onActivity(String agentRunId, String activity);

    void onToolStarted(String agentRunId, String toolCallId, String toolName, Map<String, Object> arguments);

    void onToolCompleted(String agentRunId, String toolCallId, String toolName, ToolExecutionResult result);

    void onToolFailed(String agentRunId, String toolCallId, String toolName, ToolExecutionResult result);

    void onRunCompleted(String sessionId, String agentRunId, AgentRunResult result);

    void onRunFailed(String sessionId, String agentRunId, String errorMessage);

    void onRunCancelled(String sessionId, String agentRunId);

    default void onEvent(AgentEvent event) {}

    default void onGoalCreated(String sessionId, String agentRunId, String description) {}
    default void onGoalCompleted(String sessionId, String agentRunId, Object summary) {}
    default void onGoalFailed(String sessionId, String agentRunId, String reason) {}

    default void onPlanCreated(String sessionId, String agentRunId, Object plan) {}
    default void onPlanStepStarted(String sessionId, String agentRunId, String stepId, String description) {}
    default void onPlanStepCompleted(String sessionId, String agentRunId, String stepId, String description) {}

    default void onCompilationStarted(String sessionId, String agentRunId) {}
    default void onCompilationCompleted(String sessionId, String agentRunId, boolean success, Object diagnostics) {}

    default void onDiagnosisStarted(String sessionId, String agentRunId, String diagnosticSummary) {}
    default void onRepairStarted(String sessionId, String agentRunId, String targetFile) {}
    default void onRecoveryLimitReached(String sessionId, String agentRunId, String limitDetails) {}

    default void onRuntimeTestStarted(String sessionId, String agentRunId, String testId) {}
    default void onRuntimeTestCompleted(String sessionId, String agentRunId, boolean success, Object details) {}

    default void onValidationStarted(String sessionId, String agentRunId, String validationType) {}
    default void onValidationCompleted(String sessionId, String agentRunId, boolean success, Object results) {}

    /**
     * Default NOOP implementation.
     */
    AgentEventListener NOOP = new AgentEventListener() {
        @Override public void onRunStarted(String sessionId, String agentRunId, String userMessage) {}
        @Override public void onActivity(String agentRunId, String activity) {}
        @Override public void onToolStarted(String agentRunId, String toolCallId, String toolName, Map<String, Object> arguments) {}
        @Override public void onToolCompleted(String agentRunId, String toolCallId, String toolName, ToolExecutionResult result) {}
        @Override public void onToolFailed(String agentRunId, String toolCallId, String toolName, ToolExecutionResult result) {}
        @Override public void onRunCompleted(String sessionId, String agentRunId, AgentRunResult result) {}
        @Override public void onRunFailed(String sessionId, String agentRunId, String errorMessage) {}
        @Override public void onRunCancelled(String sessionId, String agentRunId) {}
    };
}
