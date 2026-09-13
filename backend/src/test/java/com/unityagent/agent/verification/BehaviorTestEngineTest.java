package com.unityagent.agent.verification;

import com.unityagent.agent.goal.GoalRequirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BehaviorTestEngineTest {

    private BehaviorTestGenerator generator;
    private BehaviorTestEngine engine;

    @BeforeEach
    void setUp() {
        generator = new BehaviorTestGenerator();
        engine = new BehaviorTestEngine();
    }

    @Test
    @DisplayName("Synthesizes movement test scenario and passes when metrics meet threshold")
    void testMovementScenarioPasses() {
        GoalRequirement req = new GoalRequirement(
                "req_move_01",
                "goal_01",
                "Implement player horizontal movement using arrow keys or WASD"
        );

        List<BehaviorTestScenario> scenarios = generator.generateScenarios(req);
        assertFalse(scenarios.isEmpty());

        BehaviorTestScenario scenario = scenarios.get(0);
        assertEquals("Player Movement Verification", scenario.getScenarioName());

        Map<String, Object> passingMetrics = Map.of(
                "deltaX", 1.5,
                "errorCount", 0,
                "hasErrors", false
        );

        BehaviorTestResult result = engine.evaluateScenario(scenario, passingMetrics);
        assertTrue(result.isPassed());
        assertTrue(result.getFailedAssertions().isEmpty());
        assertTrue(result.getSummary().contains("PASSED"));

        VerificationEvidence evidence = result.toEvidence();
        assertNotNull(evidence);
        assertTrue(evidence.isVerified());
        assertEquals(VerificationType.BEHAVIOR_TEST, evidence.getType());
    }

    @Test
    @DisplayName("Fails scenario when object does not move or errors are present")
    void testMovementScenarioFailsOnZeroDeltaOrError() {
        GoalRequirement req = new GoalRequirement(
                "req_move_01",
                "goal_01",
                "Player movement controller"
        );

        List<BehaviorTestScenario> scenarios = generator.generateScenarios(req);
        BehaviorTestScenario scenario = scenarios.get(0);

        // Object did not move (deltaX = 0) and logged 1 error
        Map<String, Object> failingMetrics = Map.of(
                "deltaX", 0.0,
                "errorCount", 1,
                "hasErrors", true
        );

        BehaviorTestResult result = engine.evaluateScenario(scenario, failingMetrics);
        assertFalse(result.isPassed());
        assertEquals(2, result.getFailedAssertions().size());
        assertTrue(result.getSummary().contains("FAILED"));

        VerificationEvidence evidence = result.toEvidence();
        assertFalse(evidence.isVerified());
    }
}
