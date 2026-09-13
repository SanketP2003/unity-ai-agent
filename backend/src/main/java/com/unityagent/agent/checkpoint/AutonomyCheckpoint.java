package com.unityagent.agent.checkpoint;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.*;

/**
 * Milestone checkpoint snapshot persisted during long-horizon runs.
 * Enables safe, non-blind resumption if the agent or backend is interrupted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AutonomyCheckpoint {

    private String checkpointId;
    private String projectId;
    private String agentRunId;
    private String planId;
    private int planRevision;
    private List<String> completedNodes;
    private String currentNodeId;
    private Map<String, String> requirementStatusMap;
    private Map<String, Object> metrics;
    private Instant timestamp;

    public AutonomyCheckpoint() {
        this.completedNodes = new ArrayList<>();
        this.requirementStatusMap = new LinkedHashMap<>();
        this.metrics = new LinkedHashMap<>();
        this.timestamp = Instant.now();
    }

    public AutonomyCheckpoint(String checkpointId, String projectId, String agentRunId, String planId,
                              int planRevision, List<String> completedNodes, String currentNodeId,
                              Map<String, String> requirementStatusMap, Map<String, Object> metrics) {
        this.checkpointId = checkpointId;
        this.projectId = projectId;
        this.agentRunId = agentRunId;
        this.planId = planId;
        this.planRevision = planRevision;
        this.completedNodes = completedNodes != null ? List.copyOf(completedNodes) : List.of();
        this.currentNodeId = currentNodeId;
        this.requirementStatusMap = requirementStatusMap != null ? new LinkedHashMap<>(requirementStatusMap) : new LinkedHashMap<>();
        this.metrics = metrics != null ? new LinkedHashMap<>(metrics) : new LinkedHashMap<>();
        this.timestamp = Instant.now();
    }

    public String getCheckpointId() { return checkpointId; }
    public void setCheckpointId(String checkpointId) { this.checkpointId = checkpointId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public int getPlanRevision() { return planRevision; }
    public void setPlanRevision(int planRevision) { this.planRevision = planRevision; }

    public List<String> getCompletedNodes() { return completedNodes; }
    public void setCompletedNodes(List<String> completedNodes) {
        this.completedNodes = completedNodes != null ? List.copyOf(completedNodes) : List.of();
    }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }

    public Map<String, String> getRequirementStatusMap() { return requirementStatusMap; }
    public void setRequirementStatusMap(Map<String, String> requirementStatusMap) {
        this.requirementStatusMap = requirementStatusMap != null ? new LinkedHashMap<>(requirementStatusMap) : new LinkedHashMap<>();
    }

    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics != null ? new LinkedHashMap<>(metrics) : new LinkedHashMap<>();
    }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "AutonomyCheckpoint{" +
                "id='" + checkpointId + '\'' +
                ", project='" + projectId + '\'' +
                ", plan='" + planId + '\'' +
                ", rev=" + planRevision +
                ", completed=" + completedNodes.size() +
                '}';
    }
}
