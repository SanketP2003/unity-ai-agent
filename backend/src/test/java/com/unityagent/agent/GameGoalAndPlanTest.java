package com.unityagent.agent;

import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalRequirement;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.plan.PlanStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6 Goal & Planning Domain Tests")
class GameGoalAndPlanTest {

    @Test
    @DisplayName("GameGoal and GoalRequirement lifecycle")
    void testGameGoalLifecycle() {
        GameGoal goal = new GameGoal("goal_001", "session_001", "Build a small platform game");
        assertEquals("goal_001", goal.getGoalId());
        assertEquals(GameGoal.GoalStatus.ACTIVE, goal.getStatus());
        assertTrue(goal.getRequirements().isEmpty());
        assertFalse(goal.allRequirementsSatisfied());

        GoalRequirement r1 = new GoalRequirement(
                "req_player",
                "A red player sphere exists",
                GoalRequirement.RequirementType.GAME_OBJECT_EXISTS,
                "Player",
                Map.of("primitive", "Sphere"),
                "SCENE_INSPECTION"
        );
        assertEquals(GoalRequirement.RequirementStatus.PENDING, r1.getStatus());

        GoalRequirement r2 = new GoalRequirement(
                "req_compile",
                "Project compiles cleanly",
                GoalRequirement.RequirementType.COMPILE_SUCCESS,
                "CompilationPipeline",
                Map.of(),
                "COMPILER"
        );

        goal.addRequirement(r1);
        goal.addRequirement(r2);
        assertEquals(2, goal.getRequirements().size());
        assertFalse(goal.allRequirementsSatisfied());

        // Update requirement status
        r1.setStatus(GoalRequirement.RequirementStatus.IN_PROGRESS);
        assertFalse(r1.isSatisfied());

        r1.setStatus(GoalRequirement.RequirementStatus.SATISFIED);
        assertTrue(r1.isSatisfied());
        assertFalse(goal.allRequirementsSatisfied());

        r2.setStatus(GoalRequirement.RequirementStatus.SATISFIED);
        assertTrue(goal.allRequirementsSatisfied());

        goal.setStatus(GameGoal.GoalStatus.COMPLETED);
        assertEquals(GameGoal.GoalStatus.COMPLETED, goal.getStatus());
    }

    @Test
    @DisplayName("Goal requirement failure and blocking")
    void testGoalRequirementFailure() {
        GoalRequirement r = new GoalRequirement(
                "req_move",
                "Player moves on key press",
                GoalRequirement.RequirementType.BEHAVIOR_TEST,
                "Player",
                Map.of(),
                "GAME_TEST"
        );
        r.setStatus(GoalRequirement.RequirementStatus.FAILED);
        r.setFailureReason("Player position did not change");
        assertEquals(GoalRequirement.RequirementStatus.FAILED, r.getStatus());
        assertEquals("Player position did not change", r.getFailureReason());

        r.setStatus(GoalRequirement.RequirementStatus.BLOCKED);
        assertEquals(GoalRequirement.RequirementStatus.BLOCKED, r.getStatus());
    }

    @Test
    @DisplayName("AgentPlan creation and step progression")
    void testAgentPlanProgression() {
        AgentPlan plan = new AgentPlan("plan_001", "goal_001");
        assertEquals(AgentPlan.PlanStatus.DRAFT, plan.getStatus());

        PlanStep step1 = new PlanStep("step_1", "Inspect scene", List.of("get_scene_hierarchy"), "Scene inspected");
        PlanStep step2 = new PlanStep("step_2", "Create Player", List.of("create_primitive"), "Player created");
        PlanStep step3 = new PlanStep("step_3", "Compile", List.of("compile_project"), "0 errors");

        plan.addStep(step1);
        plan.addStep(step2);
        plan.addStep(step3);
        assertEquals(3, plan.getSteps().size());

        plan.markCurrentStepInProgress();
        assertEquals(AgentPlan.PlanStatus.IN_PROGRESS, plan.getStatus());
        assertEquals("step_1", plan.getCurrentStep().getStepId());

        plan.markCurrentStepCompleted();
        assertEquals(1, plan.getCurrentStepIndex());
        assertEquals("step_2", plan.getCurrentStep().getStepId());

        plan.markCurrentStepCompleted();
        assertEquals(2, plan.getCurrentStepIndex());
        assertEquals("step_3", plan.getCurrentStep().getStepId());

        plan.markCurrentStepCompleted();
        assertEquals(AgentPlan.PlanStatus.COMPLETED, plan.getStatus());
    }

    @Test
    @DisplayName("Dynamic replanning: insert diagnosis and repair steps")
    void testDynamicReplanning() {
        AgentPlan plan = new AgentPlan("plan_002", "goal_002");
        plan.addStep(new PlanStep("step_build", "Build Scene", List.of("create_primitive"), "Done"));
        plan.addStep(new PlanStep("step_compile", "Compile", List.of("compile_project"), "0 errors"));
        plan.addStep(new PlanStep("step_test", "Test Gameplay", List.of("run_game_test"), "Moved"));

        plan.setCurrentStepIndex(1); // At compilation step
        plan.getCurrentStep().setStatus(PlanStep.StepStatus.FAILED);

        // Dynamically insert diagnosis and repair steps between compile and test
        PlanStep diagStep = new PlanStep("step_diag", "Diagnose CS0103", List.of("read_script"), "Cause identified");
        PlanStep fixStep = new PlanStep("step_fix", "Repair Player.cs", List.of("update_script", "compile_project"), "Compiled");

        plan.insertStep(2, diagStep);
        plan.insertStep(3, fixStep);

        assertEquals(5, plan.getSteps().size());
        assertEquals("step_diag", plan.getSteps().get(2).getStepId());
        assertEquals("step_fix", plan.getSteps().get(3).getStepId());
        assertEquals("step_test", plan.getSteps().get(4).getStepId());
    }
}
