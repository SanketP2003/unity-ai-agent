package com.unityagent.agent.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mutable, dynamic plan for an autonomous agent run.
 * Supports both step-by-step linear execution (Phase 6 backward compatibility)
 * and complex non-linear DAG branching with 50+ nodes and immutable revisions (Phase 9).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentPlan {

    public enum PlanStatus {
        DRAFT,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED,
        REPLANNING
    }

    private String planId;
    private String goalId;
    private PlanStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    // Linear step representation (backward compatibility)
    private List<PlanStep> steps;
    private int currentStepIndex;

    // DAG representation (Phase 9)
    private Map<String, PlanNode> planNodes;
    private List<PlanDependency> dependencies;
    private List<PlanRevision> revisions;
    private int currentRevisionNumber;

    public AgentPlan() {
        this.steps = new ArrayList<>();
        this.currentStepIndex = 0;
        this.status = PlanStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();

        this.planNodes = new LinkedHashMap<>();
        this.dependencies = new ArrayList<>();
        this.revisions = new ArrayList<>();
        this.currentRevisionNumber = 1;
    }

    public AgentPlan(String planId, String goalId) {
        this.planId = planId != null ? planId : "plan_" + UUID.randomUUID().toString().substring(0, 8);
        this.goalId = goalId;
        this.steps = new ArrayList<>();
        this.currentStepIndex = 0;
        this.status = PlanStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();

        this.planNodes = new LinkedHashMap<>();
        this.dependencies = new ArrayList<>();
        this.revisions = new ArrayList<>();
        this.currentRevisionNumber = 1;
    }

    // --- Linear Step Methods (Phase 6/7) ---

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getGoalId() { return goalId; }
    public void setGoalId(String goalId) { this.goalId = goalId; }

    public List<PlanStep> getSteps() { return steps; }
    public void setSteps(List<PlanStep> steps) {
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        this.updatedAt = Instant.now();
    }

    public int getCurrentStepIndex() { return currentStepIndex; }
    public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }

    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public void addStep(PlanStep step) {
        if (step != null) {
            this.steps.add(step);
            this.updatedAt = Instant.now();
        }
    }

    public void insertStep(int index, PlanStep step) {
        if (step != null) {
            int targetIndex = Math.max(0, Math.min(index, this.steps.size()));
            this.steps.add(targetIndex, step);
            this.updatedAt = Instant.now();
        }
    }

    public PlanStep getCurrentStep() {
        if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }

    public void markCurrentStepCompleted() {
        PlanStep current = getCurrentStep();
        if (current != null) {
            current.setStatus(PlanStep.StepStatus.COMPLETED);
            if (currentStepIndex + 1 < steps.size()) {
                currentStepIndex++;
                steps.get(currentStepIndex).setStatus(PlanStep.StepStatus.IN_PROGRESS);
            } else {
                this.status = PlanStatus.COMPLETED;
            }
            this.updatedAt = Instant.now();
        }
    }

    public void markCurrentStepFailed(String reason) {
        PlanStep current = getCurrentStep();
        if (current != null) {
            current.setStatus(PlanStep.StepStatus.FAILED);
            this.updatedAt = Instant.now();
        }
    }

    public void markCurrentStepInProgress() {
        PlanStep current = getCurrentStep();
        if (current != null) {
            current.setStatus(PlanStep.StepStatus.IN_PROGRESS);
            this.status = PlanStatus.IN_PROGRESS;
            this.updatedAt = Instant.now();
        }
    }

    // --- Phase 9 DAG & Long-Horizon Plan Methods ---

    public Map<String, PlanNode> getPlanNodes() { return planNodes; }
    public void setPlanNodes(Map<String, PlanNode> planNodes) {
        this.planNodes = planNodes != null ? new LinkedHashMap<>(planNodes) : new LinkedHashMap<>();
        this.updatedAt = Instant.now();
    }

    public void addPlanNode(PlanNode node) {
        if (node != null && node.getNodeId() != null) {
            this.planNodes.put(node.getNodeId(), node);
            this.updatedAt = Instant.now();
        }
    }

    public PlanNode getPlanNode(String nodeId) {
        return nodeId != null ? this.planNodes.get(nodeId) : null;
    }

    public List<PlanDependency> getDependencies() { return dependencies; }
    public void setDependencies(List<PlanDependency> dependencies) {
        this.dependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
        this.updatedAt = Instant.now();
    }

    public void addPlanDependency(String fromNodeId, String toNodeId, DependencyType type) {
        this.dependencies.add(new PlanDependency(fromNodeId, toNodeId, type));
        PlanNode fromNode = getPlanNode(fromNodeId);
        if (fromNode != null && type == DependencyType.REQUIRES) {
            fromNode.addDependency(toNodeId);
        }
        this.updatedAt = Instant.now();
    }

    public List<PlanRevision> getRevisions() { return revisions; }
    public int getCurrentRevisionNumber() { return currentRevisionNumber; }

    /**
     * Finds nodes that are currently READY to be executed:
     * A node is READY if it is PENDING and all prerequisite dependency nodes are COMPLETED.
     */
    public synchronized List<PlanNode> getReadyPlanNodes() {
        List<PlanNode> ready = new ArrayList<>();
        for (PlanNode node : planNodes.values()) {
            if (node.getStatus() == PlanNode.PlanNodeStatus.READY) {
                ready.add(node);
                continue;
            }
            if (node.getStatus() != PlanNode.PlanNodeStatus.PENDING) {
                continue;
            }

            boolean allPrereqsDone = true;
            for (String depId : node.getDependencies()) {
                PlanNode depNode = planNodes.get(depId);
                if (depNode == null || depNode.getStatus() != PlanNode.PlanNodeStatus.COMPLETED) {
                    allPrereqsDone = false;
                    break;
                }
            }

            if (allPrereqsDone) {
                node.setStatus(PlanNode.PlanNodeStatus.READY);
                ready.add(node);
            }
        }
        return ready;
    }

    public synchronized void markNodeInProgress(String nodeId) {
        PlanNode node = getPlanNode(nodeId);
        if (node != null) {
            node.setStatus(PlanNode.PlanNodeStatus.IN_PROGRESS);
            node.setStartedAt(Instant.now());
            node.incrementAttempts();
            this.status = PlanStatus.IN_PROGRESS;
            this.updatedAt = Instant.now();
        }
    }

    public synchronized void markNodeCompleted(String nodeId, String resultSummary) {
        PlanNode node = getPlanNode(nodeId);
        if (node != null) {
            node.setStatus(PlanNode.PlanNodeStatus.COMPLETED);
            node.setResultSummary(resultSummary);
            node.setCompletedAt(Instant.now());
            this.updatedAt = Instant.now();

            // Refresh readiness for downstream nodes
            getReadyPlanNodes();

            // Check if all nodes are completed
            boolean allDone = planNodes.values().stream()
                    .allMatch(n -> n.getStatus() == PlanNode.PlanNodeStatus.COMPLETED || n.getStatus() == PlanNode.PlanNodeStatus.SKIPPED);
            if (allDone && !planNodes.isEmpty()) {
                this.status = PlanStatus.COMPLETED;
            }
        }
    }

    public synchronized void markNodeFailed(String nodeId, String reason) {
        PlanNode node = getPlanNode(nodeId);
        if (node != null) {
            node.setStatus(PlanNode.PlanNodeStatus.FAILED);
            node.setFailureReason(reason);
            this.updatedAt = Instant.now();
        }
    }

    public List<String> getCompletedNodeIds() {
        return planNodes.values().stream()
                .filter(PlanNode::isCompleted)
                .map(PlanNode::getNodeId)
                .collect(Collectors.toList());
    }

    /**
     * Creates a new plan revision, preserving all previously completed node history immutably.
     */
    public synchronized PlanRevision createRevision(String reason, List<String> added, List<String> modified, List<String> removed, String notes) {
        currentRevisionNumber++;
        List<String> completedSnapshot = getCompletedNodeIds();
        PlanRevision rev = new PlanRevision(currentRevisionNumber, reason, completedSnapshot, added, modified, removed, notes);
        this.revisions.add(rev);
        this.updatedAt = Instant.now();
        return rev;
    }

    public int getNodeCount() {
        return planNodes.size();
    }
}
