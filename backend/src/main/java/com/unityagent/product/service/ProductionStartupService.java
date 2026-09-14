package com.unityagent.product.service;

import com.unityagent.agent.reliability.AutonomousRunRecoveryService;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemorySchema;
import com.unityagent.unity.UnityConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Service orchestrating production startup sequence and health validation.
 */
@Service
public class ProductionStartupService {

    private static final Logger log = LoggerFactory.getLogger(ProductionStartupService.class);

    private final MemoryDatabase memoryDb;
    private final MigrationManager migrationManager;
    private final AutonomousRunRecoveryService recoveryService;
    private final UnityConnection unityConnection;

    private boolean ready = false;
    private boolean degraded = false;
    private String statusMessage = "INITIALIZING";

    public ProductionStartupService(MemoryDatabase memoryDb,
                                    MigrationManager migrationManager,
                                    AutonomousRunRecoveryService recoveryService,
                                    UnityConnection unityConnection) {
        this.memoryDb = memoryDb;
        this.migrationManager = migrationManager;
        this.recoveryService = recoveryService;
        this.unityConnection = unityConnection;
    }

    @PostConstruct
    public void executeStartupSequence() {
        log.info("Starting Production Startup Sequence...");
        try {
            // 1. Verify database connectivity
            try (Connection conn = memoryDb.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                if (rs.next() && !"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new IllegalStateException("Database integrity check failed on boot: " + rs.getString(1));
                }
            }
            log.info("Production Startup: Database connectivity and integrity verified.");

            // 2. Run migrations
            boolean migrated = migrationManager.migrateToVersion(MemorySchema.CURRENT_VERSION);
            if (!migrated) {
                degraded = true;
                statusMessage = "DEGRADED: Database migration incomplete";
                log.warn("Production Startup: {}", statusMessage);
                return;
            }
            log.info("Production Startup: Database migration to v{} confirmed.", MemorySchema.CURRENT_VERSION);

            // 3. Crash recovery for unfinished runs
            int recovered = recoveryService.recoverOnStartup().size();
            log.info("Production Startup: Recovered {} unfinished autonomous runs.", recovered);

            // 4. Check Unity readiness
            if (unityConnection != null && unityConnection.isReady()) {
                ready = true;
                statusMessage = "READY";
            } else {
                ready = true;
                degraded = true;
                statusMessage = "READY (Unity bridge awaiting connection)";
            }

            log.info("Production Startup Complete: status={}", statusMessage);
        } catch (Exception e) {
            degraded = true;
            statusMessage = "STARTUP_ERROR: " + e.getMessage();
            log.error("Production Startup encountered critical error: {}", e.getMessage(), e);
        }
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}
