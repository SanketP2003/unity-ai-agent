package com.unityagent.agent.verification;

import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalRequirement;
import com.unityagent.agent.goal.RequirementManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("9.2 & 9.20 ObjectiveValidator & CompletionGate Tests")
class CompletionGateTest {

    private final ObjectiveValidator validator = new ObjectiveValidator();

    @Test
    @DisplayName("CompletionGate rejects when required requirements are not satisfied")
    void testGateRejectsUnsatisfiedRequirements() {
        GameGoal goal = new GameGoal("goal_1", "sess_1", "Platformer");
        GoalRequirement r1 = new GoalRequirement("REQ-001", "Player exists", GoalRequirement.RequirementType.GAME_OBJECT_EXISTS, "Player", true, null);
        goal.addRequirement(r1);

        RequirementManager mgr = new RequirementManager(goal);

        // Compile success = true, runtime clean = true, behavior passed = true, but req is PENDING
        ValidationReport report = validator.validate(goal, mgr, true, true, true);
        assertFalse(report.isGoalCompleted());
        assertFalse(CompletionGate.evaluate(report));
    }

    @Test
    @DisplayName("CompletionGate rejects when compilation fails")
    void testGateRejectsCompileFailure() {
        GameGoal goal = new GameGoal("goal_2", "sess_2", "Platformer");
        GoalRequirement r1 = new GoalRequirement("REQ-001", "Player exists", GoalRequirement.RequirementType.GAME_OBJECT_EXISTS, "Player", true, null);
        goal.addRequirement(r1);

        RequirementManager mgr = new RequirementManager(goal);
        mgr.markSatisfied("REQ-001", VerificationEvidence.pass("REQ-001", VerificationType.GAME_OBJECT_EXISTS, "tool", "ok"));

        // Compile success = false
        ValidationReport report = validator.validate(goal, mgr, false, true, true);
        assertFalse(report.isGoalCompleted());
    }

    @Test
    @DisplayName("CompletionGate rejects when runtime errors exist or behavior tests fail")
    void testGateRejectsRuntimeOrBehaviorFailure() {
        GameGoal goal = new GameGoal("goal_3", "sess_3", "Platformer");
        GoalRequirement r1 = new GoalRequirement("REQ-001", "Player exists", GoalRequirement.RequirementType.GAME_OBJECT_EXISTS, "Player", true, null);
        goal.addRequirement(r1);

        RequirementManager mgr = new RequirementManager(goal);
        mgr.markSatisfied("REQ-001", VerificationEvidence.pass("REQ-001", VerificationType.GAME_OBJECT_EXISTS, "tool", "ok"));

        // Runtime errors clean = false
        ValidationReport reportRuntimeFail = validator.validate(goal, mgr, true, false, true);
        assertFalse(reportRuntimeFail.isGoalCompleted());

        // Behavior tests passed = false
        ValidationReport reportBehaviorFail = validator.validate(goal, mgr, true, true, false);
        assertFalse(reportBehaviorFail.isGoalCompleted());
    }

    @Test
    @DisplayName("CompletionGate passes only when all conditions are met")
    void testGatePassesWhenAllConditionsMet() {
        GameGoal goal = new GameGoal("goal_4", "sess_4", "Platformer");
        GoalRequirement r1 = new GoalRequirement("REQ-001", "Player exists", GoalRequirement.RequirementType.GAME_OBJECT_EXISTS, "Player", true, null);
        GoalRequirement r2 = new GoalRequirement("REQ-002", "Optional polish", GoalRequirement.RequirementType.LIGHTING_CONFIGURED, "Light", false, null);
        goal.addRequirement(r1);
        goal.addRequirement(r2);

        RequirementManager mgr = new RequirementManager(goal);
        mgr.markSatisfied("REQ-001", VerificationEvidence.pass("REQ-001", VerificationType.GAME_OBJECT_EXISTS, "tool", "ok"));
        // r2 is optional and not satisfied

        ValidationReport report = validator.validate(goal, mgr, true, true, true);
        assertTrue(report.isGoalCompleted());
        assertTrue(CompletionGate.evaluate(report));
    }
}
