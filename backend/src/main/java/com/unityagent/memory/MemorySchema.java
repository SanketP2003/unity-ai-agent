package com.unityagent.memory;

/**
 * SQL DDL and DML constants for the persistent memory database.
 * All tables include project_id for multi-project isolation.
 * Schema version is tracked for future migrations.
 */
public final class MemorySchema {

    private MemorySchema() {}

    public static final int CURRENT_VERSION = 1;

    // ── Schema versioning ──────────────────────────────────────────────

    public static final String CREATE_SCHEMA_VERSION = """
            CREATE TABLE IF NOT EXISTS schema_version (
                version INTEGER PRIMARY KEY,
                applied_at TEXT NOT NULL
            )
            """;

    // ── Projects ───────────────────────────────────────────────────────

    public static final String CREATE_PROJECTS = """
            CREATE TABLE IF NOT EXISTS projects (
                project_id TEXT PRIMARY KEY,
                project_name TEXT,
                unity_version TEXT,
                platform TEXT,
                render_pipeline TEXT,
                extension_version TEXT,
                project_fingerprint TEXT,
                created_at TEXT NOT NULL,
                last_connected_at TEXT,
                last_agent_run_id TEXT
            )
            """;

    // ── Conversations ──────────────────────────────────────────────────

    public static final String CREATE_CONVERSATIONS = """
            CREATE TABLE IF NOT EXISTS conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                project_id TEXT NOT NULL,
                agent_run_id TEXT,
                user_goal TEXT,
                status TEXT,
                tool_count INTEGER DEFAULT 0,
                summary TEXT,
                created_at TEXT NOT NULL,
                completed_at TEXT,
                FOREIGN KEY (project_id) REFERENCES projects(project_id)
            )
            """;

    // ── Structured memory entries ──────────────────────────────────────

    public static final String CREATE_MEMORY_ENTRIES = """
            CREATE TABLE IF NOT EXISTS memory_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_id TEXT NOT NULL,
                category TEXT NOT NULL,
                key TEXT NOT NULL,
                value TEXT,
                source TEXT,
                confidence REAL DEFAULT 1.0,
                created_at TEXT NOT NULL,
                updated_at TEXT,
                last_verified_at TEXT,
                FOREIGN KEY (project_id) REFERENCES projects(project_id)
            )
            """;

    // ── Scripts ────────────────────────────────────────────────────────

    public static final String CREATE_SCRIPTS = """
            CREATE TABLE IF NOT EXISTS scripts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_id TEXT NOT NULL,
                script_path TEXT NOT NULL,
                class_name TEXT,
                content_hash TEXT,
                attached_objects TEXT,
                dependencies TEXT,
                last_modified_at TEXT,
                source TEXT,
                confidence REAL DEFAULT 1.0,
                FOREIGN KEY (project_id) REFERENCES projects(project_id),
                UNIQUE(project_id, script_path)
            )
            """;

    // ── Assets ─────────────────────────────────────────────────────────

    public static final String CREATE_ASSETS = """
            CREATE TABLE IF NOT EXISTS assets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_id TEXT NOT NULL,
                asset_path TEXT NOT NULL,
                asset_type TEXT NOT NULL,
                asset_guid TEXT,
                metadata TEXT,
                last_modified_at TEXT,
                FOREIGN KEY (project_id) REFERENCES projects(project_id),
                UNIQUE(project_id, asset_path)
            )
            """;

    // ── Architecture snapshots ─────────────────────────────────────────

    public static final String CREATE_ARCHITECTURE_SNAPSHOTS = """
            CREATE TABLE IF NOT EXISTS architecture_snapshots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_id TEXT NOT NULL,
                snapshot_version INTEGER NOT NULL,
                architecture_json TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY (project_id) REFERENCES projects(project_id),
                UNIQUE(project_id, snapshot_version)
            )
            """;

    // ── User preferences ───────────────────────────────────────────────

    public static final String CREATE_USER_PREFERENCES = """
            CREATE TABLE IF NOT EXISTS user_preferences (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_id TEXT,
                preference_key TEXT NOT NULL,
                preference_value TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT
            )
            """;

    // ── Indexes ────────────────────────────────────────────────────────

    public static final String[] CREATE_INDEXES = {
        "CREATE INDEX IF NOT EXISTS idx_conversations_project ON conversations(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_conversations_session ON conversations(session_id)",
        "CREATE INDEX IF NOT EXISTS idx_memory_project_category ON memory_entries(project_id, category)",
        "CREATE INDEX IF NOT EXISTS idx_memory_project_key ON memory_entries(project_id, key)",
        "CREATE INDEX IF NOT EXISTS idx_scripts_project ON scripts(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_assets_project ON assets(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_arch_project ON architecture_snapshots(project_id)"
    };

    /** All table DDL statements in order. */
    public static final String[] ALL_TABLES = {
        CREATE_SCHEMA_VERSION,
        CREATE_PROJECTS,
        CREATE_CONVERSATIONS,
        CREATE_MEMORY_ENTRIES,
        CREATE_SCRIPTS,
        CREATE_ASSETS,
        CREATE_ARCHITECTURE_SNAPSHOTS,
        CREATE_USER_PREFERENCES
    };
}
