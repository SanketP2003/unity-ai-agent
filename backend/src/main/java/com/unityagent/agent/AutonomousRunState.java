package com.unityagent.agent;

import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.plan.PlanExecutionState;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.agent.verification.VerificationEvidence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the live runtime state of an autonomous long-horizon agent run.
 */
public class AutonomousRunState {

    public enum RunStatus {
        INITIALIZING,
        RUNNING,
        PAUSED,
        AWAITING_INTERVENTION,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private String runId;
    private String sessionId;
    private String projectId;
    private RunStatus status;
    private GameGoal goal;
    private AgentPlan plan;
    private PlanExecutionState executionState;
    private ValidationReport validationReport;
    private List<VerificationEvidence> accumulatedEvidence;
    private HumanInterventionBoundary currentIntervention;
    private String lastCheckpointId;
    private Instant startedAt;
    private Instant completedAt;

    public AutonomousRunState() {
        this.status = RunStatus.INITIALIZING;
        this.accumulatedEvidence = new ArrayList<>();
        this.startedAt = Instant.now();
    }

    public AutonomousRunState(String runId, String sessionId, String projectId, GameGoal goal, AgentPlan plan) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.goal = goal;
        this.plan = plan;
        this.status = RunStatus.INITIALIZING;
        this.executionState = new PlanExecutionState(runId, plan != null ? plan.getPlanId() : null, plan != null ? plan.getNodeCount() : 0);
        this.accumulatedEvidence = new ArrayList<>();
        this.startedAt = Instant.now();
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }

    public GameGoal getGoal() { return goal; }
    public void setGoal(GameGoal goal) { this.goal = goal; }

    public AgentPlan getPlan() { return plan; }
    public void setPlan(AgentPlan plan) { this.plan = plan; }

    public PlanExecutionState getExecutionState() { return executionState; }
    public void setExecutionState(PlanExecutionState executionState) { this.executionState = executionState; }

    public ValidationReport getValidationReport() { return validationReport; }
    public void setValidationReport(ValidationReport validationReport) { this.validationReport = validationReport; }

    public List<VerificationEvidence> getAccumulatedEvidence() { return accumulatedEvidence; }
    public void setAccumulatedEvidence(List<VerificationEvidence> accumulatedEvidence) {
        this.accumulatedEvidence = accumulatedEvidence != null ? new ArrayList<>(accumulatedEvidence) : new ArrayList<>();
    }
    public void addEvidence(VerificationEvidence evidence) {
        if (evidence != null) {
            this.accumulatedEvidence.add(evidence);
        }
    }

    public HumanInterventionBoundary getCurrentIntervention() { return currentIntervention; }
    public void setCurrentIntervention(HumanInterventionBoundary currentIntervention) {
        this.currentIntervention = currentIntervention;
    }

    public String getLastCheckpointId() { return lastCheckpointId; }
    public void setLastCheckpointId(String lastCheckpointId) { this.lastCheckpointId = lastCheckpointId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
