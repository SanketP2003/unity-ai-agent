package com.unityagent.agent.recovery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recommended recovery action proposed by the RecoveryEngine to resolve a failure.
 */
public class RecoveryStrategy {

    public enum StrategyType {
        RETRY_WITH_CORRECTION,     // Re-run with fixed arguments (e.g. valid enum, adjusted vector)
        INSPECT_AND_REPAIR,        // Read source file or scene state, apply surgical patch, recompile/retest
        INJECT_MISSING_DEPENDENCY, // Create missing script or asset, wire prerequisite
        SWITCH_ALTERNATIVE_APPROACH,// Use fallback tool or alternative architectural component
        REPLAN_SUBGRAPH,           // Trigger replanning engine to rebuild local sub-DAG
        ESCALATE_TO_HUMAN          // Limits exceeded or non-automatable condition
    }

    private final StrategyType type;
    private final String actionDescription;
    private final String toolToInvoke;
    private final Map<String, Object> suggestedParameters;
    private final int maxAttempts;

    public RecoveryStrategy(StrategyType type, String actionDescription,
                            String toolToInvoke, Map<String, Object> suggestedParameters,
                            int maxAttempts) {
        this.type = type;
        this.actionDescription = actionDescription;
        this.toolToInvoke = toolToInvoke;
        this.suggestedParameters = suggestedParameters != null ? new LinkedHashMap<>(suggestedParameters) : new LinkedHashMap<>();
        this.maxAttempts = maxAttempts;
    }

    public static RecoveryStrategy inspectAndRepair(String desc, String tool, Map<String, Object> params) {
        return new RecoveryStrategy(StrategyType.INSPECT_AND_REPAIR, desc, tool, params, 2);
    }

    public static RecoveryStrategy injectMissing(String desc, String tool, Map<String, Object> params) {
        return new RecoveryStrategy(StrategyType.INJECT_MISSING_DEPENDENCY, desc, tool, params, 2);
    }

    public static RecoveryStrategy retry(String desc, String tool, Map<String, Object> params) {
        return new RecoveryStrategy(StrategyType.RETRY_WITH_CORRECTION, desc, tool, params, 2);
    }

    public static RecoveryStrategy replanSubgraph(String desc) {
        return new RecoveryStrategy(StrategyType.REPLAN_SUBGRAPH, desc, null, null, 1);
    }

    public static RecoveryStrategy escalateToHuman(String reason) {
        return new RecoveryStrategy(StrategyType.ESCALATE_TO_HUMAN, reason, null, null, 0);
    }

    public StrategyType getType() { return type; }
    public String getActionDescription() { return actionDescription; }
    public String getToolToInvoke() { return toolToInvoke; }
    public Map<String, Object> getSuggestedParameters() { return suggestedParameters; }
    public int getMaxAttempts() { return maxAttempts; }

    public boolean requiresHuman() {
        return type == StrategyType.ESCALATE_TO_HUMAN;
    }

    @Override
    public String toString() {
        return "RecoveryStrategy{" +
                "type=" + type +
                ", action='" + actionDescription + '\'' +
                ", tool='" + toolToInvoke + '\'' +
                '}';
    }
}
