package com.unityagent.agent.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Tracks the live execution state and runtime metrics of an autonomous run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanExecutionState {

    private String agentRunId;
    private String sessionId;
    private String projectId;
    private String currentPlanId;
    private String currentNodeId;
    private int revision;
    private Set<String> completedNodes;
    private Set<String> failedNodes;
    private Set<String> blockedNodes;
    private int totalToolCalls;
    private int recoveryCycles;
    private int replans;
    private boolean active;
    private String currentActivity;
    private Instant startTime;
    private Instant finishTime;

    public PlanExecutionState() {
        this.completedNodes = new LinkedHashSet<>();
        this.failedNodes = new LinkedHashSet<>();
        this.blockedNodes = new LinkedHashSet<>();
        this.revision = 1;
        this.totalToolCalls = 0;
        this.recoveryCycles = 0;
        this.replans = 0;
        this.active = true;
        this.startTime = Instant.now();
    }

    public PlanExecutionState(String agentRunId, String sessionId, String projectId, String planId) {
        this.agentRunId = agentRunId;
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.currentPlanId = planId;
        this.revision = 1;
        this.completedNodes = new LinkedHashSet<>();
        this.failedNodes = new LinkedHashSet<>();
        this.blockedNodes = new LinkedHashSet<>();
        this.totalToolCalls = 0;
        this.recoveryCycles = 0;
        this.replans = 0;
        this.active = true;
        this.startTime = Instant.now();
    }

    public PlanExecutionState(String agentRunId, String planId, int totalNodes) {
        this(agentRunId, "session_" + agentRunId, "default", planId);
    }

    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getCurrentPlanId() { return currentPlanId; }
    public void setCurrentPlanId(String currentPlanId) { this.currentPlanId = currentPlanId; }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }

    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }

    public Set<String> getCompletedNodes() { return completedNodes; }
    public void setCompletedNodes(Set<String> completedNodes) {
        this.completedNodes = completedNodes != null ? new LinkedHashSet<>(completedNodes) : new LinkedHashSet<>();
    }

    public Set<String> getFailedNodes() { return failedNodes; }
    public void setFailedNodes(Set<String> failedNodes) {
        this.failedNodes = failedNodes != null ? new LinkedHashSet<>(failedNodes) : new LinkedHashSet<>();
    }

    public Set<String> getBlockedNodes() { return blockedNodes; }
    public void setBlockedNodes(Set<String> blockedNodes) {
        this.blockedNodes = blockedNodes != null ? new LinkedHashSet<>(blockedNodes) : new LinkedHashSet<>();
    }

    public int getTotalToolCalls() { return totalToolCalls; }
    public void setTotalToolCalls(int totalToolCalls) { this.totalToolCalls = totalToolCalls; }
    public void incrementToolCalls() { this.totalToolCalls++; }

    public int getRecoveryCycles() { return recoveryCycles; }
    public void setRecoveryCycles(int recoveryCycles) { this.recoveryCycles = recoveryCycles; }
    public void incrementRecoveryCycles() { this.recoveryCycles++; }

    public int getReplans() { return replans; }
    public void setReplans(int replans) { this.replans = replans; }
    public void incrementReplans() { this.replans++; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCurrentActivity() { return currentActivity; }
    public void setCurrentActivity(String currentActivity) { this.currentActivity = currentActivity; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getFinishTime() { return finishTime; }
    public void setFinishTime(Instant finishTime) { this.finishTime = finishTime; }

    public long getElapsedSeconds() {
        Instant end = finishTime != null ? finishTime : Instant.now();
        return Duration.between(startTime, end).toSeconds();
    }

    public void markNodeCompleted(String nodeId) {
        if (nodeId != null) {
            completedNodes.add(nodeId);
            failedNodes.remove(nodeId);
            blockedNodes.remove(nodeId);
        }
    }

    public void markNodeFailed(String nodeId) {
        if (nodeId != null) {
            failedNodes.add(nodeId);
        }
    }

    public void markNodeBlocked(String nodeId) {
        if (nodeId != null) {
            blockedNodes.add(nodeId);
        }
    }

    public int getTotalNodes() {
        return completedNodes.size() + failedNodes.size() + blockedNodes.size();
    }

    public int getCompletedNodesCount() {
        return completedNodes.size();
    }

    public void recordToolCalls(int count) {
        this.totalToolCalls += count;
    }

    public void recordNodeCompleted(String nodeId) {
        markNodeCompleted(nodeId);
    }

    public void recordRecoveryCycle() {
        incrementRecoveryCycles();
    }

    public void recordReplan() {
        incrementReplans();
    }
}
