package com.unityagent.product.service;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemorySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;

/**
 * Service managing safe database upgrades and migrations.
 * Automatically creates pre-migration backups and rolls back on failure.
 */
@Service
public class MigrationManager {

    private static final Logger log = LoggerFactory.getLogger(MigrationManager.class);
    private final MemoryDatabase memoryDb;

    public MigrationManager(MemoryDatabase memoryDb) {
        this.memoryDb = memoryDb;
    }

    public int getCurrentVersion() {
        String sql = "SELECT MAX(version) AS cur_ver FROM schema_version";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int ver = rs.getInt("cur_ver");
                return ver > 0 ? ver : 1;
            }
        } catch (Exception e) {
            log.debug("No schema_version table found, assuming base version 1: {}", e.getMessage());
        }
        return 1;
    }

    /**
     * Executes safe database migration to target version.
     * Backs up DB before migration and verifies integrity afterwards.
     */
    public boolean migrateToVersion(int targetVersion) {
        int current = getCurrentVersion();
        if (current >= targetVersion) {
            log.info("Database schema is already at or above target version: current={}, target={}", current, targetVersion);
            return true;
        }

        log.info("Migrating database from v{} to v{}", current, targetVersion);

        // 1. Create pre-migration backup if file exists
        Path dbPath = Paths.get(memoryDb.getDatabasePath());
        Path backupPath = null;
        if (Files.exists(dbPath)) {
            try {
                backupPath = Paths.get(memoryDb.getDatabasePath() + ".migration_bak_" + System.currentTimeMillis());
                Files.copy(dbPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Created pre-migration database snapshot at {}", backupPath);
            } catch (Exception e) {
                log.warn("Could not create file snapshot of database: {}", e.getMessage());
            }
        }

        // 2. Apply migration DDLs
        try (Connection conn = memoryDb.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String ddl : MemorySchema.ALL_TABLES) {
                stmt.execute(ddl);
            }
            for (String idx : MemorySchema.CREATE_INDEXES) {
                stmt.execute(idx);
            }

            stmt.execute("INSERT OR REPLACE INTO schema_version (version, applied_at) VALUES (" +
                         targetVersion + ", '" + Instant.now() + "')");

            // 3. Post-migration integrity check
            try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                if (rs.next()) {
                    String result = rs.getString(1);
                    if (!"ok".equalsIgnoreCase(result)) {
                        throw new IllegalStateException("Integrity check failed post-migration: " + result);
                    }
                }
            }

            log.info("Migration to v{} completed successfully and integrity verified", targetVersion);
            return true;
        } catch (Exception e) {
            log.error("Migration to v{} FAILED: {}", targetVersion, e.getMessage(), e);

            // Rollback from backup snapshot if available
            if (backupPath != null && Files.exists(backupPath)) {
                try {
                    Files.copy(backupPath, dbPath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Rolled back database to pre-migration snapshot after failure");
                } catch (Exception rbe) {
                    log.error("CRITICAL: Failed to restore backup after migration failure: {}", rbe.getMessage());
                }
            }
            return false;
        } finally {
            // Clean up temporary backup snapshot if migration succeeded
            if (backupPath != null) {
                try {
                    Files.deleteIfExists(backupPath);
                } catch (Exception ignored) {}
            }
        }
    }
}
