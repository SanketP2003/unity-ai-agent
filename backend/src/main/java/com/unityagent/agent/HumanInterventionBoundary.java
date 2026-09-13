package com.unityagent.agent;

import java.time.Instant;

/**
 * Encapsulates an active or historical human intervention boundary where the autonomous
 * run yielded control to the human developer.
 */
public class HumanInterventionBoundary {

    public enum InterventionReason {
        LIMIT_EXCEEDED,
        UNRESOLVABLE_FAILURE,
        HIGH_RISK_TOOL_APPROVAL,
        MANUAL_PAUSE
    }

    private final String interventionId;
    private final String runId;
    private final InterventionReason reason;
    private final String message;
    private final String requiredAction;
    private final Instant timestamp;

    private boolean resolved;
    private boolean approved;
    private String userResponse;

    public HumanInterventionBoundary(String interventionId, String runId,
                                     InterventionReason reason, String message, String requiredAction) {
        this.interventionId = interventionId;
        this.runId = runId;
        this.reason = reason;
        this.message = message;
        this.requiredAction = requiredAction;
        this.timestamp = Instant.now();
        this.resolved = false;
        this.approved = false;
    }

    public String getInterventionId() { return interventionId; }
    public String getRunId() { return runId; }
    public InterventionReason getReason() { return reason; }
    public String getMessage() { return message; }
    public String getRequiredAction() { return requiredAction; }
    public Instant getTimestamp() { return timestamp; }

    public boolean isResolved() { return resolved; }
    public boolean isApproved() { return approved; }
    public String getUserResponse() { return userResponse; }

    public void resolve(boolean approved, String userResponse) {
        this.resolved = true;
        this.approved = approved;
        this.userResponse = userResponse;
    }

    @Override
    public String toString() {
        return "HumanIntervention{" +
                "id='" + interventionId + '\'' +
                ", reason=" + reason +
                ", resolved=" + resolved +
                ", approved=" + approved +
                '}';
    }
}
