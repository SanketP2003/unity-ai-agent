package com.unityagent.agent.goal;

import com.unityagent.agent.verification.VerificationEvidence;
import com.unityagent.agent.verification.VerificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("9.1 RequirementManager Lifecycle & Transitions Tests")
class RequirementManagerTest {

    @Test
    @DisplayName("Readiness updates as prerequisites are satisfied")
    void testReadinessProgression() {
        GameGoal goal = new GameGoal("goal_1", "sess_1", "Build game");

        GoalRequirement r1 = new GoalRequirement("REQ-001", "Player exists", GoalRequirement.RequirementType.GAME_OBJECT_EXISTS, "Player", null, null);
        GoalRequirement r2 = new GoalRequirement("REQ-002", "Player can move", GoalRequirement.RequirementType.BEHAVIOR_TEST, "Player", null, null);
        r2.addPrerequisite("REQ-001");

        goal.addRequirement(r1);
        goal.addRequirement(r2);

        RequirementManager mgr = new RequirementManager(goal);

        // Initially: r1 has no prerequisites so it should be READY; r2 depends on r1 so it should be PENDING
        assertEquals(RequirementStatus.READY, r1.getStatus());
        assertEquals(RequirementStatus.PENDING, r2.getStatus());

        List<GoalRequirement> ready = mgr.getReadyRequirements();
        assertEquals(1, ready.size());
        assertEquals("REQ-001", ready.get(0).getRequirementId());

        // Mark r1 in progress then satisfied
        mgr.markInProgress("REQ-001");
        assertEquals(RequirementStatus.IN_PROGRESS, r1.getStatus());

        VerificationEvidence ev = VerificationEvidence.pass("REQ-001", VerificationType.GAME_OBJECT_EXISTS, "get_scene_hierarchy", "Player found");
        mgr.markSatisfied("REQ-001", ev);
        assertEquals(RequirementStatus.SATISFIED, r1.getStatus());

        // Now r2 should have transitioned to READY
        assertEquals(RequirementStatus.READY, r2.getStatus());
        List<GoalRequirement> readyAfter = mgr.getReadyRequirements();
        assertEquals(1, readyAfter.size());
        assertEquals("REQ-002", readyAfter.get(0).getRequirementId());
    }

    @Test
    @DisplayName("Status transitions to FAILED, BLOCKED, and STALE")
    void testFailureAndStaleTransitions() {
        GameGoal goal = new GameGoal("goal_2", "sess_2", "Build arena");
        GoalRequirement r1 = new GoalRequirement("REQ-001", "Arena exists", GoalRequirement.RequirementType.GAME_OBJECT_EXISTS, "Arena", null, null);
        goal.addRequirement(r1);

        RequirementManager mgr = new RequirementManager(goal);
        mgr.markFailed("REQ-001", "Timeout creating mesh");
        assertEquals(RequirementStatus.FAILED, r1.getStatus());
        assertEquals("Timeout creating mesh", r1.getFailureReason());

        mgr.markStale("REQ-001");
        assertEquals(RequirementStatus.STALE, r1.getStatus());
    }
}
