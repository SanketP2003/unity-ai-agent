package com.unityagent.agent.recovery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnostic context capturing the specifics of a failed execution step.
 */
public class FailureContext {

    private String nodeId;
    private String toolName;
    private String rawErrorMessage;
    private FailureType failureType;
    private String errorCode; // e.g. "CS1002", "CS0246"
    private String targetFileOrAsset;
    private String targetEntity;
    private int attemptCount;
    private Map<String, Object> diagnosticDetails;

    public FailureContext() {
        this.diagnosticDetails = new LinkedHashMap<>();
    }

    public FailureContext(String nodeId, String toolName, String rawErrorMessage,
                          FailureType failureType, String errorCode) {
        this.nodeId = nodeId;
        this.toolName = toolName;
        this.rawErrorMessage = rawErrorMessage;
        this.failureType = failureType != null ? failureType : FailureType.UNKNOWN;
        this.errorCode = errorCode;
        this.diagnosticDetails = new LinkedHashMap<>();
        this.attemptCount = 1;
    }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getRawErrorMessage() { return rawErrorMessage; }
    public void setRawErrorMessage(String rawErrorMessage) { this.rawErrorMessage = rawErrorMessage; }

    public FailureType getFailureType() { return failureType; }
    public void setFailureType(FailureType failureType) { this.failureType = failureType; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getTargetFileOrAsset() { return targetFileOrAsset; }
    public void setTargetFileOrAsset(String targetFileOrAsset) { this.targetFileOrAsset = targetFileOrAsset; }

    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void incrementAttempts() { this.attemptCount++; }

    public Map<String, Object> getDiagnosticDetails() { return diagnosticDetails; }
    public void setDiagnosticDetails(Map<String, Object> details) {
        this.diagnosticDetails = details != null ? new LinkedHashMap<>(details) : new LinkedHashMap<>();
    }

    @Override
    public String toString() {
        return "FailureContext{" +
                "node='" + nodeId + '\'' +
                ", tool='" + toolName + '\'' +
                ", type=" + failureType +
                ", code='" + errorCode + '\'' +
                ", attempts=" + attemptCount +
                '}';
    }
}
