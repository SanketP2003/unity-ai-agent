package com.unityagent.agent.plan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReplanningEngineTest {

    private ReplanningEngine replanningEngine;
    private AgentPlan plan;

    @BeforeEach
    void setUp() {
        replanningEngine = new ReplanningEngine(5);
        plan = new AgentPlan("test_plan_01", "goal_platformer");

        // Set up DAG: Node A -> Node B -> Node C -> Node D
        PlanNode nodeA = new PlanNode("node_A", "Create Player GameObject", PlanNode.PlanActionType.CREATE, List.of("create_primitive"));
        PlanNode nodeB = new PlanNode("node_B", "Add Rigidbody to Player", PlanNode.PlanActionType.MODIFY, List.of("add_component"));
        nodeB.addDependency("node_A");

        PlanNode nodeC = new PlanNode("node_C", "Attach PlayerController script", PlanNode.PlanActionType.MODIFY, List.of("add_component"));
        nodeC.addDependency("node_B");

        PlanNode nodeD = new PlanNode("node_D", "Verify Player Movement", PlanNode.PlanActionType.VERIFY, List.of("run_game_test"));
        nodeD.addDependency("node_C");

        plan.addPlanNode(nodeA);
        plan.addPlanNode(nodeB);
        plan.addPlanNode(nodeC);
        plan.addPlanNode(nodeD);

        plan.addPlanDependency("node_B", "node_A", DependencyType.REQUIRES);
        plan.addPlanDependency("node_C", "node_B", DependencyType.REQUIRES);
        plan.addPlanDependency("node_D", "node_C", DependencyType.REQUIRES);
    }

    @Test
    @DisplayName("Plan revision strictly does not mutate completed history")
    void testPlanRevisionDoesNotMutateCompletedHistory() {
        // Step 1: Execute and complete node A
        plan.markNodeInProgress("node_A");
        plan.getPlanNode("node_A").setParameters(Map.of("primitiveType", "Capsule", "name", "Player"));
        plan.markNodeCompleted("node_A", "Created Capsule named Player at (0,1,0)");

        // Step 2: Execute and complete node B
        plan.markNodeInProgress("node_B");
        plan.getPlanNode("node_B").setParameters(Map.of("component", "Rigidbody", "useGravity", true));
        plan.markNodeCompleted("node_B", "Added Rigidbody component to Player");

        // Verify pre-replan state: nodes A and B are completed
        assertTrue(plan.getPlanNode("node_A").isCompleted());
        assertTrue(plan.getPlanNode("node_B").isCompleted());
        assertEquals(2, plan.getCompletedNodeIds().size());
        assertTrue(plan.getCompletedNodeIds().containsAll(List.of("node_A", "node_B")));

        // Step 3: Node C fails due to missing PlayerController.cs script
        plan.markNodeInProgress("node_C");
        plan.markNodeFailed("node_C", "Type 'PlayerController' not found (CS0246)");

        // Step 4: Replan using ReplanningEngine
        PlanRevision revision = replanningEngine.replanOnFailure(
                plan,
                "node_C",
                "Missing PlayerController script",
                "PlayerController.cs"
        );

        assertNotNull(revision);
        assertEquals(2, revision.getRevisionNumber());
        assertEquals("FAILURE_RECOVERY", revision.getTriggerReason());

        // CRITICAL ACCEPTANCE GATE CHECKS:
        // 1. Revision snapshot must capture exactly completed nodes at the moment of replan
        assertEquals(List.of("node_A", "node_B"), revision.getCompletedNodeIds());

        // 2. Completed nodes must still be COMPLETED and unmutated
        PlanNode postNodeA = plan.getPlanNode("node_A");
        assertEquals(PlanNode.PlanNodeStatus.COMPLETED, postNodeA.getStatus());
        assertEquals("Created Capsule named Player at (0,1,0)", postNodeA.getResultSummary());
        assertEquals("Capsule", postNodeA.getParameters().get("primitiveType"));

        PlanNode postNodeB = plan.getPlanNode("node_B");
        assertEquals(PlanNode.PlanNodeStatus.COMPLETED, postNodeB.getStatus());
        assertEquals("Added Rigidbody component to Player", postNodeB.getResultSummary());
        assertEquals(true, postNodeB.getParameters().get("useGravity"));

        // 3. Repair node must have been injected
        assertEquals(1, revision.getAddedNodeIds().size());
        String repairNodeId = revision.getAddedNodeIds().get(0);
        assertTrue(repairNodeId.startsWith("repair_PlayerController_cs"));
        PlanNode repairNode = plan.getPlanNode(repairNodeId);
        assertNotNull(repairNode);
        assertEquals(PlanNode.PlanNodeStatus.READY, repairNode.getStatus());
        assertTrue(repairNode.getCandidateTools().contains("create_script"));

        // 4. Failed node C must now depend on the repair node and be reset to PENDING
        PlanNode postNodeC = plan.getPlanNode("node_C");
        assertEquals(PlanNode.PlanNodeStatus.PENDING, postNodeC.getStatus());
        assertTrue(postNodeC.getDependencies().contains(repairNodeId));

        // 5. Downstream node D must remain PENDING
        PlanNode postNodeD = plan.getPlanNode("node_D");
        assertEquals(PlanNode.PlanNodeStatus.PENDING, postNodeD.getStatus());
    }

    @Test
    @DisplayName("Replan on discovered entities converts CREATE to REUSE without altering completed nodes")
    void testReplanOnDiscoveredEntitiesConvertsCreateToReuse() {
        // Complete node A
        plan.markNodeInProgress("node_A");
        plan.markNodeCompleted("node_A", "Done");

        // Suppose scene already has Ground plane and MainCamera
        PlanNode nodeGround = new PlanNode("node_ground", "Create Ground Plane", PlanNode.PlanActionType.CREATE, List.of("create_primitive"));
        plan.addPlanNode(nodeGround);

        PlanRevision rev = replanningEngine.replanOnDiscoveredEntities(plan, List.of("Ground"));
        assertNotNull(rev);
        assertEquals("ENTITY_DISCOVERY", rev.getTriggerReason());

        // Node Ground should now be REUSE
        PlanNode updatedGround = plan.getPlanNode("node_ground");
        assertEquals(PlanNode.PlanActionType.REUSE, updatedGround.getActionType());
        assertTrue(updatedGround.getDescription().contains("Reuse existing Ground"));
        assertEquals(true, updatedGround.getParameters().get("reuseExisting"));

        // Node A must remain completed
        assertTrue(plan.getPlanNode("node_A").isCompleted());
    }

    @Test
    @DisplayName("Max replan limit halts execution cleanly")
    void testMaxReplanLimitEnforced() {
        ReplanningEngine limitedEngine = new ReplanningEngine(2);
        assertTrue(limitedEngine.canReplan(plan));

        limitedEngine.replanOnFailure(plan, "node_A", "Fail 1", "Script1.cs");
        assertTrue(limitedEngine.canReplan(plan));

        limitedEngine.replanOnFailure(plan, "node_B", "Fail 2", "Script2.cs");
        assertFalse(limitedEngine.canReplan(plan));

        PlanRevision blocked = limitedEngine.replanOnFailure(plan, "node_C", "Fail 3", "Script3.cs");
        assertNull(blocked);
        assertEquals(AgentPlan.PlanStatus.FAILED, plan.getStatus());
    }
}
