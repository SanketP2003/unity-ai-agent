package com.unityagent.agent.reliability;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the execution state and lifecycle of an individual tool invocation.
 * Supports UNKNOWN resolution upon connection loss.
 */
public class ToolExecutionRecord {

    private final String toolCallId;
    private final String operationId;
    private final String projectId;
    private final String agentRunId;
    private final String toolName;
    private final Map<String, Object> parameters;
    private final int timeoutSeconds;
    private final Instant startTimestamp;
    private Instant completionTimestamp;
    private volatile ToolExecutionStatus resultStatus;
    private Object resultData;
    private String errorMessage;

    public ToolExecutionRecord(String toolCallId, String operationId, String projectId,
                               String agentRunId, String toolName, Map<String, Object> parameters,
                               int timeoutSeconds) {
        this.toolCallId = toolCallId != null ? toolCallId : "tc_" + UUID.randomUUID().toString().substring(0, 8);
        this.operationId = operationId != null ? operationId : UUID.randomUUID().toString();
        this.projectId = projectId;
        this.agentRunId = agentRunId;
        this.toolName = toolName;
        this.parameters = parameters;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.startTimestamp = Instant.now();
        this.resultStatus = ToolExecutionStatus.PENDING;
    }

    public void markRunning() {
        this.resultStatus = ToolExecutionStatus.RUNNING;
    }

    public void markSucceeded(Object data) {
        this.completionTimestamp = Instant.now();
        this.resultStatus = ToolExecutionStatus.SUCCEEDED;
        this.resultData = data;
    }

    public void markFailed(String message) {
        this.completionTimestamp = Instant.now();
        this.resultStatus = ToolExecutionStatus.FAILED;
        this.errorMessage = message;
    }

    public void markTimedOut() {
        this.completionTimestamp = Instant.now();
        this.resultStatus = ToolExecutionStatus.TIMED_OUT;
        this.errorMessage = "Tool execution timed out after " + timeoutSeconds + "s";
    }

    public void markCancelled() {
        this.completionTimestamp = Instant.now();
        this.resultStatus = ToolExecutionStatus.CANCELLED;
        this.errorMessage = "Tool execution cancelled";
    }

    public void markUnknown(String reason) {
        this.completionTimestamp = Instant.now();
        this.resultStatus = ToolExecutionStatus.UNKNOWN;
        this.errorMessage = reason != null ? reason : "Connection lost while tool was in-flight";
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getAgentRunId() {
        return agentRunId;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Instant getStartTimestamp() {
        return startTimestamp;
    }

    public Instant getCompletionTimestamp() {
        return completionTimestamp;
    }

    public ToolExecutionStatus getResultStatus() {
        return resultStatus;
    }

    public Object getResultData() {
        return resultData;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
