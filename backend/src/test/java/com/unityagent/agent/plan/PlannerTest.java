package com.unityagent.agent.plan;

import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalAnalyzer;
import com.unityagent.agent.goal.RequirementManager;
import com.unityagent.tools.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("9.5 & 9.6 LongHorizonPlanner & DAG Plan Tests")
class PlannerTest {

    private final ToolRegistry registry = new ToolRegistry(List.of());
    private final LongHorizonPlanner planner = new LongHorizonPlanner(registry);
    private final GoalAnalyzer analyzer = new GoalAnalyzer();

    @Test
    @DisplayName("Planner distinguishes CREATE vs REUSE when objects already exist")
    void testCreateVsReuseDistinction() {
        GameGoal goal = analyzer.analyzeGoal("sess_plan_1", "Create a third-person fantasy arena game with player, enemy and boss");
        RequirementManager mgr = new RequirementManager(goal);

        // Case 1: Fresh project (no objects, no scripts)
        AgentPlan freshPlan = planner.createPlan(goal, mgr, Set.of(), Set.of());
        PlanNode freshPlayerNode = freshPlan.getPlanNode("NODE_PLAYER");
        assertNotNull(freshPlayerNode);
        assertEquals(PlanNode.PlanActionType.CREATE, freshPlayerNode.getActionType(), "Should CREATE Player in fresh project");

        // Case 2: Existing project (Player and PlayerController already exist)
        AgentPlan existingPlan = planner.createPlan(goal, mgr, Set.of("Player", "Ground"), Set.of("PlayerController"));
        PlanNode reusePlayerNode = existingPlan.getPlanNode("NODE_PLAYER");
        assertNotNull(reusePlayerNode);
        assertEquals(PlanNode.PlanActionType.REUSE, reusePlayerNode.getActionType(), "Should REUSE Player when Player object already exists");

        PlanNode reuseScriptNode = existingPlan.getPlanNode("NODE_SCRIPT_PLAYER_CONTROLLER");
        assertNotNull(reuseScriptNode);
        assertEquals(PlanNode.PlanActionType.REUSE, reuseScriptNode.getActionType(), "Should REUSE script when PlayerController already exists");
    }

    @Test
    @DisplayName("DAG plan has branching structure and computes ready nodes")
    void testBranchingAndReadyNodes() {
        GameGoal goal = analyzer.analyzeGoal("sess_plan_2", "Create arena combat game with boss");
        RequirementManager mgr = new RequirementManager(goal);

        AgentPlan plan = planner.createPlan(goal, mgr, Set.of(), Set.of());
        assertTrue(plan.getNodeCount() >= 8, "Plan should contain at least 8 nodes");

        // Environment and standalone scripts should be ready initially
        List<PlanNode> readyInitially = plan.getReadyPlanNodes();
        assertFalse(readyInitially.isEmpty());
        assertTrue(readyInitially.stream().anyMatch(n -> n.getNodeId().equals("NODE_ENV_SETUP")));

        // Complete NODE_ENV_SETUP
        plan.markNodeCompleted("NODE_ENV_SETUP", "Ground plane created");

        // Now NODE_PLAYER should become ready
        List<PlanNode> readyAfterEnv = plan.getReadyPlanNodes();
        assertTrue(readyAfterEnv.stream().anyMatch(n -> n.getNodeId().equals("NODE_PLAYER")));
    }
}
