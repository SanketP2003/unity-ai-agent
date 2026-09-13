package com.unityagent.memory;

/**
 * SQL DDL and DML constants for the persistent memory database.
 * All tables include project_id for multi-project isolation.
 * Schema version is tracked for future migrations.
 */
public final class MemorySchema {

    private MemorySchema() {}

    public static final int CURRENT_VERSION = 3;

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

    // ── Autonomous runs ───────────────────────────────────────────────

    public static final String CREATE_AUTONOMOUS_RUNS = """
            CREATE TABLE IF NOT EXISTS autonomous_runs (
                run_id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                project_id TEXT NOT NULL,
                goal_text TEXT,
                status TEXT NOT NULL,
                start_time TEXT NOT NULL,
                last_update TEXT NOT NULL,
                completed_at TEXT,
                current_plan_revision INTEGER DEFAULT 0,
                active_node_id TEXT,
                completed_nodes_json TEXT,
                failed_nodes_json TEXT,
                recovery_count INTEGER DEFAULT 0,
                replan_count INTEGER DEFAULT 0,
                tool_call_count INTEGER DEFAULT 0,
                requirement_states_json TEXT,
                completion_state TEXT,
                checkpoint_ref TEXT,
                final_validation_result_json TEXT,
                plan_json TEXT,
                FOREIGN KEY (project_id) REFERENCES projects(project_id)
            )
            """;

    // ── Durable event journal ──────────────────────────────────────────

    public static final String CREATE_RUN_EVENTS = """
            CREATE TABLE IF NOT EXISTS run_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id TEXT NOT NULL UNIQUE,
                sequence INTEGER NOT NULL,
                timestamp TEXT NOT NULL,
                project_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                agent_run_id TEXT NOT NULL,
                event_type TEXT NOT NULL,
                payload TEXT,
                FOREIGN KEY (project_id) REFERENCES projects(project_id)
            )
            """;

    // ── Phase 11: Studio Project Metadata (Linked projection) ──────────

    public static final String CREATE_STUDIO_PROJECT_METADATA = """
            CREATE TABLE IF NOT EXISTS studio_project_metadata (
                project_id TEXT PRIMARY KEY,
                description TEXT,
                tags TEXT,
                favorite INTEGER DEFAULT 0,
                target_fps INTEGER DEFAULT 60,
                active_build_profile TEXT DEFAULT 'Development',
                created_at TEXT NOT NULL,
                updated_at TEXT,
                FOREIGN KEY (project_id) REFERENCES projects(project_id)
            )
            """;

    // ── Phase 11: Change Sets & Entries ────────────────────────────────

    public static final String CREATE_CHANGE_SETS = """
            CREATE TABLE IF NOT EXISTS change_sets (
                change_set_id TEXT PRIMARY KEY,
                project_id TEXT NOT NULL,
                agent_run_id TEXT,
                plan_node_id TEXT,
                summary TEXT,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                reviewed_at TEXT,
                reviewed_by TEXT,
                FOREIGN KEY (project_id) REFERENCES projects(project_id)
            )
            """;

    public static final String CREATE_CHANGE_ENTRIES = """
            CREATE TABLE IF NOT EXISTS change_entries (
                entry_id TEXT PRIMARY KEY,
                change_set_id TEXT NOT NULL,
                change_type TEXT NOT NULL,
                target_path TEXT NOT NULL,
                before_hash TEXT,
                after_hash TEXT,
                diff_content TEXT,
                risk_level TEXT NOT NULL,
                approval_state TEXT NOT NULL,
                rationale TEXT,
                FOREIGN KEY (change_set_id) REFERENCES change_sets(change_set_id)
            )
            """;

    // ── Phase 11: Build Records ────────────────────────────────────────

    public static final String CREATE_BUILD_RECORDS = """
            CREATE TABLE IF NOT EXISTS build_records (
                build_id TEXT PRIMARY KEY,
                project_id TEXT NOT NULL,
                platform TEXT NOT NULL,
                build_target TEXT,
                configuration TEXT NOT NULL,
                status TEXT NOT NULL,
                duration_ms INTEGER DEFAULT 0,
                output_path TEXT,
                artifact_size INTEGER DEFAULT 0,
                errors TEXT,
                validation_evidence TEXT,
                created_at TEXT NOT NULL,
                completed_at TEXT,
                FOREIGN KEY (project_id) REFERENCES projects(project_id)
            )
            """;

    // ── Phase 11: Audit Trail ──────────────────────────────────────────

    public static final String CREATE_AUDIT_EVENTS = """
            CREATE TABLE IF NOT EXISTS audit_events (
                event_id TEXT PRIMARY KEY,
                timestamp TEXT NOT NULL,
                user_id TEXT NOT NULL,
                project_id TEXT,
                run_id TEXT,
                action TEXT NOT NULL,
                target TEXT,
                result TEXT NOT NULL,
                details TEXT
            )
            """;

    // ── Phase 11: Studio Notifications ─────────────────────────────────

    public static final String CREATE_STUDIO_NOTIFICATIONS = """
            CREATE TABLE IF NOT EXISTS studio_notifications (
                notification_id TEXT PRIMARY KEY,
                project_id TEXT,
                run_id TEXT,
                severity TEXT NOT NULL,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                read INTEGER DEFAULT 0,
                created_at TEXT NOT NULL
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
        "CREATE INDEX IF NOT EXISTS idx_arch_project ON architecture_snapshots(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_runs_project ON autonomous_runs(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_runs_status ON autonomous_runs(status)",
        "CREATE INDEX IF NOT EXISTS idx_events_run_seq ON run_events(agent_run_id, sequence)",
        "CREATE INDEX IF NOT EXISTS idx_events_project_time ON run_events(project_id, timestamp)",
        "CREATE INDEX IF NOT EXISTS idx_change_sets_project ON change_sets(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_change_entries_set ON change_entries(change_set_id)",
        "CREATE INDEX IF NOT EXISTS idx_build_records_project ON build_records(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_audit_events_project ON audit_events(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_audit_events_time ON audit_events(timestamp)",
        "CREATE INDEX IF NOT EXISTS idx_notifications_project ON studio_notifications(project_id)"
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
        CREATE_USER_PREFERENCES,
        CREATE_AUTONOMOUS_RUNS,
        CREATE_RUN_EVENTS,
        CREATE_STUDIO_PROJECT_METADATA,
        CREATE_CHANGE_SETS,
        CREATE_CHANGE_ENTRIES,
        CREATE_BUILD_RECORDS,
        CREATE_AUDIT_EVENTS,
        CREATE_STUDIO_NOTIFICATIONS
    };
}
