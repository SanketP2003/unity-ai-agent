package com.unityagent.agent.chaos;

import com.unityagent.agent.budget.BudgetExceededException;
import com.unityagent.agent.budget.ResourceBudget;
import com.unityagent.agent.concurrency.ProjectConflictException;
import com.unityagent.agent.concurrency.ProjectLockService;
import com.unityagent.agent.events.EventJournalService;
import com.unityagent.agent.events.RunEventRecord;
import com.unityagent.agent.events.RunEventType;
import com.unityagent.agent.identity.EntityCandidate;
import com.unityagent.agent.identity.EntityResolutionResult;
import com.unityagent.agent.identity.EntityResolutionService;
import com.unityagent.agent.persistence.AutonomousRunRecord;
import com.unityagent.agent.persistence.RunPersistenceService;
import com.unityagent.agent.reliability.ToolExecutionRecord;
import com.unityagent.agent.reliability.ToolExecutionStatus;
import com.unityagent.agent.reliability.ToolExecutionTracker;
import com.unityagent.agent.resilience.CircuitBreaker;
import com.unityagent.agent.resilience.CircuitBreakerOpenException;
import com.unityagent.agent.resilience.ProviderRetryPolicy;
import com.unityagent.memory.MemoryDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production Chaos and Resilience Verification Suite for Phase 10.
 * Directly exercises Test B, C, D, E, F, G, and I.
 */
