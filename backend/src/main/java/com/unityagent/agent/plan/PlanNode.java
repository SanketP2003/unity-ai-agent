package com.unityagent.agent.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.*;

/**
 * Granular node in a long-horizon DAG AgentPlan.
 * Represents a single development unit of work (CREATE, REUSE, MODIFY, or VERIFY).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanNode {

    public enum PlanNodeStatus {
        PENDING,
        READY,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        BLOCKED,
        SKIPPED
    }

    public enum PlanActionType {
        CREATE,
        REUSE,
        MODIFY,
        VERIFY
    }

    private String nodeId;
    private String description;
    private List<String> requirementIds;
    private List<String> dependencies; // Prerequisite node IDs that must be COMPLETED before this node is READY
    private List<String> candidateTools;
    private PlanNodeStatus status;
    private PlanActionType actionType;
    private int attemptCount;
    private String resultSummary;
    private String failureReason;
    private Instant startedAt;
    private Instant completedAt;
    private Map<String, Object> parameters;

    public PlanNode() {
        this.requirementIds = new ArrayList<>();
        this.dependencies = new ArrayList<>();
        this.candidateTools = new ArrayList<>();
        this.status = PlanNodeStatus.PENDING;
        this.actionType = PlanActionType.CREATE;
        this.attemptCount = 0;
        this.parameters = new LinkedHashMap<>();
    }

    public PlanNode(String nodeId, String description, PlanActionType actionType, List<String> candidateTools) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.description = description != null ? description : nodeId;
        this.actionType = actionType != null ? actionType : PlanActionType.CREATE;
        this.candidateTools = candidateTools != null ? new ArrayList<>(candidateTools) : new ArrayList<>();
        this.requirementIds = new ArrayList<>();
        this.dependencies = new ArrayList<>();
        this.status = PlanNodeStatus.PENDING;
        this.attemptCount = 0;
        this.parameters = new LinkedHashMap<>();
    }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getRequirementIds() { return requirementIds; }
    public void setRequirementIds(List<String> requirementIds) {
        this.requirementIds = requirementIds != null ? new ArrayList<>(requirementIds) : new ArrayList<>();
    }
    public void addRequirementId(String reqId) {
        if (reqId != null && !this.requirementIds.contains(reqId)) {
            this.requirementIds.add(reqId);
        }
    }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
    }
    public void addDependency(String depNodeId) {
        if (depNodeId != null && !this.dependencies.contains(depNodeId)) {
            this.dependencies.add(depNodeId);
        }
    }

    public List<String> getCandidateTools() { return candidateTools; }
    public void setCandidateTools(List<String> candidateTools) {
        this.candidateTools = candidateTools != null ? new ArrayList<>(candidateTools) : new ArrayList<>();
    }

    public PlanNodeStatus getStatus() { return status; }
    public void setStatus(PlanNodeStatus status) { this.status = status; }

    public PlanActionType getActionType() { return actionType; }
    public void setActionType(PlanActionType actionType) { this.actionType = actionType; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void incrementAttempts() { this.attemptCount++; }

    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters != null ? new LinkedHashMap<>(parameters) : new LinkedHashMap<>();
    }

    public boolean isCompleted() {
        return this.status == PlanNodeStatus.COMPLETED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanNode planNode = (PlanNode) o;
        return Objects.equals(nodeId, planNode.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }

    @Override
    public String toString() {
        return "PlanNode{" +
                "id='" + nodeId + '\'' +
                ", action=" + actionType +
                ", status=" + status +
                ", deps=" + dependencies +
                '}';
    }
}
