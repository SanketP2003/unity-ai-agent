package com.unityagent.agent;

import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalAnalyzer;
import com.unityagent.agent.goal.GoalRequirement;
import com.unityagent.agent.goal.RequirementManager;
import com.unityagent.agent.identity.EntityCandidate;
import com.unityagent.agent.identity.EntityResolutionResult;
import com.unityagent.agent.identity.EntityResolutionService;
import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.plan.LongHorizonPlanner;
import com.unityagent.agent.plan.PlanNode;
import com.unityagent.agent.plan.ReplanningEngine;
import com.unityagent.agent.recovery.FailureClassifier;
import com.unityagent.agent.recovery.RecoveryEngine;
import com.unityagent.agent.verification.*;
import com.unityagent.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * End-to-end acceptance scenarios covering the three representative game genres:
 * 1. 2D/3D Platformer
 * 2. Top-Down Combat Arena
 * 3. Third-Person Fantasy Arena
 */
class Phase9AcceptanceScenarioTest {

    private GoalAnalyzer goalAnalyzer;
    private RequirementManager requirementManager;
    private LongHorizonPlanner planner;
    private AgentLoop agentLoop;
    private ReplanningEngine replanningEngine;
    private FailureClassifier failureClassifier;
    private RecoveryEngine recoveryEngine;
    private ObjectiveValidator objectiveValidator;
    private CompletionGate completionGate;
    private EntityResolutionService entityResolution;
    private BehaviorTestGenerator testGenerator;
    private BehaviorTestEngine testEngine;

    @BeforeEach
    void setUp() {
        goalAnalyzer = new GoalAnalyzer();
        requirementManager = new RequirementManager();
        planner = new LongHorizonPlanner(new ToolRegistry(List.of()));
        agentLoop = Mockito.mock(AgentLoop.class);
        replanningEngine = new ReplanningEngine();
        failureClassifier = new FailureClassifier();
        recoveryEngine = new RecoveryEngine();
        objectiveValidator = Mockito.mock(ObjectiveValidator.class);
        completionGate = new CompletionGate();
        entityResolution = new EntityResolutionService();
        testGenerator = new BehaviorTestGenerator();
        testEngine = new BehaviorTestEngine();
    }

    @Test
    @DisplayName("Acceptance Scenario 1: Complete 3D Platformer game construction, behavioral test, and completion gate")
    void testPlatformerAcceptanceScenario(@TempDir Path tempDir) {
        CheckpointService checkpointService = new CheckpointService(tempDir);
        AutonomousRunController controller = new AutonomousRunController(
                goalAnalyzer, requirementManager, planner, agentLoop,
                replanningEngine, failureClassifier, recoveryEngine,
                objectiveValidator, completionGate, checkpointService
        );

        String prompt = "Build me a small playable 3D platform game with a player, ground, platforms, goal, camera, lighting, and movement.";
        AutonomousRunState state = controller.startRun(prompt, "proj_platformer", "sess_plat_01");

        assertNotNull(state);
        assertEquals(AutonomousRunState.RunStatus.RUNNING, state.getStatus());
        AgentPlan plan = state.getPlan();
        assertTrue(plan.getNodeCount() >= 8, "Platformer plan must contain at least 8 structured DAG nodes");

        // Verify behavioral test generation for movement requirement
        GoalRequirement moveReq = new GoalRequirement("req_move", "proj_platformer", "Player movement controller with physics");
        List<BehaviorTestScenario> scenarios = testGenerator.generateScenarios(moveReq);
        assertFalse(scenarios.isEmpty());

        BehaviorTestResult moveResult = testEngine.evaluateScenario(scenarios.get(0), Map.of(
                "deltaX", 2.1,
                "errorCount", 0,
                "hasErrors", false
        ));
        assertTrue(moveResult.isPassed(), "Behavioral movement test must pass on simulated input");
        state.addEvidence(moveResult.toEvidence());

        // Fast-forward plan nodes completion
        for (PlanNode node : plan.getPlanNodes().values()) {
            plan.markNodeCompleted(node.getNodeId(), "Completed: " + node.getDescription());
        }

        // Setup completion report
        ValidationReport report = new ValidationReport("proj_platformer");
        report.setCompilationSuccess(true);
        report.setRuntimeErrorsClean(true);
        report.setBehaviorTestsPassed(true);
        report.setRequiredCount(state.getGoal().getRequiredRequirements().size());
        report.setSatisfiedRequiredCount(state.getGoal().getRequiredRequirements().size());
        report.setSummary("Platformer acceptance criteria all satisfied");
        when(objectiveValidator.validateAll(any(), any())).thenReturn(report);

        // Finalize run
        controller.executeNextSubGoal(state.getRunId());
        assertEquals(AutonomousRunState.RunStatus.COMPLETED, state.getStatus());
        assertTrue(CompletionGate.evaluate(report));
    }

