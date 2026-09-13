package com.unityagent.agent.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mutable, dynamic plan for an autonomous agent run.
 * Not a rigid workflow engine: steps can be dynamically inserted, skipped, or updated
 * as project reality dictates (e.g. inserting diagnosis and repair steps when compilation fails).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentPlan {

    public enum PlanStatus {
        DRAFT,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private String planId;
    private String goalId;
    private List<PlanStep> steps;
    private int currentStepIndex;
    private PlanStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public AgentPlan() {
        this.steps = new ArrayList<>();
        this.currentStepIndex = 0;
        this.status = PlanStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public AgentPlan(String planId, String goalId) {
        this.planId = planId != null ? planId : "plan_" + UUID.randomUUID().toString().substring(0, 8);
        this.goalId = goalId;
        this.steps = new ArrayList<>();
        this.currentStepIndex = 0;
        this.status = PlanStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

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
}
