package com.unityagent.agent.reliability;

import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.checkpoint.AutonomyCheckpoint;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.events.EventJournalService;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.persistence.AutonomousRunRecord;
import com.unityagent.agent.persistence.RunPersistenceService;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.plan.PlanNode;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.unity.UnityConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AutonomousRunRecoveryTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private RunPersistenceService persistenceService;
    private EventJournalService eventJournalService;
    private CheckpointService checkpointService;
    private AutonomousRunController runController;
    private UnityConnection unityConnection;
    private AIProvider aiProvider;
    private AutonomousRunRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("recovery_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();

        persistenceService = new RunPersistenceService(db, null);
        eventJournalService = new EventJournalService(db);
        checkpointService = new CheckpointService(tempDir.resolve("checkpoints"));
        runController = Mockito.mock(AutonomousRunController.class);
        unityConnection = Mockito.mock(UnityConnection.class);
        aiProvider = Mockito.mock(AIProvider.class);

        recoveryService = new AutonomousRunRecoveryService(
                persistenceService, eventJournalService, checkpointService,
                runController, unityConnection, aiProvider
        );
    }

    @Test
    void testWaitingForProviderWhenAiProviderNotConfigured() {
        when(aiProvider.isConfigured()).thenReturn(false);

        AutonomousRunRecord run = new AutonomousRunRecord("run_prov_1", "s1", "proj_1", "Build Game", "RUNNING");
        persistenceService.saveRun(run);

        List<AutonomousRunRecord> recovered = recoveryService.recoverOnStartup();
        assertEquals(1, recovered.size());
        assertEquals("WAITING_FOR_PROVIDER", recovered.get(0).getStatus());

        AutonomousRunRecord inDb = persistenceService.getRun("run_prov_1").orElseThrow();
        assertEquals("WAITING_FOR_PROVIDER", inDb.getStatus());
    }

    @Test
    void testWaitingForUnityWhenUnityNotConnected() {
        when(aiProvider.isConfigured()).thenReturn(true);
        when(unityConnection.isReady()).thenReturn(false);

        AutonomousRunRecord run = new AutonomousRunRecord("run_unity_1", "s1", "proj_2", "Build Game", "RUNNING");
        persistenceService.saveRun(run);

        List<AutonomousRunRecord> recovered = recoveryService.recoverOnStartup();
        assertEquals(1, recovered.size());
        assertEquals("WAITING_FOR_UNITY", recovered.get(0).getStatus());

        AutonomousRunRecord inDb = persistenceService.getRun("run_unity_1").orElseThrow();
        assertEquals("WAITING_FOR_UNITY", inDb.getStatus());
    }

    @Test
    void testReconcileAndResumeWhenUnityConnects() {
        String projectId = "proj_platformer";
        String runId = "run_reconcile_1";

        // Create checkpoint on disk
        AutonomyCheckpoint cp = new AutonomyCheckpoint(
                "cp_step_1", projectId, runId, "plan_1", 1,
                List.of("node_ground"), "node_player", Map.of(), Map.of()
        );
        checkpointService.saveCheckpoint(cp);

        AutonomousRunRecord record = new AutonomousRunRecord(runId, "s1", projectId, "Create Platformer", "WAITING_FOR_UNITY");
        record.setCheckpointRef("cp_step_1");
        persistenceService.saveRun(record);

        // Mock runController resume
        GameGoal goal = new GameGoal("g1", "Create Platformer");
        AgentPlan plan = new AgentPlan();
        plan.addPlanNode(new PlanNode("node_ground", "Create Ground", PlanNode.PlanActionType.CREATE, List.of()));
        plan.addPlanNode(new PlanNode("node_player", "Create Player", PlanNode.PlanActionType.CREATE, List.of()));
        AutonomousRunState runState = new AutonomousRunState(runId, "s1", projectId, goal, plan);
        when(runController.resumeRun(eq(runId), eq("cp_step_1"), any())).thenReturn(runState);

        // Resume with live scene objects
        AutonomousRunState resumed = recoveryService.reconcileAndResumeRun(runId, List.of("Ground"));
        assertNotNull(resumed);
        assertEquals(runId, resumed.getRunId());

        AutonomousRunRecord updatedInDb = persistenceService.getRun(runId).orElseThrow();
        assertEquals("RUNNING", updatedInDb.getStatus());
    }
}
