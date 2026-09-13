package com.unityagent.agent;

import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalAnalyzer;
import com.unityagent.agent.goal.RequirementManager;
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
import org.mockito.Mockito;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AutonomousRunControllerTest {

    private GoalAnalyzer goalAnalyzer;
    private RequirementManager requirementManager;
    private LongHorizonPlanner planner;
    private AgentLoop agentLoop;
    private ReplanningEngine replanningEngine;
    private FailureClassifier failureClassifier;
    private RecoveryEngine recoveryEngine;
    private ObjectiveValidator objectiveValidator;
    private CompletionGate completionGate;
    private CheckpointService checkpointService;
    private AutonomousRunController controller;

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

        // Use temporary directory for checkpoints
        String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "unity_test_ck_" + System.currentTimeMillis();
        checkpointService = new CheckpointService(java.nio.file.Paths.get(tempDir));

        controller = new AutonomousRunController(
                goalAnalyzer,
                requirementManager,
                planner,
                agentLoop,
                replanningEngine,
                failureClassifier,
                recoveryEngine,
                objectiveValidator,
                completionGate,
                checkpointService,
                new AutonomyLimits(50, 100, 5, 5, 600)
        );
    }

    @Test
    @DisplayName("Starting run decomposes goal, generates plan, and persists initial checkpoint")
    void testStartRunInitializesPlanAndSavesCheckpoint() {
        AutonomousRunState state = controller.startRun(
                "Create a 2D Platformer with player movement and ground",
                "test_proj_01",
                "test_session_01"
        );

        assertNotNull(state);
        assertNotNull(state.getRunId());
        assertEquals(AutonomousRunState.RunStatus.RUNNING, state.getStatus());
        assertNotNull(state.getGoal());
        assertFalse(state.getGoal().getRequirements().isEmpty());
        assertNotNull(state.getPlan());
        assertFalse(state.getPlan().getPlanNodes().isEmpty());
        assertNotNull(state.getLastCheckpointId());
    }

    @Test
    @DisplayName("executeNextSubGoal submits granular sub-goal to AgentLoop and updates state on success")
    void testStepRunSubmitsSubGoalToAgentLoop() {
        AutonomousRunState state = controller.startRun(
                "Create player character",
                "test_proj_02",
                "test_session_02"
        );

        // Mock AgentLoop response
        AgentRunResult mockResult = AgentRunResult.success(
                state.getSessionId(),
                "subrun_1",
                "Created player capsule successfully",
                2,
                1,
                List.of()
        );
        when(agentLoop.run(anyString(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(mockResult);

        // Execute first sub-goal
        boolean hasMore = controller.executeNextSubGoal(state.getRunId());

        assertEquals(1, state.getExecutionState().getCompletedNodes().size());
        assertEquals(1, state.getExecutionState().getTotalToolCalls());
        assertNotNull(state.getLastCheckpointId());
    }

    @Test
    @DisplayName("CompletionGate authoritatively gates completion: approved when valid, rejected when invalid")
    void testCompletionGateEnforcedAtFinalization() {
        AutonomousRunState state = controller.startRun(
                "Verify game build",
                "test_proj_03",
                "test_session_03"
        );

        // Mark all plan nodes completed
        for (PlanNode node : state.getPlan().getPlanNodes().values()) {
            state.getPlan().markNodeCompleted(node.getNodeId(), "Done");
        }

        // Case 1: Validation report has compile errors -> CompletionGate rejects completion
        ValidationReport failingReport = new ValidationReport(state.getGoal().getGoalId());
        failingReport.setCompilationSuccess(false);
        failingReport.setRuntimeErrorsClean(true);
        failingReport.setBehaviorTestsPassed(true);
        failingReport.setRequiredCount(2);
        failingReport.setSatisfiedRequiredCount(2);
        failingReport.setSummary("Gate rejected due to compile errors");
        when(objectiveValidator.validateAll(any(), any())).thenReturn(failingReport);

        boolean hasMore = controller.executeNextSubGoal(state.getRunId());
        assertFalse(hasMore);
        assertEquals(AutonomousRunState.RunStatus.FAILED, state.getStatus());

        // Case 2: Validation report satisfies all conditions -> CompletionGate completes run
        ValidationReport passingReport = new ValidationReport(state.getGoal().getGoalId());
        passingReport.setCompilationSuccess(true);
        passingReport.setRuntimeErrorsClean(true);
        passingReport.setBehaviorTestsPassed(true);
        passingReport.setRequiredCount(2);
        passingReport.setSatisfiedRequiredCount(2);
        passingReport.setSummary("All requirements validated");
        when(objectiveValidator.validateAll(any(), any())).thenReturn(passingReport);

        // Reset status to running and re-test finalization
        state.setStatus(AutonomousRunState.RunStatus.RUNNING);
        controller.executeNextSubGoal(state.getRunId());
        assertEquals(AutonomousRunState.RunStatus.COMPLETED, state.getStatus());
        assertNotNull(state.getCompletedAt());
    }

    @Test
    @DisplayName("Pause and resume reconciles checkpoint against live scene entities")
    void testPauseAndResumeWithReconciliation() {
        AutonomousRunState state = controller.startRun(
                "Create player and enemy",
                "test_proj_04",
                "test_session_04"
        );

        String runId = state.getRunId();
        String initialCheckpointId = state.getLastCheckpointId();

        // Pause
        controller.pauseRun(runId, "Pausing for scene inspection");
        assertEquals(AutonomousRunState.RunStatus.PAUSED, state.getStatus());
        assertNotNull(state.getCurrentIntervention());

        // Resume with existing scene objects
        AutonomousRunState resumed = controller.resumeRun(runId, initialCheckpointId, List.of("Player"));
        assertNotNull(resumed);
        assertEquals(AutonomousRunState.RunStatus.RUNNING, resumed.getStatus());
        assertNull(resumed.getCurrentIntervention());
    }
}
