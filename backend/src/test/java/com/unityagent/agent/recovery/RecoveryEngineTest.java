package com.unityagent.agent.recovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryEngineTest {

    private RecoveryEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RecoveryEngine(4); // Max 4 recovery cycles for test
    }

    @Test
    @DisplayName("Syntax error on attempt 1 escalates to inspect and repair")
    void testSyntaxErrorEscalatesToInspectAndRepair() {
        FailureContext ctx = new FailureContext("node_compile", "compile_project", "CS1002: ; expected", FailureType.COMPILATION_SYNTAX, "CS1002");
        ctx.setTargetFileOrAsset("PlayerController.cs");
        ctx.setAttemptCount(1);

        RecoveryStrategy strategy = engine.determineStrategy(ctx);
        assertNotNull(strategy);
        assertEquals(RecoveryStrategy.StrategyType.INSPECT_AND_REPAIR, strategy.getType());
        assertEquals("update_script", strategy.getToolToInvoke());
        assertEquals("PlayerController.cs", strategy.getSuggestedParameters().get("scriptName"));
        assertFalse(strategy.requiresHuman());
    }

    @Test
    @DisplayName("Missing type error on attempt 1 injects create_script dependency")
    void testMissingTypeInjectsCreateScript() {
        FailureContext ctx = new FailureContext("node_compile", "compile_project", "CS0246: EnemyAI not found", FailureType.COMPILATION_TYPE, "CS0246");
        ctx.setDiagnosticDetails(Map.of("missingSymbol", "EnemyAI"));
        ctx.setAttemptCount(1);

        RecoveryStrategy strategy = engine.determineStrategy(ctx);
        assertNotNull(strategy);
        assertEquals(RecoveryStrategy.StrategyType.INJECT_MISSING_DEPENDENCY, strategy.getType());
        assertEquals("create_script", strategy.getToolToInvoke());
        assertEquals("EnemyAI.cs", strategy.getSuggestedParameters().get("scriptName"));
    }

    @Test
    @DisplayName("Repeated failure escalates to REPLAN_SUBGRAPH")
    void testRepeatedFailuresEscalateToReplan() {
        FailureContext ctx = new FailureContext("node_compile", "compile_project", "CS1002: ; expected", FailureType.COMPILATION_SYNTAX, "CS1002");
        ctx.setTargetFileOrAsset("PlayerController.cs");
        ctx.setAttemptCount(3); // 3rd attempt exceeds repair limit

        RecoveryStrategy strategy = engine.determineStrategy(ctx);
        assertNotNull(strategy);
        assertEquals(RecoveryStrategy.StrategyType.REPLAN_SUBGRAPH, strategy.getType());
    }

    @Test
    @DisplayName("Exceeding max recovery cycles escalates to ESCALATE_TO_HUMAN")
    void testMaxRecoveryCyclesExceededEscalatesToHuman() {
        FailureContext ctx = new FailureContext("node_compile", "compile_project", "CS1002", FailureType.COMPILATION_SYNTAX, "CS1002");

        // Engine max is 4
        engine.determineStrategy(ctx); // cycle 1
        engine.determineStrategy(ctx); // cycle 2
        engine.determineStrategy(ctx); // cycle 3
        engine.determineStrategy(ctx); // cycle 4
        assertEquals(4, engine.getCurrentCycles());

        // Cycle 5 exceeds max cycles
        RecoveryStrategy strategy = engine.determineStrategy(ctx);
        assertNotNull(strategy);
        assertEquals(RecoveryStrategy.StrategyType.ESCALATE_TO_HUMAN, strategy.getType());
        assertTrue(strategy.requiresHuman());
        assertTrue(strategy.getActionDescription().contains("Autonomy limit exceeded"));
    }
}
