package com.unityagent.product;

import com.unityagent.agent.reliability.AutonomousRunRecoveryService;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.service.MigrationManager;
import com.unityagent.product.service.ProductionStartupService;
import com.unityagent.unity.UnityConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Production Startup Sequence & Integrity Tests")
class ProductionStartupTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MigrationManager migrationManager;
    private AutonomousRunRecoveryService recoveryService;
    private UnityConnection unityConnection;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("startup_test_" + UUID.randomUUID() + ".db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();

        migrationManager = new MigrationManager(db);
        recoveryService = Mockito.mock(AutonomousRunRecoveryService.class);
        when(recoveryService.recoverOnStartup()).thenReturn(Collections.emptyList());

        unityConnection = Mockito.mock(UnityConnection.class);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    @DisplayName("Verify startup sequence completes successfully when Unity is connected")
    void testStartupSequenceFullyReady() {
        when(unityConnection.isReady()).thenReturn(true);

        ProductionStartupService service = new ProductionStartupService(
                db, migrationManager, recoveryService, unityConnection
        );

        service.executeStartupSequence();

        assertTrue(service.isReady(), "Startup service must report ready");
        assertFalse(service.isDegraded(), "Startup service must not be degraded when Unity is connected");
        assertEquals("READY", service.getStatusMessage());
    }

    @Test
    @DisplayName("Verify startup sequence completes with degraded state when Unity bridge is awaiting connection")
    void testStartupSequenceUnityAwaiting() {
        when(unityConnection.isReady()).thenReturn(false);

        ProductionStartupService service = new ProductionStartupService(
                db, migrationManager, recoveryService, unityConnection
        );

        service.executeStartupSequence();

        assertTrue(service.isReady(), "Backend must still report ready to accept requests");
        assertTrue(service.isDegraded(), "Service is marked degraded while awaiting Unity bridge");
        assertTrue(service.getStatusMessage().contains("awaiting connection"));
    }
}
