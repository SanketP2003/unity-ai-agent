package com.unityagent.agent.event;

import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.model.ToolExecutionResult;

import java.util.List;
import java.util.Map;

/**
 * Dispatches agent events to multiple wrapped listeners.
 * Used to compose the SSE broadcast listener with the persistent memory listener.
 */
public class CompositeEventListener implements AgentEventListener {

    private final List<AgentEventListener> delegates;

    public CompositeEventListener(AgentEventListener... listeners) {
        this.delegates = List.of(listeners);
    }

    @Override
    public void onRunStarted(String sessionId, String agentRunId, String userMessage) {
        for (AgentEventListener l : delegates) l.onRunStarted(sessionId, agentRunId, userMessage);
    }

    @Override
    public void onActivity(String agentRunId, String activity) {
        for (AgentEventListener l : delegates) l.onActivity(agentRunId, activity);
    }

    @Override
    public void onToolStarted(String agentRunId, String toolCallId, String toolName, Map<String, Object> arguments) {
        for (AgentEventListener l : delegates) l.onToolStarted(agentRunId, toolCallId, toolName, arguments);
    }

    @Override
    public void onToolCompleted(String agentRunId, String toolCallId, String toolName, ToolExecutionResult result) {
        for (AgentEventListener l : delegates) l.onToolCompleted(agentRunId, toolCallId, toolName, result);
    }

    @Override
    public void onToolFailed(String agentRunId, String toolCallId, String toolName, ToolExecutionResult result) {
        for (AgentEventListener l : delegates) l.onToolFailed(agentRunId, toolCallId, toolName, result);
    }

    @Override
    public void onRunCompleted(String sessionId, String agentRunId, AgentRunResult result) {
        for (AgentEventListener l : delegates) l.onRunCompleted(sessionId, agentRunId, result);
    }

    @Override
    public void onRunFailed(String sessionId, String agentRunId, String errorMessage) {
        for (AgentEventListener l : delegates) l.onRunFailed(sessionId, agentRunId, errorMessage);
    }

    @Override
    public void onRunCancelled(String sessionId, String agentRunId) {
        for (AgentEventListener l : delegates) l.onRunCancelled(sessionId, agentRunId);
    }

    @Override
    public void onEvent(AgentEvent event) {
        for (AgentEventListener l : delegates) l.onEvent(event);
    }

    @Override
    public void onGoalCreated(String sessionId, String agentRunId, String description) {
        for (AgentEventListener l : delegates) l.onGoalCreated(sessionId, agentRunId, description);
    }

    @Override
    public void onGoalCompleted(String sessionId, String agentRunId, Object summary) {
        for (AgentEventListener l : delegates) l.onGoalCompleted(sessionId, agentRunId, summary);
    }

    @Override
    public void onGoalFailed(String sessionId, String agentRunId, String reason) {
        for (AgentEventListener l : delegates) l.onGoalFailed(sessionId, agentRunId, reason);
    }

    @Override
    public void onPlanCreated(String sessionId, String agentRunId, Object plan) {
        for (AgentEventListener l : delegates) l.onPlanCreated(sessionId, agentRunId, plan);
    }

    @Override
    public void onPlanStepStarted(String sessionId, String agentRunId, String stepId, String description) {
        for (AgentEventListener l : delegates) l.onPlanStepStarted(sessionId, agentRunId, stepId, description);
    }

    @Override
    public void onPlanStepCompleted(String sessionId, String agentRunId, String stepId, String description) {
        for (AgentEventListener l : delegates) l.onPlanStepCompleted(sessionId, agentRunId, stepId, description);
    }

    @Override
    public void onCompilationStarted(String sessionId, String agentRunId) {
        for (AgentEventListener l : delegates) l.onCompilationStarted(sessionId, agentRunId);
    }

    @Override
    public void onCompilationCompleted(String sessionId, String agentRunId, boolean success, Object diagnostics) {
        for (AgentEventListener l : delegates) l.onCompilationCompleted(sessionId, agentRunId, success, diagnostics);
    }

    @Override
    public void onDiagnosisStarted(String sessionId, String agentRunId, String diagnosticSummary) {
        for (AgentEventListener l : delegates) l.onDiagnosisStarted(sessionId, agentRunId, diagnosticSummary);
    }

    @Override
    public void onRepairStarted(String sessionId, String agentRunId, String targetFile) {
        for (AgentEventListener l : delegates) l.onRepairStarted(sessionId, agentRunId, targetFile);
    }

    @Override
    public void onRecoveryLimitReached(String sessionId, String agentRunId, String limitDetails) {
        for (AgentEventListener l : delegates) l.onRecoveryLimitReached(sessionId, agentRunId, limitDetails);
    }

    @Override
    public void onRuntimeTestStarted(String sessionId, String agentRunId, String testId) {
        for (AgentEventListener l : delegates) l.onRuntimeTestStarted(sessionId, agentRunId, testId);
    }

    @Override
    public void onRuntimeTestCompleted(String sessionId, String agentRunId, boolean success, Object details) {
        for (AgentEventListener l : delegates) l.onRuntimeTestCompleted(sessionId, agentRunId, success, details);
    }

    @Override
    public void onValidationStarted(String sessionId, String agentRunId, String validationType) {
        for (AgentEventListener l : delegates) l.onValidationStarted(sessionId, agentRunId, validationType);
    }

    @Override
    public void onValidationCompleted(String sessionId, String agentRunId, boolean success, Object results) {
        for (AgentEventListener l : delegates) l.onValidationCompleted(sessionId, agentRunId, success, results);
    }
}
