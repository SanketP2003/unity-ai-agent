package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.service.MigrationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Database Migration & Schema Integrity Tests")
class MigrationTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MigrationManager migrationManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("migration_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        migrationManager = new MigrationManager(db);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    @DisplayName("Verify current schema version is 4 after initialization")
    void testCurrentSchemaVersion() {
        int ver = migrationManager.getCurrentVersion();
        assertEquals(4, ver, "Schema version must be 4 after full initialization");
        assertTrue(migrationManager.migrateToVersion(4), "Migrating to current version should succeed");
    }

    @Test
    @DisplayName("Verify all product & distribution schema tables exist in SQLite")
    void testSchemaTablesExist() throws Exception {
        Set<String> requiredTables = Set.of(
                "projects",
                "game_releases",
                "build_artifacts",
                "backups",
                "workspaces",
                "workspace_projects",
                "project_templates",
                "configuration_profiles",
                "deployment_records"
        );

        Set<String> actualTables = new HashSet<>();
        try (Connection conn = db.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    actualTables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
        }

        for (String expected : requiredTables) {
            assertTrue(actualTables.contains(expected.toLowerCase()),
                    "Database must contain table: " + expected);
        }
    }
}