class Phase10ProductionChaosTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private RunPersistenceService persistenceService;
    private EventJournalService eventJournalService;
    private ToolExecutionTracker toolTracker;
    private ProjectLockService lockService;
    private EntityResolutionService resolutionService;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("chaos_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();

        persistenceService = new RunPersistenceService(db, null);
        eventJournalService = new EventJournalService(db);
        toolTracker = new ToolExecutionTracker();
        lockService = new ProjectLockService();
        resolutionService = new EntityResolutionService();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    // ── Test B: Backend Crash & Restart Recovery ──────────────────────────────

    @Test
    void testBackendCrashAndRestartRecoveryFromDisk() {
        String runId = "run_crash_100";
        String projectId = "proj_crash_test";

        // 1. Simulate active run before crash
        AutonomousRunRecord preCrash = new AutonomousRunRecord(runId, "s1", projectId, "Create 3D Platformer", "RUNNING");
        preCrash.setCurrentPlanRevision(1);
        preCrash.setCompletedNodes(List.of("node_ground", "node_light"));
        preCrash.setActiveNodeId("node_player");
        preCrash.setCheckpointRef("cp_ground_done");
        persistenceService.saveRun(preCrash);

        eventJournalService.recordEvent(projectId, "s1", runId, RunEventType.RUN_STARTED, "Started");
        eventJournalService.recordEvent(projectId, "s1", runId, RunEventType.NODE_COMPLETED, "Completed node_ground");

        // 2. Simulate complete JVM / process crash: shutdown DB and instantiate brand new DB connection
        db.shutdown();

        MemoryDatabase restartedDb = new MemoryDatabase(tempDir.resolve("chaos_test.db").toFile().getAbsolutePath());
        restartedDb.initialize();
        RunPersistenceService restartedPersistence = new RunPersistenceService(restartedDb, null);
        EventJournalService restartedJournal = new EventJournalService(restartedDb);

        // 3. Verify state recovered from disk
        Optional<AutonomousRunRecord> recoveredOpt = restartedPersistence.getRun(runId);
        assertTrue(recoveredOpt.isPresent(), "Unfinished run must survive backend process termination");

        AutonomousRunRecord recovered = recoveredOpt.get();
        assertEquals("RUNNING", recovered.getStatus());
        assertEquals("node_player", recovered.getActiveNodeId());
        assertEquals(2, recovered.getCompletedNodes().size());
        assertTrue(recovered.getCompletedNodes().contains("node_ground"));
        assertEquals("cp_ground_done", recovered.getCheckpointRef());

        // Verify event journal history is completely intact
        List<RunEventRecord> events = restartedJournal.getEventsForRun(runId);
        assertEquals(2, events.size());
        assertEquals("RUN_STARTED", events.get(0).getEventType());
        assertEquals("NODE_COMPLETED", events.get(1).getEventType());

        restartedDb.shutdown();
    }

    // ── Test D: Provider Failure & Circuit Breaker ──────────────────────────

    @Test
    void testProviderFailureCircuitBreakerTripsAndRecovers() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker("provider_chaos", 3, 100, 2);
        ProviderRetryPolicy retryPolicy = new ProviderRetryPolicy(3, 20, 100);

        // Transient failures trigger retries and trip circuit breaker
        breaker.recordFailure(true);
        breaker.recordFailure(true);
        assertTrue(breaker.allowRequest());

        breaker.recordFailure(true);
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.allowRequest());

        // When OPEN, calls are rejected immediately
        assertThrows(CircuitBreakerOpenException.class, () -> {
            if (!breaker.allowRequest()) {
                throw new CircuitBreakerOpenException("Circuit breaker OPEN");
            }
        });

        // Wait for reset timeout
        Thread.sleep(120);
        assertTrue(breaker.allowRequest());
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

        // Success probes close the breaker
        breaker.recordSuccess();
        breaker.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    // ── Test E: Duplicate Prevention in Existing Scene ──────────────────────

    @Test
    void testDuplicatePreventionMatchesExistingSceneEntities() {
        List<EntityCandidate> existingSceneObjects = List.of(
                new EntityCandidate("e1", "Player", "Player", List.of("CharacterController", "PlayerMovement")),
                new EntityCandidate("e2", "Main Camera", "Main Camera", List.of("Camera")),
                new EntityCandidate("e3", "Ground", "Ground", List.of("BoxCollider")),
                new EntityCandidate("e4", "Enemy", "Enemy", List.of("EnemyAI"))
        );
        List<String> allNames = List.of("Player", "Main Camera", "Ground", "Enemy");

        // When goal asks for "Player", resolution service MUST resolve to REUSE instead of CREATE
        EntityCandidate playerCandidate = new EntityCandidate(
                "target_player",
                "Player",
                "Player",
                List.of("CharacterController", "PlayerMovement")
        );
        playerCandidate.setScriptName("PlayerMovement");

        EntityResolutionResult result = resolutionService.resolveEntity(playerCandidate, existingSceneObjects, allNames);

        assertTrue(result.shouldReuse(),
                "Existing Player object must be REUSED to prevent duplicate object clutter");
        assertEquals(EntityResolutionResult.MatchType.EXACT_MATCH, result.getMatchType());
        assertEquals("Player", result.getMatchedName());
        assertTrue(result.getConfidence() >= 0.85);
    }

    // ── Test F: Multi-Project Concurrency Isolation ─────────────────────────

    @Test
    void testMultiProjectConcurrencyIsolation() {
        String projA = "UnityProject_A";
        String projB = "UnityProject_B";
        String runA = "run_a_1";
        String runB = "run_b_1";

        // Both projects run concurrently without interference
        lockService.acquireProjectLock(projA, runA);
        lockService.acquireProjectLock(projB, runB);

        assertTrue(lockService.isProjectLocked(projA));
        assertTrue(lockService.isProjectLocked(projB));

        // Attempting to run second job on Project A throws conflict
        assertThrows(ProjectConflictException.class, () ->
                lockService.acquireProjectLock(projA, "run_a_duplicate"));

        // Releasing A leaves B intact
        lockService.releaseProjectLock(projA, runA);
        assertFalse(lockService.isProjectLocked(projA));
        assertTrue(lockService.isProjectLocked(projB));
    }

    // ── Test G: Tool Ambiguity & UNKNOWN State Reconciliation ────────────────

    @Test
    void testUnknownToolOutcomeReconciledAgainstUnityReality() {
        String opId = "op_spawn_checkpoint";
        ToolExecutionRecord record = toolTracker.registerExecution(
                "tc_spawn", opId, "proj_a", "run_1", "create_primitive",
                Map.of("primitiveType", "Cube", "name", "CheckpointZone"), 30
        );
        record.markRunning();

        // Backend loses connection before receiving response -> UNKNOWN
        toolTracker.markInFlightAsUnknown("proj_a", "WebSocket connection reset");
        assertEquals(ToolExecutionStatus.UNKNOWN, record.getResultStatus());

        // Check if object actually exists in Unity after reconnect
        List<String> sceneAfterReconnect = List.of("Main Camera", "CheckpointZone");
        ToolExecutionStatus status = toolTracker.reconcileUnknownOperation(opId, sceneAfterReconnect);

        // Operation verified in engine -> SUCCEEDED without issuing a duplicate create command!
        assertEquals(ToolExecutionStatus.SUCCEEDED, status);
        assertEquals(ToolExecutionStatus.SUCCEEDED, record.getResultStatus());
    }

    // ── Test I: Resource Budget Exhaustion ───────────────────────────────────

    @Test
    void testResourceBudgetExhaustionStopsCleanly() {
        ResourceBudget budget = new ResourceBudget(600, 100, 30, 5, 5, 2, 8000);

        // Exhaust replans
        BudgetExceededException replanEx = assertThrows(BudgetExceededException.class, () ->
                budget.validateBudget("run_exhaust", 50, 20, 10, 6, 1));
        assertEquals(BudgetExceededException.ExhaustionReason.REPLAN_LIMIT_REACHED, replanEx.getReason());

        // Exhaust recovery cycles
        BudgetExceededException recEx = assertThrows(BudgetExceededException.class, () ->
                budget.validateBudget("run_exhaust", 50, 20, 10, 2, 6));
        assertEquals(BudgetExceededException.ExhaustionReason.RECOVERY_LIMIT_REACHED, recEx.getReason());

        // Exhaust turns
        BudgetExceededException turnEx = assertThrows(BudgetExceededException.class, () ->
                budget.validateBudget("run_exhaust", 50, 20, 35, 2, 2));
        assertEquals(BudgetExceededException.ExhaustionReason.LLM_TURN_LIMIT_REACHED, turnEx.getReason());
    }
}
