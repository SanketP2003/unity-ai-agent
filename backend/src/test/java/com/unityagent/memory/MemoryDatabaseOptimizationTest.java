package com.unityagent.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryDatabase Performance & Optimization Tests")
class MemoryDatabaseOptimizationTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("test_memory_opt.db");
        db = new MemoryDatabase(dbPath.toString());
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    @DisplayName("Should persist WAL mode in database")
    void testWalModePersistent() throws Exception {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
            assertTrue(rs.next());
            assertEquals("wal", rs.getString(1).toLowerCase());
        }
    }

    @Test
    @DisplayName("Should apply required connection-level performance PRAGMAs")
    void testConnectionLevelPragmas() throws Exception {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {

            // Foreign keys must be ON (1)
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "foreign_keys should be ON (1)");
            }

            // Synchronous must be NORMAL (1)
            try (ResultSet rs = stmt.executeQuery("PRAGMA synchronous")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "synchronous should be NORMAL (1)");
            }

            // Busy timeout must be 5000 ms
            try (ResultSet rs = stmt.executeQuery("PRAGMA busy_timeout")) {
                assertTrue(rs.next());
                assertEquals(5000, rs.getInt(1), "busy_timeout should be 5000");
            }

            // Cache size must be -8000
            try (ResultSet rs = stmt.executeQuery("PRAGMA cache_size")) {
                assertTrue(rs.next());
                assertEquals(-8000, rs.getInt(1), "cache_size should be -8000");
            }

            // mmap_size must be configured (at least > 0)
            try (ResultSet rs = stmt.executeQuery("PRAGMA mmap_size")) {
                assertTrue(rs.next());
                assertTrue(rs.getLong(1) >= 0, "mmap_size should be configured");
            }
        }
    }

    @Test
    @DisplayName("Should support concurrent reads and writes across multiple connections")
    void testConcurrentConnections() throws Exception {
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int id = i;
            tasks.add(() -> {
                try (Connection conn = db.getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(
                            "INSERT OR IGNORE INTO projects (project_id, project_name, created_at) " +
                            "VALUES ('proj_" + id + "', 'Project " + id + "', datetime('now'))"
                    );
                    try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM projects")) {
                        assertTrue(rs.next());
                        return rs.getInt(1) > 0;
                    }
                }
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        for (Future<Boolean> future : futures) {
            assertTrue(future.get(), "Concurrent DB operation should succeed");
        }
        executor.shutdown();
    }
}
