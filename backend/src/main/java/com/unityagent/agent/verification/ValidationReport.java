package com.unityagent.agent.verification;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured report evaluating goal requirements, compilation status, runtime errors, and behavioral tests.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationReport {

    private String goalId;
    private int totalRequirements;
    private int requiredCount;
    private int satisfiedRequiredCount;
    private int satisfiedOptionalCount;
    private int failedCount;
    private boolean compilationSuccess;
    private boolean runtimeErrorsClean;
    private boolean behaviorTestsPassed;
    private boolean goalCompleted;
    private String summary;
    private List<VerificationEvidence> evidenceList;
    private Instant timestamp;

    public ValidationReport() {
        this.evidenceList = new ArrayList<>();
        this.timestamp = Instant.now();
    }

    public ValidationReport(String goalId) {
        this.goalId = goalId;
        this.evidenceList = new ArrayList<>();
        this.timestamp = Instant.now();
    }

    public String getGoalId() { return goalId; }
    public void setGoalId(String goalId) { this.goalId = goalId; }

    public int getTotalRequirements() { return totalRequirements; }
    public void setTotalRequirements(int totalRequirements) { this.totalRequirements = totalRequirements; }

    public int getRequiredCount() { return requiredCount; }
    public void setRequiredCount(int requiredCount) { this.requiredCount = requiredCount; }

    public int getSatisfiedRequiredCount() { return satisfiedRequiredCount; }
    public void setSatisfiedRequiredCount(int satisfiedRequiredCount) { this.satisfiedRequiredCount = satisfiedRequiredCount; }

    public int getSatisfiedOptionalCount() { return satisfiedOptionalCount; }
    public void setSatisfiedOptionalCount(int satisfiedOptionalCount) { this.satisfiedOptionalCount = satisfiedOptionalCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public boolean isCompilationSuccess() { return compilationSuccess; }
    public void setCompilationSuccess(boolean compilationSuccess) { this.compilationSuccess = compilationSuccess; }

    public boolean isRuntimeErrorsClean() { return runtimeErrorsClean; }
    public void setRuntimeErrorsClean(boolean runtimeErrorsClean) { this.runtimeErrorsClean = runtimeErrorsClean; }

    public boolean isBehaviorTestsPassed() { return behaviorTestsPassed; }
    public void setBehaviorTestsPassed(boolean behaviorTestsPassed) { this.behaviorTestsPassed = behaviorTestsPassed; }

    public boolean isGoalCompleted() { return goalCompleted; }
    public void setGoalCompleted(boolean goalCompleted) { this.goalCompleted = goalCompleted; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<VerificationEvidence> getEvidenceList() { return evidenceList; }
    public void setEvidenceList(List<VerificationEvidence> evidenceList) {
        this.evidenceList = evidenceList != null ? evidenceList : new ArrayList<>();
    }
    public void addEvidence(VerificationEvidence evidence) {
        if (evidence != null) this.evidenceList.add(evidence);
    }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "ValidationReport{" +
                "goalId='" + goalId + '\'' +
                ", satisfied=" + satisfiedRequiredCount + "/" + requiredCount +
                ", compile=" + compilationSuccess +
                ", runtimeClean=" + runtimeErrorsClean +
                ", behaviorPassed=" + behaviorTestsPassed +
                ", GOAL_COMPLETED=" + goalCompleted +
                '}';
    }
}
