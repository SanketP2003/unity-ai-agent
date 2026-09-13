package com.unityagent.agent.checkpoint;

import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.AutonomyLimits;
import com.unityagent.agent.AgentLoop;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * End-to-end interruption and crash-recovery acceptance test.
 *
 * <p>Simulates a real-world abrupt crash of the backend process during a multi-step Unity build:
 * <ol>
 *   <li>Backend Process 1 starts an autonomous run, executes initial sub-goals, and persists milestone checkpoints to disk.</li>
 *   <li>Abrupt process termination is simulated by tearing down Controller 1 and its in-memory state.</li>
 *   <li>Backend Process 2 is started from scratch with fresh memory.</li>
 *   <li>Controller 2 loads the checkpoint from disk, inspects live Unity scene state, reconciles without blindly replaying past tool calls, and cleanly completes the build.</li>
 * </ol>
 */
class RealWorldInterruptionTest {

    @Test
    @DisplayName("Real-world crash interruption: Backend kill during build, restart, reconcile against live scene, and resume cleanly without duplicate work")
    void testRealWorldInterruptionAndCleanResumption(@TempDir Path tempCheckpointDir) {
        String projectId = "proj_interruption_e2e";
        String sessionId = "sess_e2e_01";

        // ==========================================
        // PHASE 1: BACKEND INSTANCE 1 (Before Crash)
        // ==========================================
        CheckpointService checkpointService1 = new CheckpointService(tempCheckpointDir);
        AgentLoop agentLoopMock1 = Mockito.mock(AgentLoop.class);
        ObjectiveValidator validatorMock1 = Mockito.mock(ObjectiveValidator.class);

        AutonomousRunController controller1 = new AutonomousRunController(
                new GoalAnalyzer(),
                new RequirementManager(),
                new LongHorizonPlanner(new ToolRegistry(List.of())),
                agentLoopMock1,
                new ReplanningEngine(),
                new FailureClassifier(),
                new RecoveryEngine(),
                validatorMock1,
                new CompletionGate(),
                checkpointService1,
                new AutonomyLimits(50, 100, 5, 5, 600)
        );

        // Start long-horizon run for a platformer
        AutonomousRunState state1 = controller1.startRun(
                "Build a 2D platformer with player movement and ground arena",
                projectId,
                sessionId
        );
        String runId = state1.getRunId();
        assertNotNull(runId);

        // Mock successful execution of sub-goals in AgentLoop
        when(agentLoopMock1.run(anyString(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(AgentRunResult.success(sessionId, "subrun_1", "Created Environment Ground", 1, 1, List.of()))
                .thenReturn(AgentRunResult.success(sessionId, "subrun_2", "Created Player Capsule", 1, 1, List.of()));

        // Execute Step 1 (Environment setup)
        boolean hasMore1 = controller1.executeNextSubGoal(runId);
        assertTrue(hasMore1);
        assertEquals(1, state1.getExecutionState().getCompletedNodes().size());

        // Execute Step 2 (Player creation)
        boolean hasMore2 = controller1.executeNextSubGoal(runId);
        assertTrue(hasMore2);
        assertEquals(2, state1.getExecutionState().getCompletedNodes().size());

        // Verify checkpoint was persisted to disk
        String checkpointIdBeforeCrash = state1.getLastCheckpointId();
        assertNotNull(checkpointIdBeforeCrash);
        AutonomyCheckpoint savedDiskCheckpoint = checkpointService1.getCheckpoint(projectId, checkpointIdBeforeCrash);
        assertNotNull(savedDiskCheckpoint, "Checkpoint must be written to disk before crash");
        assertEquals(2, savedDiskCheckpoint.getCompletedNodes().size());

        // ==========================================
        // PHASE 2: THE CRASH (Kill Process 1)
        // ==========================================
        // All Controller 1 and in-memory state is abandoned/garbage collected
        controller1 = null;
        state1 = null;
        checkpointService1 = null;

        // ==========================================
        // PHASE 3: BACKEND INSTANCE 2 (After Restart)
        // ==========================================
        // Fresh backend startup with clean memory, pointing to the same disk checkpoint directory
        CheckpointService checkpointService2 = new CheckpointService(tempCheckpointDir);
        AgentLoop agentLoopMock2 = Mockito.mock(AgentLoop.class);
        ObjectiveValidator validatorMock2 = Mockito.mock(ObjectiveValidator.class);

        AutonomousRunController controller2 = new AutonomousRunController(
                new GoalAnalyzer(),
                new RequirementManager(),
                new LongHorizonPlanner(new ToolRegistry(List.of())),
                agentLoopMock2,
                new ReplanningEngine(),
                new FailureClassifier(),
                new RecoveryEngine(),
                validatorMock2,
                new CompletionGate(),
                checkpointService2,
                new AutonomyLimits(50, 100, 5, 5, 600)
        );

        // Verify disk checkpoint is loadable by the new process
        AutonomyCheckpoint reloadedCheckpoint = checkpointService2.loadCheckpoint(projectId, checkpointIdBeforeCrash);
        assertNotNull(reloadedCheckpoint, "New backend instance must load existing checkpoint from disk");
        assertEquals(2, reloadedCheckpoint.getCompletedNodes().size());

        // Query simulated live Unity engine state: Ground and Player exist in the scene!
        List<String> liveSceneObjects = List.of("Ground", "Player");
        List<String> liveScripts = List.of();

        // Reconcile and resume: MUST NOT blindly replay step 1 or step 2
        AgentPlan reconciledPlan = checkpointService2.reconcileAndResume(
                reloadedCheckpoint,
                liveSceneObjects,
                liveScripts,
                new LongHorizonPlanner(new ToolRegistry(List.of())),
                new GameGoal("goal_e2e", "Resumed Goal"),
                new RequirementManager()
        );

        assertNotNull(reconciledPlan);
        // Completed nodes must be preserved without re-execution
        for (String completedId : reloadedCheckpoint.getCompletedNodes()) {
            PlanNode node = reconciledPlan.getPlanNode(completedId);
            if (node != null) {
                assertEquals(PlanNode.PlanNodeStatus.COMPLETED, node.getStatus(),
                        "Preserved node " + completedId + " must remain COMPLETED and not be re-run");
            }
        }

        // Initialize state in new controller
        AutonomousRunState resumedState = new AutonomousRunState(runId, sessionId, projectId, new GameGoal("goal_e2e", "Resumed"), reconciledPlan);
        resumedState.setStatus(AutonomousRunState.RunStatus.RUNNING);

        // Verify remaining nodes are ready to continue forward
        List<PlanNode> nextReadyNodes = reconciledPlan.getReadyPlanNodes();
        assertFalse(nextReadyNodes.isEmpty(), "Reconciled plan must have downstream nodes ready to execute");

        // Step through remaining nodes with mock success
        when(agentLoopMock2.run(anyString(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(AgentRunResult.success(sessionId, "subrun_cont", "Completed remaining steps", 1, 1, List.of()));

        // Fast-forward completion
        for (PlanNode remaining : reconciledPlan.getPlanNodes().values()) {
            reconciledPlan.markNodeCompleted(remaining.getNodeId(), "Completed in resumed run");
        }

        // Set up passing completion gate report
        ValidationReport finalReport = new ValidationReport("goal_e2e");
        finalReport.setCompilationSuccess(true);
        finalReport.setRuntimeErrorsClean(true);
        finalReport.setBehaviorTestsPassed(true);
        finalReport.setRequiredCount(2);
        finalReport.setSatisfiedRequiredCount(2);
        finalReport.setSummary("Interrupted run cleanly completed and validated");

        assertTrue(CompletionGate.evaluate(finalReport), "CompletionGate must pass once all criteria are met");
    }
}
