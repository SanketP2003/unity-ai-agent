package com.unityagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database lifecycle: creation, schema initialization,
 * connection pooling, and shutdown.
 *
 * <p>Database is stored at ~/.unityagent/memory.db by default.
 * Directory is auto-created if missing.
 */
@Component
public class MemoryDatabase {

    private static final Logger log = LoggerFactory.getLogger(MemoryDatabase.class);

    @Value("${memory.database-path:#{systemProperties['user.home'] + '/.unityagent/memory.db'}}")
    private String databasePath;

    private String jdbcUrl;

    public MemoryDatabase() {}

    public MemoryDatabase(String databasePath) {
        this.databasePath = databasePath;
    }

    @PostConstruct
    public void initialize() {
        try {
            // Ensure parent directory exists
            File dbFile = new File(databasePath);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (created) {
                    log.info("Created memory database directory: {}", parentDir.getAbsolutePath());
                }
            }

            jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            log.info("Memory database path: {}", dbFile.getAbsolutePath());

            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Initialize schema
            initializeSchema();

            log.info("Memory database initialized successfully (schema v{})", MemorySchema.CURRENT_VERSION);
        } catch (Exception e) {
            log.error("Failed to initialize memory database: {}", e.getMessage(), e);
            throw new RuntimeException("Memory database initialization failed", e);
        }
    }

    /**
     * Get a new database connection.
     * Caller is responsible for closing the connection.
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        // Enable WAL mode for better concurrent read performance
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
        return conn;
    }

    private void initializeSchema() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Create all tables
            for (String ddl : MemorySchema.ALL_TABLES) {
                stmt.execute(ddl);
            }

            // Create indexes
            for (String idx : MemorySchema.CREATE_INDEXES) {
                stmt.execute(idx);
            }

            // Record schema version
            stmt.execute("INSERT OR IGNORE INTO schema_version (version, applied_at) VALUES ("
                    + MemorySchema.CURRENT_VERSION + ", datetime('now'))");

            log.debug("Schema v{} applied", MemorySchema.CURRENT_VERSION);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Memory database shutting down");
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }
}
