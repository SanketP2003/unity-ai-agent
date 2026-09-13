package com.unityagent.agent.verification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of executing a BehaviorTestScenario.
 */
public class BehaviorTestResult {

    private String scenarioId;
    private boolean passed;
    private String summary;
    private List<String> failedAssertions;
    private Map<String, Object> observedMetrics;
    private Instant timestamp;

    public BehaviorTestResult() {
        this.failedAssertions = new ArrayList<>();
        this.observedMetrics = new LinkedHashMap<>();
        this.timestamp = Instant.now();
    }

    public BehaviorTestResult(String scenarioId, boolean passed, String summary,
                              List<String> failedAssertions, Map<String, Object> observedMetrics) {
        this.scenarioId = scenarioId;
        this.passed = passed;
        this.summary = summary;
        this.failedAssertions = failedAssertions != null ? new ArrayList<>(failedAssertions) : new ArrayList<>();
        this.observedMetrics = observedMetrics != null ? new LinkedHashMap<>(observedMetrics) : new LinkedHashMap<>();
        this.timestamp = Instant.now();
    }

    public static BehaviorTestResult success(String scenarioId, String summary, Map<String, Object> metrics) {
        return new BehaviorTestResult(scenarioId, true, summary, List.of(), metrics);
    }

    public static BehaviorTestResult failure(String scenarioId, String summary, List<String> failures, Map<String, Object> metrics) {
        return new BehaviorTestResult(scenarioId, false, summary, failures, metrics);
    }

    public String getScenarioId() { return scenarioId; }
    public boolean isPassed() { return passed; }
    public String getSummary() { return summary; }
    public List<String> getFailedAssertions() { return failedAssertions; }
    public Map<String, Object> getObservedMetrics() { return observedMetrics; }
    public Instant getTimestamp() { return timestamp; }

    public VerificationEvidence toEvidence() {
        return new VerificationEvidence(
                VerificationType.BEHAVIOR_TEST,
                passed,
                summary,
                observedMetrics
        );
    }
}
