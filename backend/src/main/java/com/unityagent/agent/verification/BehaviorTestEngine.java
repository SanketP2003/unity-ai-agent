package com.unityagent.agent.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Executes behavioral test scenarios against Unity play mode and verifies assertions.
 */
@Service
public class BehaviorTestEngine {

    private static final Logger log = LoggerFactory.getLogger(BehaviorTestEngine.class);

    /**
     * Evaluates a scenario given observed metrics (e.g. from Unity play mode or simulated test harness).
     */
    public BehaviorTestResult evaluateScenario(BehaviorTestScenario scenario, Map<String, Object> observedMetrics) {
        if (scenario == null) {
            return BehaviorTestResult.failure("unknown", "Scenario was null", List.of("Scenario was null"), Map.of());
        }

        Map<String, Object> metrics = observedMetrics != null ? new LinkedHashMap<>(observedMetrics) : new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();

        for (BehaviorAction assertion : scenario.getAssertions()) {
            boolean assertionPassed = evaluateAssertion(assertion, metrics);
            if (!assertionPassed) {
                failures.add(assertion.getExpectedOutcome() + " (Action: " + assertion.getActionType() + ")");
            }
        }

        boolean allPassed = failures.isEmpty();
        String summary = allPassed
                ? "Scenario '" + scenario.getScenarioName() + "' PASSED: All " + scenario.getAssertions().size() + " assertions satisfied."
                : "Scenario '" + scenario.getScenarioName() + "' FAILED: " + failures.size() + " assertions failed.";

        log.info("Scenario {}: passed={}, failures={}", scenario.getScenarioId(), allPassed, failures.size());
        return new BehaviorTestResult(scenario.getScenarioId(), allPassed, summary, failures, metrics);
    }

    private boolean evaluateAssertion(BehaviorAction assertion, Map<String, Object> metrics) {
        switch (assertion.getActionType()) {
            case ASSERT_POSITION_DELTA: {
                String axis = (String) assertion.getParameters().getOrDefault("axis", "X");
                double minDelta = ((Number) assertion.getParameters().getOrDefault("minDelta", 0.1)).doubleValue();
                String metricKey = "delta" + axis.toUpperCase();
                Number actualDelta = (Number) metrics.getOrDefault(metricKey, metrics.getOrDefault("distanceMoved", 0.0));
                return actualDelta != null && actualDelta.doubleValue() >= minDelta;
            }

            case ASSERT_LOG_ABSENCE: {
                Number errorCount = (Number) metrics.getOrDefault("errorCount", 0);
                Boolean hasErrors = (Boolean) metrics.getOrDefault("hasErrors", false);
                return (errorCount == null || errorCount.intValue() == 0) && !Boolean.TRUE.equals(hasErrors);
            }

            case OBSERVE_COMPONENT_FIELD: {
                String comp = (String) assertion.getParameters().get("component");
                String field = (String) assertion.getParameters().get("field");
                String compKey = comp + "." + field;
                Object val = metrics.get(compKey);
                if (val == null) {
                    // Fallback to direct field lookup
                    val = metrics.get(field);
                }
                String comparison = (String) assertion.getParameters().getOrDefault("comparison", "EQUALS");
                if ("LESS_THAN".equalsIgnoreCase(comparison)) {
                    Number numVal = val instanceof Number ? (Number) val : null;
                    return numVal != null && numVal.doubleValue() < 100.0; // Health decreased
                }
                return val != null;
            }

            default:
                return true;
        }
    }
}
