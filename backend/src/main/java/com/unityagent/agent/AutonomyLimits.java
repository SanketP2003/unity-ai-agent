package com.unityagent.agent;

import com.unityagent.agent.plan.PlanExecutionState;
import org.springframework.stereotype.Component;

/**
 * Global guardrail limits bounding autonomous long-horizon runs.
 */
@Component
public class AutonomyLimits {

    private final int maxNodes;
    private final int maxToolCalls;
    private final int maxReplans;
    private final int maxRecoveryCycles;
    private final long maxRunTimeSeconds;

    public AutonomyLimits() {
        this(100, 250, 10, 10, 1800);
    }

    public AutonomyLimits(int maxNodes, int maxToolCalls, int maxReplans,
                          int maxRecoveryCycles, long maxRunTimeSeconds) {
        this.maxNodes = maxNodes;
        this.maxToolCalls = maxToolCalls;
        this.maxReplans = maxReplans;
        this.maxRecoveryCycles = maxRecoveryCycles;
        this.maxRunTimeSeconds = maxRunTimeSeconds;
    }

    public static AutonomyLimits defaultLimits() {
        return new AutonomyLimits();
    }

    public int getMaxNodes() { return maxNodes; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public int getMaxReplans() { return maxReplans; }
    public int getMaxRecoveryCycles() { return maxRecoveryCycles; }
    public long getMaxRunTimeSeconds() { return maxRunTimeSeconds; }

    /**
     * Checks if any autonomy limits have been exceeded.
     *
     * @return null if within limits, or a diagnostic reason string if exceeded
     */
    public String checkLimits(PlanExecutionState state, int currentReplans, int currentRecoveryCycles) {
        if (state == null) return null;

        if (state.getTotalNodes() > maxNodes) {
            return String.format("Node count limit exceeded: %d > %d", state.getTotalNodes(), maxNodes);
        }
        if (state.getTotalToolCalls() > maxToolCalls) {
            return String.format("Tool call budget exceeded: %d > %d", state.getTotalToolCalls(), maxToolCalls);
        }
        if (currentReplans > maxReplans) {
            return String.format("Max replans exceeded: %d > %d", currentReplans, maxReplans);
        }
        if (currentRecoveryCycles > maxRecoveryCycles) {
            return String.format("Max recovery cycles exceeded: %d > %d", currentRecoveryCycles, maxRecoveryCycles);
        }
        if (state.getElapsedSeconds() > maxRunTimeSeconds) {
            return String.format("Run timeout exceeded: %ds > %ds", state.getElapsedSeconds(), maxRunTimeSeconds);
        }

        return null;
    }
}
