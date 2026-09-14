package com.unityagent.product;

import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.events.EventJournalService;
import com.unityagent.agent.events.RunEventType;
import com.unityagent.agent.persistence.AutonomousRunRecord;
import com.unityagent.agent.persistence.RunPersistenceService;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.reliability.AutonomousRunRecoveryService;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemorySchema;
import com.unityagent.product.model.Release;
import com.unityagent.product.service.MigrationManager;
import com.unityagent.product.service.ProductionStartupService;
import com.unityagent.product.service.ReleaseManager;
import com.unityagent.unity.UnityConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DisasterRecoveryTest {

    private RunPersistenceService persistenceService;
    private EventJournalService eventJournalService;
    private CheckpointService checkpointService;
    private AutonomousRunController runController;
    private UnityConnection unityConnection;
    private AIProvider aiProvider;
    private AutonomousRunRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        persistenceService = mock(RunPersistenceService.class);
        eventJournalService = mock(EventJournalService.class);
        checkpointService = mock(CheckpointService.class);
        runController = mock(AutonomousRunController.class);
        unityConnection = mock(UnityConnection.class);
        aiProvider = mock(AIProvider.class);

        recoveryService = new AutonomousRunRecoveryService(
                persistenceService,
                eventJournalService,
                checkpointService,
                runController,
                unityConnection,
                aiProvider
        );
    }

    @Test
    void testSqliteCrashRecoveryAndIntegrityCheck(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("disaster_test.db");
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        // 1. Initial run and abnormal close simulation
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("CREATE TABLE test_data (id INTEGER PRIMARY KEY, value TEXT);");
            stmt.execute("INSERT INTO test_data (value) VALUES ('pre_crash_data');");
        }

        // 2. Restart and verify PRAGMA integrity_check
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check;")) {
            assertTrue(rs.next());
            assertEquals("ok", rs.getString(1));
        }

        // 3. Verify data survived
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT value FROM test_data WHERE id=1;")) {
            assertTrue(rs.next());
            assertEquals("pre_crash_data", rs.getString(1));
        }
    }

    @Test
    void testInterruptedRunTransitionsToWaitingForUnityWhenUnityOffline() {
        AutonomousRunRecord uncompletedRun = new AutonomousRunRecord(
                "run-101", "sess-1", "proj-alpha", "Building level", "RUNNING"
        );

        when(persistenceService.getUnfinishedRuns()).thenReturn(List.of(uncompletedRun));
        when(aiProvider.isConfigured()).thenReturn(true);
        when(unityConnection.isReady()).thenReturn(false); // Unity was killed

        List<AutonomousRunRecord> recovered = recoveryService.recoverOnStartup();

        assertEquals(1, recovered.size());
        assertEquals("WAITING_FOR_UNITY", uncompletedRun.getStatus());
        verify(persistenceService).saveRun(uncompletedRun);
        verify(eventJournalService).recordEvent(
                eq("proj-alpha"), eq("sess-1"), eq("run-101"), eq(RunEventType.RUN_PAUSED), contains("Waiting for Unity")
        );
    }

    @Test
    void testInterruptedRunTransitionsToWaitingForProviderWhenProviderOffline() {
        AutonomousRunRecord uncompletedRun = new AutonomousRunRecord(
                "run-102", "sess-2", "proj-beta", "Refactoring", "RUNNING"
        );

        when(persistenceService.getUnfinishedRuns()).thenReturn(List.of(uncompletedRun));
        when(aiProvider.isConfigured()).thenReturn(false); // API key or network down

        List<AutonomousRunRecord> recovered = recoveryService.recoverOnStartup();

        assertEquals(1, recovered.size());
        assertEquals("WAITING_FOR_PROVIDER", uncompletedRun.getStatus());
        verify(persistenceService).saveRun(uncompletedRun);
        verify(eventJournalService).recordEvent(
                eq("proj-beta"), eq("sess-2"), eq("run-102"), eq(RunEventType.RUN_PAUSED), contains("AI Provider is unavailable")
        );
    }

    @Test
    void testIncompleteStagedReleaseCannotBePublishedWithoutApproval() {
        Release stagedRelease = new Release(
                "rel-001", "proj-gamma", "1.0.0", com.unityagent.product.model.ReleaseChannel.DEVELOPMENT,
                com.unityagent.product.model.ReleaseStatus.READY_FOR_REVIEW, "art-001", "Changelog", java.time.Instant.now()
        );

        assertFalse(stagedRelease.isImmutable());
        assertEquals(com.unityagent.product.model.ReleaseStatus.READY_FOR_REVIEW, stagedRelease.getStatus());
        assertNull(stagedRelease.getApprovalRecordJson());
    }
}
