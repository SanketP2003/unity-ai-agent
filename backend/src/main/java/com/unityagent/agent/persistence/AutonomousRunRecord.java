package com.unityagent.agent.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent snapshot record of an autonomous development run.
 * Stored in SQLite for crash recovery, diagnostics, and session continuity.
 *
 * <p>Security: Never stores API keys, tokens, auth headers, or hidden LLM reasoning.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutonomousRunRecord {

    private String runId;
    private String sessionId;
    private String projectId;
    private String goalText;
    private String status;
    private Instant startTime;
    private Instant lastUpdate;
    private Instant completedAt;
    private int currentPlanRevision;
    private String activeNodeId;
    private List<String> completedNodes = new ArrayList<>();
    private List<String> failedNodes = new ArrayList<>();
    private int recoveryCount;
    private int replanCount;
    private int toolCallCount;
    private Map<String, String> requirementStates = new HashMap<>();
    private String completionState;
    private String checkpointRef;
    private String finalValidationResult;
    private String planJson;

    public AutonomousRunRecord() {
    }

    public AutonomousRunRecord(String runId, String sessionId, String projectId, String goalText, String status) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.goalText = goalText;
        this.status = status;
        this.startTime = Instant.now();
        this.lastUpdate = this.startTime;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getGoalText() {
        return goalText;
    }

    public void setGoalText(String goalText) {
        this.goalText = goalText;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getCurrentPlanRevision() {
        return currentPlanRevision;
    }

    public void setCurrentPlanRevision(int currentPlanRevision) {
        this.currentPlanRevision = currentPlanRevision;
    }

    public String getActiveNodeId() {
        return activeNodeId;
    }

    public void setActiveNodeId(String activeNodeId) {
        this.activeNodeId = activeNodeId;
    }

    public List<String> getCompletedNodes() {
        return completedNodes;
    }

    public void setCompletedNodes(List<String> completedNodes) {
        this.completedNodes = completedNodes != null ? completedNodes : new ArrayList<>();
    }

    public List<String> getFailedNodes() {
        return failedNodes;
    }

    public void setFailedNodes(List<String> failedNodes) {
        this.failedNodes = failedNodes != null ? failedNodes : new ArrayList<>();
    }

    public int getRecoveryCount() {
        return recoveryCount;
    }

    public void setRecoveryCount(int recoveryCount) {
        this.recoveryCount = recoveryCount;
    }

    public int getReplanCount() {
        return replanCount;
    }

    public void setReplanCount(int replanCount) {
        this.replanCount = replanCount;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(int toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public Map<String, String> getRequirementStates() {
        return requirementStates;
    }

    public void setRequirementStates(Map<String, String> requirementStates) {
        this.requirementStates = requirementStates != null ? requirementStates : new HashMap<>();
    }

    public String getCompletionState() {
        return completionState;
    }

    public void setCompletionState(String completionState) {
        this.completionState = completionState;
    }

    public String getCheckpointRef() {
        return checkpointRef;
    }

    public void setCheckpointRef(String checkpointRef) {
        this.checkpointRef = checkpointRef;
    }

    public String getFinalValidationResult() {
        return finalValidationResult;
    }

    public void setFinalValidationResult(String finalValidationResult) {
        this.finalValidationResult = finalValidationResult;
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }
}
