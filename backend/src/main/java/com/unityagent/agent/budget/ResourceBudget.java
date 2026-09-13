package com.unityagent.agent.budget;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Immutable production guardrail budgets bounding resource usage.
 * The LLM has zero capability to alter or bypass these limits.
 */
@Component
public class ResourceBudget {

    private final long maxRunDurationSeconds;
    private final int maxToolCalls;
    private final int maxLlmTurns;
    private final int maxReplans;
    private final int maxRecoveryCycles;
    private final int maxConcurrentRuns;
    private final int maxContextChars;

    public ResourceBudget() {
        this(1800, 500, 150, 15, 15, 4, 16000);
    }

    public ResourceBudget(
            @Value("${agent.production.max-run-duration-seconds:1800}") long maxRunDurationSeconds,
            @Value("${agent.production.max-tool-calls:500}") int maxToolCalls,
            @Value("${agent.production.max-llm-turns:150}") int maxLlmTurns,
            @Value("${agent.production.max-replans:15}") int maxReplans,
            @Value("${agent.production.max-recovery-cycles:15}") int maxRecoveryCycles,
            @Value("${agent.production.max-concurrent-runs:4}") int maxConcurrentRuns,
            @Value("${memory.max-context-chars:16000}") int maxContextChars) {
        this.maxRunDurationSeconds = maxRunDurationSeconds > 0 ? maxRunDurationSeconds : 1800;
        this.maxToolCalls = maxToolCalls > 0 ? maxToolCalls : 500;
        this.maxLlmTurns = maxLlmTurns > 0 ? maxLlmTurns : 150;
        this.maxReplans = maxReplans > 0 ? maxReplans : 15;
        this.maxRecoveryCycles = maxRecoveryCycles > 0 ? maxRecoveryCycles : 15;
        this.maxConcurrentRuns = maxConcurrentRuns > 0 ? maxConcurrentRuns : 4;
        this.maxContextChars = maxContextChars > 0 ? maxContextChars : 16000;
    }

    public static ResourceBudget defaultBudget() {
        return new ResourceBudget();
    }

    public long getMaxRunDurationSeconds() {
        return maxRunDurationSeconds;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public int getMaxLlmTurns() {
        return maxLlmTurns;
    }

    public int getMaxReplans() {
        return maxReplans;
    }

    public int getMaxRecoveryCycles() {
        return maxRecoveryCycles;
    }

    public int getMaxConcurrentRuns() {
        return maxConcurrentRuns;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    /**
     * Asserts that current usage is within budget, otherwise throws {@link BudgetExceededException}.
     */
    public void validateBudget(String runId, long elapsedSeconds, int toolCalls, int llmTurns, int replans, int recoveryCycles) {
        if (elapsedSeconds > maxRunDurationSeconds) {
            throw new BudgetExceededException(runId, BudgetExceededException.ExhaustionReason.TIMEOUT,
                    String.format("Run timeout exceeded: %d seconds elapsed (max %d seconds)", elapsedSeconds, maxRunDurationSeconds));
        }
        if (toolCalls >= maxToolCalls) {
            throw new BudgetExceededException(runId, BudgetExceededException.ExhaustionReason.TOOL_LIMIT_REACHED,
                    String.format("Tool budget exceeded: %d calls executed (max %d calls)", toolCalls, maxToolCalls));
        }
        if (llmTurns >= maxLlmTurns) {
            throw new BudgetExceededException(runId, BudgetExceededException.ExhaustionReason.LLM_TURN_LIMIT_REACHED,
                    String.format("LLM turn limit reached: %d turns executed (max %d turns)", llmTurns, maxLlmTurns));
        }
        if (replans >= maxReplans) {
            throw new BudgetExceededException(runId, BudgetExceededException.ExhaustionReason.REPLAN_LIMIT_REACHED,
                    String.format("Replan limit reached: %d replans executed (max %d replans)", replans, maxReplans));
        }
        if (recoveryCycles >= maxRecoveryCycles) {
            throw new BudgetExceededException(runId, BudgetExceededException.ExhaustionReason.RECOVERY_LIMIT_REACHED,
                    String.format("Recovery cycle limit reached: %d cycles executed (max %d cycles)", recoveryCycles, maxRecoveryCycles));
        }
    }

    /**
     * Checks if concurrent runs exceed allowable budget.
     */
    public void validateConcurrentRuns(int currentActiveRuns) {
        if (currentActiveRuns >= maxConcurrentRuns) {
            throw new BudgetExceededException("system", BudgetExceededException.ExhaustionReason.CONCURRENT_RUN_LIMIT_REACHED,
                    String.format("Concurrent run limit reached: %d active runs (max %d allowed)", currentActiveRuns, maxConcurrentRuns));
        }
    }
}
