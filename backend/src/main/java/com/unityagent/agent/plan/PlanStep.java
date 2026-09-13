package com.unityagent.agent.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * An individual step in an AgentPlan.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanStep {

    public enum StepStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    private String stepId;
    private String description;
    private StepStatus status;
    private List<String> requiredTools;
    private String verificationCriteria;

    public PlanStep() {
        this.status = StepStatus.PENDING;
        this.requiredTools = new ArrayList<>();
    }

    public PlanStep(String stepId, String description, List<String> requiredTools, String verificationCriteria) {
        this.stepId = stepId;
        this.description = description;
        this.status = StepStatus.PENDING;
        this.requiredTools = requiredTools != null ? new ArrayList<>(requiredTools) : new ArrayList<>();
        this.verificationCriteria = verificationCriteria;
    }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }

    public List<String> getRequiredTools() { return requiredTools; }
    public void setRequiredTools(List<String> requiredTools) {
        this.requiredTools = requiredTools != null ? new ArrayList<>(requiredTools) : new ArrayList<>();
    }

    public String getVerificationCriteria() { return verificationCriteria; }
    public void setVerificationCriteria(String verificationCriteria) { this.verificationCriteria = verificationCriteria; }
}
