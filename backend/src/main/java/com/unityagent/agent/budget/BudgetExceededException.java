package com.unityagent.agent.budget;

/**
 * Thrown when an autonomous run exceeds its non-negotiable resource budget.
 * Explicit exhaustion reasons prevent infinite loops and runaway costs.
 */
public class BudgetExceededException extends RuntimeException {

    public enum ExhaustionReason {
        BUDGET_EXHAUSTED,
        TIMEOUT,
        TOOL_LIMIT_REACHED,
        LLM_TURN_LIMIT_REACHED,
        REPLAN_LIMIT_REACHED,
        RECOVERY_LIMIT_REACHED,
        CONCURRENT_RUN_LIMIT_REACHED
    }

    private final ExhaustionReason reason;
    private final String runId;

    public BudgetExceededException(String runId, ExhaustionReason reason, String message) {
        super(message);
        this.runId = runId;
        this.reason = reason;
    }

    public ExhaustionReason getReason() {
        return reason;
    }

    public String getRunId() {
        return runId;
    }
}
