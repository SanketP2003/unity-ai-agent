package com.unityagent.agent.verification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single actionable step in an automated behavioral test scenario.
 */
public class BehaviorAction {

    private BehaviorActionType actionType;
    private String targetObjectName;
    private Map<String, Object> parameters;
    private int timeoutSeconds;
    private String expectedOutcome;

    public BehaviorAction() {
        this.parameters = new LinkedHashMap<>();
        this.timeoutSeconds = 5;
    }

    public BehaviorAction(BehaviorActionType actionType, String targetObjectName,
                          Map<String, Object> parameters, String expectedOutcome) {
        this.actionType = actionType;
        this.targetObjectName = targetObjectName;
        this.parameters = parameters != null ? new LinkedHashMap<>(parameters) : new LinkedHashMap<>();
        this.timeoutSeconds = 5;
        this.expectedOutcome = expectedOutcome;
    }

    public BehaviorActionType getActionType() { return actionType; }
    public void setActionType(BehaviorActionType actionType) { this.actionType = actionType; }

    public String getTargetObjectName() { return targetObjectName; }
    public void setTargetObjectName(String targetObjectName) { this.targetObjectName = targetObjectName; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters != null ? new LinkedHashMap<>(parameters) : new LinkedHashMap<>();
    }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public String getExpectedOutcome() { return expectedOutcome; }
    public void setExpectedOutcome(String expectedOutcome) { this.expectedOutcome = expectedOutcome; }

    public static BehaviorAction simulateInput(String target, String key, double duration) {
        return new BehaviorAction(
                BehaviorActionType.SIMULATE_INPUT,
                target,
                Map.of("key", key, "duration", duration),
                "Simulate pressing " + key + " for " + duration + "s"
        );
    }

    public static BehaviorAction assertPositionDelta(String target, String axis, double minDelta) {
        return new BehaviorAction(
                BehaviorActionType.ASSERT_POSITION_DELTA,
                target,
                Map.of("axis", axis, "minDelta", minDelta),
                "Assert " + target + " moves along " + axis + " by >= " + minDelta
        );
    }

    public static BehaviorAction assertNoErrors() {
        return new BehaviorAction(
                BehaviorActionType.ASSERT_LOG_ABSENCE,
                "Console",
                Map.of("severity", "Error"),
                "Assert zero runtime exceptions or errors in console"
        );
    }

    @Override
    public String toString() {
        return "BehaviorAction{" +
                "type=" + actionType +
                ", target='" + targetObjectName + '\'' +
                ", params=" + parameters +
                '}';
    }
}
