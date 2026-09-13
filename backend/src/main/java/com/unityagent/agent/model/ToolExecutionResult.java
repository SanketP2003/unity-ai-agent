package com.unityagent.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encapsulates the structured outcome of a tool execution.
 * Preserves correlation between toolCallId and operationId.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolExecutionResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String toolCallId;
    private final String operationId;
    private final String toolName;
    private final boolean success;
    private final Object resultData;
    private final ErrorType errorType;
    private final String errorMessage;

    public ToolExecutionResult(String toolCallId,
                               String operationId,
                               String toolName,
                               boolean success,
                               Object resultData,
                               ErrorType errorType,
                               String errorMessage) {
        this.toolCallId = toolCallId;
        this.operationId = operationId;
        this.toolName = toolName;
        this.success = success;
        this.resultData = resultData;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public static ToolExecutionResult success(String toolCallId, String operationId, String toolName, Object resultData) {
        return new ToolExecutionResult(toolCallId, operationId, toolName, true, resultData, null, null);
    }

    public static ToolExecutionResult failure(String toolCallId, String operationId, String toolName, ErrorType errorType, String errorMessage) {
        return new ToolExecutionResult(toolCallId, operationId, toolName, false, null, errorType, errorMessage);
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getResultData() {
        return resultData;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Converts the tool outcome into a clean structured JSON string for the LLM.
     */
    public String toLLMResultString() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tool", toolName);
        map.put("success", success);
        if (operationId != null) {
            map.put("operationId", operationId);
        }
        if (success) {
            map.put("result", resultData != null ? resultData : "Operation completed successfully");
        } else {
            map.put("errorType", errorType != null ? errorType.name() : "UNKNOWN_ERROR");
            map.put("error", errorMessage != null ? errorMessage : "Operation failed");
        }

        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"success\":" + success + ",\"error\":\"" + errorMessage + "\"}";
        }
    }

    @Override
    public String toString() {
        return "ToolExecutionResult{" +
                "toolCallId='" + toolCallId + '\'' +
                ", operationId='" + operationId + '\'' +
                ", toolName='" + toolName + '\'' +
                ", success=" + success +
                ", errorType=" + errorType +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
