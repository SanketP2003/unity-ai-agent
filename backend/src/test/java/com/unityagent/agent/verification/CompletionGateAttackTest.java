package com.unityagent.agent.verification;

import com.unityagent.agent.goal.AcceptanceCriterion;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalRequirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production Acceptance Test H — CompletionGate Attack.
 * Verifies that the CompletionGate is completely authoritative and impossible
 * for an LLM to bypass by simply claiming "GAME COMPLETE" or attempting premature completion.
 */
class CompletionGateAttackTest {

    private CompletionGate gate;

    @BeforeEach
    void setUp() {
        gate = new CompletionGate();
    }

    @Test
    void testLlmCannotBypassWhenRequirementsUnmet() {
        GameGoal goal = new GameGoal("goal_attack", "Build RPG Game");
        GoalRequirement r1 = new GoalRequirement("req_combat", "goal_attack", "Combat system must be active");
        r1.setRequired(true);
        r1.addCriterion(AcceptanceCriterion.exists("c1", "Player can attack", "attack_damage"));
        goal.addRequirement(r1);

        // Validation report shows requirement is NOT satisfied
        ValidationReport report = new ValidationReport("goal_attack");
        report.setGoalCompleted(false);
        report.setCompilationSuccess(true);
        report.setRuntimeErrorsClean(true);
        report.setBehaviorTestsPassed(true);
        report.setRequiredCount(1);
        report.setSatisfiedRequiredCount(0);
        report.setSummary("LLM claimed: 'Everything is built and game is completely finished!'");

        // CompletionGate MUST reject completion
        assertFalse(gate.canComplete(report));
        assertFalse(CompletionGate.evaluate(report));
    }

    @Test
    void testLlmCannotBypassWhenCompilationErrorsExist() {
        ValidationReport report = new ValidationReport("goal_compilation_fail");
        report.setGoalCompleted(true);
        report.setCompilationSuccess(false); // C# compilation failed!
        report.setRuntimeErrorsClean(true);
        report.setBehaviorTestsPassed(true);
        report.setRequiredCount(5);
        report.setSatisfiedRequiredCount(5);
        report.setSummary("CS1002: ; expected in PlayerController.cs");

        assertFalse(gate.canComplete(report));
    }

    @Test
    void testLlmCannotBypassWhenBehaviorTestsFail() {
        ValidationReport report = new ValidationReport("goal_behavior_fail");
        report.setGoalCompleted(true);
        report.setCompilationSuccess(true);
        report.setRuntimeErrorsClean(true);
        report.setBehaviorTestsPassed(false); // Behavior test failed in Play Mode!
        report.setRequiredCount(5);
        report.setSatisfiedRequiredCount(5);
        report.setSummary("Behavior test 'PlayerJumpTest' failed: position.y did not increase");

        assertFalse(gate.canComplete(report));
    }

    @Test
    void testLlmCannotBypassWhenRuntimeExceptionsExist() {
        ValidationReport report = new ValidationReport("goal_runtime_fail");
        report.setGoalCompleted(true);
        report.setCompilationSuccess(true);
        report.setRuntimeErrorsClean(false); // NullReferenceException at runtime!
        report.setBehaviorTestsPassed(true);
        report.setRequiredCount(5);
        report.setSatisfiedRequiredCount(5);
        report.setSummary("NullReferenceException in EnemyAI.Update()");

        assertFalse(gate.canComplete(report));
    }

    @Test
    void testCompletionSucceedsOnlyWhenAllFourGatesPass() {
        ValidationReport report = new ValidationReport("goal_success");
        report.setGoalCompleted(true);
        report.setCompilationSuccess(true);
        report.setRuntimeErrorsClean(true);
        report.setBehaviorTestsPassed(true);
        report.setRequiredCount(5);
        report.setSatisfiedRequiredCount(5);
        report.setSummary("All criteria verified with evidence");

        assertTrue(gate.canComplete(report));
        assertTrue(CompletionGate.evaluate(report));
    }
}
