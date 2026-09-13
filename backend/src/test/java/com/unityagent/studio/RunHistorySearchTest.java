package com.unityagent.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.persistence.AutonomousRunRecord;
import com.unityagent.agent.persistence.RunPersistenceService;
import com.unityagent.memory.MemoryDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RunHistorySearchTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private RunPersistenceService persistenceService;

    @BeforeEach
    void setUp() throws Exception {
        memoryDb = new MemoryDatabase(tempDir.resolve("history_test.db").toString());
        memoryDb.initialize();

        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO projects (project_id, project_name, created_at) VALUES (?, ?, datetime('now'))")) {
            stmt.setString(1, "proj_hist_1");
            stmt.setString(2, "History Project 1");
            stmt.executeUpdate();

            stmt.setString(1, "proj_hist_2");
            stmt.setString(2, "History Project 2");
            stmt.executeUpdate();
        }

        persistenceService = new RunPersistenceService(memoryDb, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (memoryDb != null) {
            memoryDb.shutdown();
        }
    }

    @Test
    @DisplayName("Should isolate and query run records by project")
    void testProjectRunIsolation() {
        AutonomousRunRecord r1 = new AutonomousRunRecord("run_h1", "sess_1", "proj_hist_1", "Build Game 1", "COMPLETED");
        AutonomousRunRecord r2 = new AutonomousRunRecord("run_h2", "sess_2", "proj_hist_1", "Add Sound", "RUNNING");
        AutonomousRunRecord r3 = new AutonomousRunRecord("run_h3", "sess_3", "proj_hist_2", "Build Game 2", "COMPLETED");

        persistenceService.saveRun(r1);
        persistenceService.saveRun(r2);
        persistenceService.saveRun(r3);

        List<AutonomousRunRecord> p1Runs = persistenceService.getRunsByProject("proj_hist_1");
        assertEquals(2, p1Runs.size());
        assertTrue(p1Runs.stream().allMatch(r -> r.getProjectId().equals("proj_hist_1")));

        List<AutonomousRunRecord> p2Runs = persistenceService.getRunsByProject("proj_hist_2");
        assertEquals(1, p2Runs.size());
        assertEquals("run_h3", p2Runs.get(0).getRunId());
    }

    @Test
    @DisplayName("Should query specific run and preserve immutable history")
    void testGetRunImmutability() {
        AutonomousRunRecord r = new AutonomousRunRecord("run_immut", "sess_immut", "proj_hist_1", "Immutable Goal", "COMPLETED");
        r.setToolCallCount(15);
        persistenceService.saveRun(r);

        Optional<AutonomousRunRecord> loaded = persistenceService.getRun("run_immut");
        assertTrue(loaded.isPresent());
        assertEquals(15, loaded.get().getToolCallCount());
        assertEquals("Immutable Goal", loaded.get().getGoalText());
    }
}
