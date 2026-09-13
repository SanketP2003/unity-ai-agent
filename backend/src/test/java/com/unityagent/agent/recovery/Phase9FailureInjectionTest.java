package com.unityagent.agent.recovery;

import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.AutonomyLimits;
import com.unityagent.agent.AgentLoop;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.goal.GoalAnalyzer;
import com.unityagent.agent.goal.RequirementManager;
import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.model.ErrorType;
import com.unityagent.agent.plan.LongHorizonPlanner;
import com.unityagent.agent.plan.PlanNode;
import com.unityagent.agent.plan.ReplanningEngine;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Validates system resilience under injected failure scenarios:
 * CS1002 (syntax), CS0246 (missing type), MissingComponentException,
 * behavioral test timeouts, and scene drift.
 */
class Phase9FailureInjectionTest {

    private FailureClassifier classifier;
    private RecoveryEngine recoveryEngine;
    private ReplanningEngine replanningEngine;

    @BeforeEach
    void setUp() {
        classifier = new FailureClassifier();
        recoveryEngine = new RecoveryEngine(10);
        replanningEngine = new ReplanningEngine(10);
    }

    @Test
    @DisplayName("Failure Injection: CS1002 syntax error triggers surgical script repair")
    void testInjectedSyntaxErrorRecovery() {
        String errorMsg = "Assets/Scripts/PlayerController.cs(18,22): error CS1002: ; expected";
        FailureContext failure = classifier.classify("node_script", "compile_project", errorMsg);

        assertEquals(FailureType.COMPILATION_SYNTAX, failure.getFailureType());
        assertEquals("CS1002", failure.getErrorCode());
        assertEquals("PlayerController.cs", failure.getTargetFileOrAsset());

        RecoveryStrategy strategy = recoveryEngine.determineStrategy(failure);
        assertEquals(RecoveryStrategy.StrategyType.INSPECT_AND_REPAIR, strategy.getType());
        assertEquals("update_script", strategy.getToolToInvoke());
    }

    @Test
    @DisplayName("Failure Injection: CS0246 missing type triggers create_script dependency injection")
    void testInjectedMissingTypeRecovery() {
        String errorMsg = "Assets/Scripts/Combat.cs(12,5): error CS0246: The type or namespace name 'HealthManager' could not be found";
        FailureContext failure = classifier.classify("node_script", "compile_project", errorMsg);

        assertEquals(FailureType.COMPILATION_TYPE, failure.getFailureType());
        assertEquals("CS0246", failure.getErrorCode());
        assertEquals("HealthManager", failure.getDiagnosticDetails().get("missingSymbol"));

        RecoveryStrategy strategy = recoveryEngine.determineStrategy(failure);
        assertEquals(RecoveryStrategy.StrategyType.INJECT_MISSING_DEPENDENCY, strategy.getType());
        assertEquals("create_script", strategy.getToolToInvoke());
        assertEquals("HealthManager.cs", strategy.getSuggestedParameters().get("scriptName"));
    }

    @Test
    @DisplayName("Failure Injection: MissingComponentException triggers add_component retry")
    void testInjectedMissingComponentRecovery() {
        String errorMsg = "MissingComponentException: GameObject 'Player' has no 'Rigidbody' component.";
        FailureContext failure = classifier.classify("node_phys", "set_component_property", errorMsg);

        assertEquals(FailureType.MISSING_COMPONENT, failure.getFailureType());

        RecoveryStrategy strategy = recoveryEngine.determineStrategy(failure);
        assertEquals(RecoveryStrategy.StrategyType.RETRY_WITH_CORRECTION, strategy.getType());
        assertEquals("add_component", strategy.getToolToInvoke());
    }

    @Test
    @DisplayName("Failure Injection: Behavioral test zero distance moved triggers physics recovery and replan")
    void testInjectedBehaviorTimeoutRecovery() {
        String errorMsg = "Game test failed: distanceMoved = 0 after 3.0s of simulated movement input.";
        FailureContext failure = classifier.classify("node_test", "run_game_test", errorMsg);

        assertEquals(FailureType.BEHAVIOR_TIMEOUT, failure.getFailureType());

        // Attempt 1 -> Inspect physics properties
        failure.setAttemptCount(1);
        RecoveryStrategy strategy1 = recoveryEngine.determineStrategy(failure);
        assertEquals(RecoveryStrategy.StrategyType.RETRY_WITH_CORRECTION, strategy1.getType());

        // Attempt 2 -> Escalates to replanning subgraph
        failure.setAttemptCount(2);
        RecoveryStrategy strategy2 = recoveryEngine.determineStrategy(failure);
        assertEquals(RecoveryStrategy.StrategyType.REPLAN_SUBGRAPH, strategy2.getType());
    }

    @Test
    @DisplayName("Failure Injection: Scene drift with missing GameObject triggers hierarchy inspection")
    void testInjectedSceneDriftRecovery() {
        String errorMsg = "GameObject 'Boss_Orc' not found in active scene hierarchy.";
        FailureContext failure = classifier.classify("node_boss", "set_transform", errorMsg);

        assertEquals(FailureType.SCENE_DRIFT, failure.getFailureType());

        RecoveryStrategy strategy = recoveryEngine.determineStrategy(failure);
        assertEquals(RecoveryStrategy.StrategyType.INSPECT_AND_REPAIR, strategy.getType());
        assertEquals("find_game_objects", strategy.getToolToInvoke());
    }
}