    @Test
    @DisplayName("Acceptance Scenario 2: Top-Down Combat Arena with duplicate prevention and combat verification")
    void testTopDownCombatAcceptanceScenario(@TempDir Path tempDir) {
        CheckpointService checkpointService = new CheckpointService(tempDir);
        AutonomousRunController controller = new AutonomousRunController(
                goalAnalyzer, requirementManager, planner, agentLoop,
                replanningEngine, failureClassifier, recoveryEngine,
                objectiveValidator, completionGate, checkpointService
        );

        // Pre-existing Arena in scene: EntityResolutionService must detect and reuse it!
        EntityCandidate targetArena = new EntityCandidate(null, "Arena", "/Arena", List.of("Transform", "BoxCollider"));
        EntityCandidate existingArena = new EntityCandidate("obj_arena_01", "Arena", "/Arena", List.of("Transform", "BoxCollider"));
        EntityResolutionResult res = entityResolution.resolveEntity(targetArena, List.of(existingArena), Set.of("Arena"));

        assertTrue(res.shouldReuse(), "Entity resolution must reuse existing Arena to prevent duplicate geometry");

        String prompt = "Build a top-down combat arena with player, enemy orc, attack combat controller, and health system.";
        AutonomousRunState state = controller.startRun(prompt, "proj_combat", "sess_combat_01");

        // Verify combat scenario synthesis
        GoalRequirement combatReq = new GoalRequirement("req_combat", "proj_combat", "Player attack reduces enemy health in combat");
        List<BehaviorTestScenario> combatScenarios = testGenerator.generateScenarios(combatReq);
        assertFalse(combatScenarios.isEmpty());

        BehaviorTestResult combatResult = testEngine.evaluateScenario(combatScenarios.get(0), Map.of(
                "EnemyHealth.currentHealth", 75.0,
                "errorCount", 0,
                "hasErrors", false
        ));
        assertTrue(combatResult.isPassed(), "Combat interaction test must pass when enemy health is reduced");
    }

    @Test
    @DisplayName("Acceptance Scenario 3: Third-Person Fantasy Arena with zero errors and CompletionGate enforcement")
    void testThirdPersonFantasyArenaAcceptanceScenario(@TempDir Path tempDir) {
        CheckpointService checkpointService = new CheckpointService(tempDir);
        AutonomousRunController controller = new AutonomousRunController(
                goalAnalyzer, requirementManager, planner, agentLoop,
                replanningEngine, failureClassifier, recoveryEngine,
                objectiveValidator, completionGate, checkpointService
        );

        String prompt = "Create a third-person fantasy combat arena with orbit camera, enemy spawners, lighting, and player controls.";
        AutonomousRunState state = controller.startRun(prompt, "proj_fantasy", "sess_fan_01");

        assertNotNull(state.getPlan());
        assertTrue(state.getPlan().getNodeCount() >= 5);

        // Verify CompletionGate strictly blocks if compile errors exist
        ValidationReport blockedReport = new ValidationReport("proj_fantasy");
        blockedReport.setCompilationSuccess(false); // Compiler error present
        blockedReport.setRuntimeErrorsClean(true);
        blockedReport.setBehaviorTestsPassed(true);
        blockedReport.setRequiredCount(3);
        blockedReport.setSatisfiedRequiredCount(3);
        assertFalse(CompletionGate.evaluate(blockedReport), "CompletionGate MUST reject if compilation has errors");

        // Verify CompletionGate approves when clean
        ValidationReport cleanReport = new ValidationReport("proj_fantasy");
        cleanReport.setCompilationSuccess(true);
        cleanReport.setRuntimeErrorsClean(true);
        cleanReport.setBehaviorTestsPassed(true);
        cleanReport.setRequiredCount(3);
        cleanReport.setSatisfiedRequiredCount(3);
        assertTrue(CompletionGate.evaluate(cleanReport), "CompletionGate MUST approve when all criteria are satisfied");
    }
}
