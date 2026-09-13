package com.unityagent.agent.plan;

import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalRequirement;
import com.unityagent.agent.goal.RequirementManager;
import com.unityagent.agent.verification.VerificationType;
import com.unityagent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Long-Horizon Planner that generates structured, branching DAG plans.
 * Inspects existing scene state and project memory to strictly distinguish:
 * CREATE, REUSE, MODIFY, and VERIFY actions.
 */
@Service
public class LongHorizonPlanner {

    private static final Logger log = LoggerFactory.getLogger(LongHorizonPlanner.class);

    private final ToolRegistry toolRegistry;

    public LongHorizonPlanner(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public AgentPlan createPlan(GameGoal goal) {
        return createPlan(goal, new RequirementManager(), List.of(), List.of());
    }

    public AgentPlan createPlan(GameGoal goal, RequirementManager requirementManager) {
        return createPlan(goal, requirementManager, List.of(), List.of());
    }

    /**
     * Builds a comprehensive DAG AgentPlan based on the goal, requirements, and current project context.
     *
     * @param goal the overall game objective
     * @param requirementManager tracks requirements and prerequisites
     * @param existingGameObjects list of GameObject names currently existing in the active Unity scene
     * @param existingScripts list of script class names currently compiled in Assets/
     * @return a branching DAG plan
     */
    public AgentPlan createPlan(GameGoal goal,
                                RequirementManager requirementManager,
                                Collection<String> existingGameObjects,
                                Collection<String> existingScripts) {
        if (goal == null) {
            throw new IllegalArgumentException("GameGoal cannot be null");
        }

        Set<String> sceneObjects = new HashSet<>();
        if (existingGameObjects != null) {
            for (String obj : existingGameObjects) {
                if (obj != null) sceneObjects.add(obj.toLowerCase());
            }
        }

        Set<String> scripts = new HashSet<>();
        if (existingScripts != null) {
            for (String s : existingScripts) {
                if (s != null) scripts.add(s.toLowerCase());
            }
        }

        AgentPlan plan = new AgentPlan("plan_" + UUID.randomUUID().toString().substring(0, 8), goal.getGoalId());
        log.info("Creating long-horizon plan for goal: '{}' (existing objects: {}, scripts: {})",
                goal.getDescription(), sceneObjects.size(), scripts.size());

        // 1. Initial Scene Setup / Environment
        String envNodeId = "NODE_ENV_SETUP";
        boolean arenaExists = sceneObjects.contains("ground") || sceneObjects.contains("arena") || sceneObjects.contains("environment");
        PlanNode.PlanActionType envAction = arenaExists ? PlanNode.PlanActionType.REUSE : PlanNode.PlanActionType.CREATE;
        PlanNode envNode = new PlanNode(envNodeId, (arenaExists ? "Reuse existing environment ground" : "Create arena environment ground"), envAction, List.of("create_primitive", "get_scene_hierarchy"));
        plan.addPlanNode(envNode);

        // 2. Player Character Setup
        String playerNodeId = "NODE_PLAYER";
        boolean playerExists = sceneObjects.contains("player");
        PlanNode.PlanActionType playerAction = playerExists ? PlanNode.PlanActionType.REUSE : PlanNode.PlanActionType.CREATE;
        PlanNode playerNode = new PlanNode(playerNodeId, (playerExists ? "Reuse existing Player object" : "Create Player character GameObject"), playerAction, List.of("create_primitive", "add_component"));
        plan.addPlanNode(playerNode);
        plan.addPlanDependency(playerNodeId, envNodeId, DependencyType.REQUIRES);

        // Branch 1: Movement & Controller Pipeline
        String moveScriptNodeId = "NODE_SCRIPT_PLAYER_CONTROLLER";
        boolean moveScriptExists = scripts.contains("playercontroller");
        PlanNode.PlanActionType moveScriptAction = moveScriptExists ? PlanNode.PlanActionType.REUSE : PlanNode.PlanActionType.CREATE;
        PlanNode moveScriptNode = new PlanNode(moveScriptNodeId, (moveScriptExists ? "Reuse PlayerController script" : "Create PlayerController script"), moveScriptAction, List.of("create_script", "update_script"));
        plan.addPlanNode(moveScriptNode);

        String attachControllerNodeId = "NODE_ATTACH_CONTROLLER";
        PlanNode attachControllerNode = new PlanNode(attachControllerNodeId, "Attach and configure PlayerController on Player", PlanNode.PlanActionType.MODIFY, List.of("add_component", "set_component_property"));
        plan.addPlanNode(attachControllerNode);
        plan.addPlanDependency(attachControllerNodeId, playerNodeId, DependencyType.REQUIRES);
        plan.addPlanDependency(attachControllerNodeId, moveScriptNodeId, DependencyType.REQUIRES);

        // Branch 2: Health & Damage System Pipeline
        String healthScriptNodeId = "NODE_SCRIPT_HEALTH";
        boolean healthScriptExists = scripts.contains("healthsystem");
        PlanNode.PlanActionType healthAction = healthScriptExists ? PlanNode.PlanActionType.REUSE : PlanNode.PlanActionType.CREATE;
        PlanNode healthScriptNode = new PlanNode(healthScriptNodeId, (healthScriptExists ? "Reuse HealthSystem script" : "Create HealthSystem script"), healthAction, List.of("create_script"));
        plan.addPlanNode(healthScriptNode);

        String attachHealthNodeId = "NODE_ATTACH_HEALTH";
        PlanNode attachHealthNode = new PlanNode(attachHealthNodeId, "Attach HealthSystem to Player", PlanNode.PlanActionType.MODIFY, List.of("add_component"));
        plan.addPlanNode(attachHealthNode);
        plan.addPlanDependency(attachHealthNodeId, playerNodeId, DependencyType.REQUIRES);
        plan.addPlanDependency(attachHealthNodeId, healthScriptNodeId, DependencyType.REQUIRES);

        // Convergence Point: Combat Integration
        String combatNodeId = "NODE_COMBAT_SETUP";
        PlanNode combatNode = new PlanNode(combatNodeId, "Configure player combat mechanics and inputs", PlanNode.PlanActionType.MODIFY, List.of("set_component_property", "bind_input_action"));
        plan.addPlanNode(combatNode);
        plan.addPlanDependency(combatNodeId, attachControllerNodeId, DependencyType.REQUIRES);
        plan.addPlanDependency(combatNodeId, attachHealthNodeId, DependencyType.REQUIRES);

        // Enemy Pipeline
        String enemyNodeId = "NODE_ENEMY_SPAWN";
        boolean enemyExists = sceneObjects.contains("enemy");
        PlanNode.PlanActionType enemyAction = enemyExists ? PlanNode.PlanActionType.REUSE : PlanNode.PlanActionType.CREATE;
        PlanNode enemyNode = new PlanNode(enemyNodeId, (enemyExists ? "Reuse existing Enemy objects" : "Create enemy spawns with AI"), enemyAction, List.of("create_primitive", "add_component"));
        plan.addPlanNode(enemyNode);
        plan.addPlanDependency(enemyNodeId, combatNodeId, DependencyType.REQUIRES);

        // Boss Pipeline (if boss mentioned in goal)
        String bossNodeId = "NODE_BOSS_SPAWN";
        if (goal.getDescription().toLowerCase().contains("boss")) {
            boolean bossExists = sceneObjects.contains("boss");
            PlanNode.PlanActionType bossAction = bossExists ? PlanNode.PlanActionType.REUSE : PlanNode.PlanActionType.CREATE;
            PlanNode bossNode = new PlanNode(bossNodeId, (bossExists ? "Reuse existing Boss object" : "Create Arena Boss with special attacks"), bossAction, List.of("create_primitive", "set_material_color", "add_component"));
            plan.addPlanNode(bossNode);
            plan.addPlanDependency(bossNodeId, enemyNodeId, DependencyType.REQUIRES);
        }

        // Compilation Verification Gate Node
        String compileGateNodeId = "NODE_VERIFY_COMPILE";
        PlanNode compileNode = new PlanNode(compileGateNodeId, "Compile project and verify zero compilation diagnostics", PlanNode.PlanActionType.VERIFY, List.of("compile_project"));
        plan.addPlanNode(compileNode);
        plan.addPlanDependency(compileGateNodeId, enemyNodeId, DependencyType.REQUIRES);

        // Behavioral Testing Nodes
        String moveTestNodeId = "NODE_TEST_PLAYER_MOVEMENT";
        PlanNode moveTestNode = new PlanNode(moveTestNodeId, "Test Player responds to input and moves in Play Mode", PlanNode.PlanActionType.VERIFY, List.of("enter_play_mode", "run_game_test", "exit_play_mode"));
        plan.addPlanNode(moveTestNode);
        plan.addPlanDependency(moveTestNodeId, compileGateNodeId, DependencyType.REQUIRES);

        // Final Victory & Objective Validation Gate
        String finalValidationNodeId = "NODE_VALIDATE_GOAL";
        PlanNode finalValidationNode = new PlanNode(finalValidationNodeId, "Execute full objective validation and evaluate CompletionGate", PlanNode.PlanActionType.VERIFY, List.of("validate_game_state"));
        plan.addPlanNode(finalValidationNode);
        plan.addPlanDependency(finalValidationNodeId, moveTestNodeId, DependencyType.REQUIRES);

        // Map requirements to plan nodes
        for (GoalRequirement req : goal.getRequirements()) {
            String desc = req.getDescription().toLowerCase();
            if (desc.contains("player exists")) {
                playerNode.addRequirementId(req.getRequirementId());
            } else if (desc.contains("ground exists") || desc.contains("arena exists")) {
                envNode.addRequirementId(req.getRequirementId());
            } else if (desc.contains("player can move") || desc.contains("player can jump")) {
                moveTestNode.addRequirementId(req.getRequirementId());
            } else if (desc.contains("enemy")) {
                enemyNode.addRequirementId(req.getRequirementId());
            }
            finalValidationNode.addRequirementId(req.getRequirementId());
        }

        // Backward compatibility: generate linear PlanStep representations as well
        generateLinearStepsForCompatibility(plan);

        // Compute initial ready nodes
        plan.getReadyPlanNodes();

        log.info("Generated DAG plan {} with {} nodes and initial ready count: {}",
                plan.getPlanId(), plan.getNodeCount(), plan.getReadyPlanNodes().size());
        return plan;
    }

    private void generateLinearStepsForCompatibility(AgentPlan plan) {
        int idx = 1;
        for (PlanNode node : plan.getPlanNodes().values()) {
            PlanStep step = new PlanStep("step_" + idx++, node.getDescription(), node.getCandidateTools(), "Completed " + node.getNodeId());
            plan.addStep(step);
        }
    }
}
