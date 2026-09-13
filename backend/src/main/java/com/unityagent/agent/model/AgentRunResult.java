package com.unityagent.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the final outcome of an autonomous agent run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentRunResult {

    private final String sessionId;
    private final String agentRunId;
    private final boolean success;
    private final String response;
    private final int iterationsExecuted;
    private final int toolCallsExecuted;
    private final List<ToolExecutionResult> toolExecutions;
    private final ErrorType errorType;
    private final String errorMessage;
    private final String userMessage;

    public AgentRunResult(String sessionId,
                          String agentRunId,
                          boolean success,
                          String response,
                          int iterationsExecuted,
                          int toolCallsExecuted,
                          List<ToolExecutionResult> toolExecutions,
                          ErrorType errorType,
                          String errorMessage) {
        this(sessionId, agentRunId, success, response, iterationsExecuted, toolCallsExecuted, toolExecutions, errorType, errorMessage, null);
    }

    public AgentRunResult(String sessionId,
                          String agentRunId,
                          boolean success,
                          String response,
                          int iterationsExecuted,
                          int toolCallsExecuted,
                          List<ToolExecutionResult> toolExecutions,
                          ErrorType errorType,
                          String errorMessage,
                          String userMessage) {
        this.sessionId = sessionId;
        this.agentRunId = agentRunId;
        this.success = success;
        this.response = response;
        this.iterationsExecuted = iterationsExecuted;
        this.toolCallsExecuted = toolCallsExecuted;
        this.toolExecutions = toolExecutions != null ? Collections.unmodifiableList(new ArrayList<>(toolExecutions)) : List.of();
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.userMessage = userMessage;
    }

    public static AgentRunResult success(String sessionId, String agentRunId, String response, int iterations, int toolCalls, List<ToolExecutionResult> executions) {
        return new AgentRunResult(sessionId, agentRunId, true, response, iterations, toolCalls, executions, null, null, null);
    }

    public static AgentRunResult success(String sessionId, String agentRunId, String response, int iterations, int toolCalls, List<ToolExecutionResult> executions, String userMessage) {
        return new AgentRunResult(sessionId, agentRunId, true, response, iterations, toolCalls, executions, null, null, userMessage);
    }

    public static AgentRunResult failure(String sessionId, String agentRunId, ErrorType errorType, String errorMessage, int iterations, int toolCalls, List<ToolExecutionResult> executions) {
        return new AgentRunResult(sessionId, agentRunId, false, null, iterations, toolCalls, executions, errorType, errorMessage, null);
    }

    public static AgentRunResult failure(String sessionId, String agentRunId, ErrorType errorType, String errorMessage, int iterations, int toolCalls, List<ToolExecutionResult> executions, String userMessage) {
        return new AgentRunResult(sessionId, agentRunId, false, null, iterations, toolCalls, executions, errorType, errorMessage, userMessage);
    }

    public static AgentRunResult cancelled(String sessionId, String agentRunId, int iterations, int toolCalls, List<ToolExecutionResult> executions) {
        return new AgentRunResult(sessionId, agentRunId, false, "Agent run was cancelled by user request.", iterations, toolCalls, executions, ErrorType.CANCELLED, "Run cancelled", null);
    }

    public static AgentRunResult cancelled(String sessionId, String agentRunId, int iterations, int toolCalls, List<ToolExecutionResult> executions, String userMessage) {
        return new AgentRunResult(sessionId, agentRunId, false, "Agent run was cancelled by user request.", iterations, toolCalls, executions, ErrorType.CANCELLED, "Run cancelled", userMessage);
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentRunId() {
        return agentRunId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getResponse() {
        return response;
    }

    public int getIterationsExecuted() {
        return iterationsExecuted;
    }

    public int getIterations() {
        return iterationsExecuted;
    }

    public int getToolCallsExecuted() {
        return toolCallsExecuted;
    }

    public int getTotalToolCalls() {
        return toolCallsExecuted;
    }

    public boolean isCancelled() {
        return errorType == ErrorType.CANCELLED;
    }

    public List<ToolExecutionResult> getToolExecutions() {
        return toolExecutions;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "AgentRunResult{" +
                "sessionId='" + sessionId + '\'' +
                ", agentRunId='" + agentRunId + '\'' +
                ", success=" + success +
                ", iterations=" + iterationsExecuted +
                ", toolCalls=" + toolCallsExecuted +
                ", errorType=" + errorType +
                '}';
    }
}
