package com.unityagent.agent.persistence;

import com.unityagent.agent.events.EventJournalService;
import com.unityagent.agent.events.RunEventRecord;
import com.unityagent.agent.events.RunEventType;
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

class RunPersistenceTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private RunPersistenceService persistenceService;
    private EventJournalService eventJournalService;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("test_memory.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        persistenceService = new RunPersistenceService(db, null);
        eventJournalService = new EventJournalService(db);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testSaveAndLoadRun() {
        AutonomousRunRecord record = new AutonomousRunRecord(
                "run_1001", "sess_1", "proj_alpha", "Build a 3D Platformer", "RUNNING"
        );
        record.setCurrentPlanRevision(2);
        record.setActiveNodeId("node_ground");
        record.setCompletedNodes(List.of("node_camera", "node_light"));
        record.setFailedNodes(List.of("node_enemy"));
        record.setToolCallCount(15);
        record.setRequirementStates(Map.of("req_player", "SATISFIED", "req_ground", "IN_PROGRESS"));

        persistenceService.saveRun(record);

        Optional<AutonomousRunRecord> loadedOpt = persistenceService.getRun("run_1001");
        assertTrue(loadedOpt.isPresent());
        AutonomousRunRecord loaded = loadedOpt.get();
        assertEquals("run_1001", loaded.getRunId());
        assertEquals("proj_alpha", loaded.getProjectId());
        assertEquals("Build a 3D Platformer", loaded.getGoalText());
        assertEquals("RUNNING", loaded.getStatus());
        assertEquals(2, loaded.getCurrentPlanRevision());
        assertEquals("node_ground", loaded.getActiveNodeId());
        assertEquals(2, loaded.getCompletedNodes().size());
        assertTrue(loaded.getCompletedNodes().contains("node_camera"));
        assertEquals(1, loaded.getFailedNodes().size());
        assertEquals(15, loaded.getToolCallCount());
        assertEquals("SATISFIED", loaded.getRequirementStates().get("req_player"));
    }

    @Test
    void testGetUnfinishedRuns() {
        AutonomousRunRecord run1 = new AutonomousRunRecord("run_1", "s1", "p1", "Goal 1", "RUNNING");
        AutonomousRunRecord run2 = new AutonomousRunRecord("run_2", "s2", "p1", "Goal 2", "COMPLETED");
        AutonomousRunRecord run3 = new AutonomousRunRecord("run_3", "s3", "p2", "Goal 3", "WAITING_FOR_UNITY");

        persistenceService.saveRun(run1);
        persistenceService.saveRun(run2);
        persistenceService.saveRun(run3);

        List<AutonomousRunRecord> unfinished = persistenceService.getUnfinishedRuns();
        assertEquals(2, unfinished.size());
        List<String> ids = unfinished.stream().map(AutonomousRunRecord::getRunId).toList();
        assertTrue(ids.contains("run_1"));
        assertTrue(ids.contains("run_3"));
        assertFalse(ids.contains("run_2"));
    }

    @Test
    void testEventJournalMonotonicSequence() {
        String runId = "run_event_test";
        RunEventRecord e1 = eventJournalService.recordEvent("p1", "s1", runId, RunEventType.RUN_CREATED, "init");
        RunEventRecord e2 = eventJournalService.recordEvent("p1", "s1", runId, RunEventType.RUN_STARTED, "start");
        RunEventRecord e3 = eventJournalService.recordEvent("p1", "s1", runId, RunEventType.NODE_STARTED, "node_1");

        assertEquals(1, e1.getSequence());
        assertEquals(2, e2.getSequence());
        assertEquals(3, e3.getSequence());

        List<RunEventRecord> allEvents = eventJournalService.getEventsForRun(runId);
        assertEquals(3, allEvents.size());
        assertEquals("RUN_CREATED", allEvents.get(0).getEventType());
        assertEquals("RUN_STARTED", allEvents.get(1).getEventType());
        assertEquals("NODE_STARTED", allEvents.get(2).getEventType());

        List<RunEventRecord> sinceEvents = eventJournalService.getEventsSince(runId, 1);
        assertEquals(2, sinceEvents.size());
        assertEquals(2, sinceEvents.get(0).getSequence());
    }

    @Test
    void testEventPayloadTruncation() {
        String longPayload = "A".repeat(70000);
        RunEventRecord e = eventJournalService.recordEvent("p1", "s1", "run_payload", RunEventType.TOOL_COMPLETED, longPayload);

        assertTrue(e.getPayload().length() <= RunEventRecord.MAX_PAYLOAD_BYTES);
        assertTrue(e.getPayload().endsWith("[PAYLOAD_TRUNCATED]"));
    }
}
