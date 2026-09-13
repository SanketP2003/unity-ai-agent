package com.unityagent.agent.verification;

import java.util.ArrayList;
import java.util.List;

/**
 * Full scenario specification for an autonomous behavioral verification test.
 */
public class BehaviorTestScenario {

    private String scenarioId;
    private String scenarioName;
    private String description;
    private List<BehaviorAction> setupActions;
    private List<BehaviorAction> testActions;
    private List<BehaviorAction> assertions;
    private int timeoutSeconds;

    public BehaviorTestScenario() {
        this.setupActions = new ArrayList<>();
        this.testActions = new ArrayList<>();
        this.assertions = new ArrayList<>();
        this.timeoutSeconds = 15;
    }

    public BehaviorTestScenario(String scenarioId, String scenarioName, String description) {
        this.scenarioId = scenarioId;
        this.scenarioName = scenarioName;
        this.description = description;
        this.setupActions = new ArrayList<>();
        this.testActions = new ArrayList<>();
        this.assertions = new ArrayList<>();
        this.timeoutSeconds = 15;
    }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public String getScenarioName() { return scenarioName; }
    public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<BehaviorAction> getSetupActions() { return setupActions; }
    public void setSetupActions(List<BehaviorAction> setupActions) {
        this.setupActions = setupActions != null ? new ArrayList<>(setupActions) : new ArrayList<>();
    }
    public void addSetupAction(BehaviorAction action) { this.setupActions.add(action); }

    public List<BehaviorAction> getTestActions() { return testActions; }
    public void setTestActions(List<BehaviorAction> testActions) {
        this.testActions = testActions != null ? new ArrayList<>(testActions) : new ArrayList<>();
    }
    public void addTestAction(BehaviorAction action) { this.testActions.add(action); }

    public List<BehaviorAction> getAssertions() { return assertions; }
    public void setAssertions(List<BehaviorAction> assertions) {
        this.assertions = assertions != null ? new ArrayList<>(assertions) : new ArrayList<>();
    }
    public void addAssertion(BehaviorAction action) { this.assertions.add(action); }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
