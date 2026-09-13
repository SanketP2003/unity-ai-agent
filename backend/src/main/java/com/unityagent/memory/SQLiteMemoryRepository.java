package com.unityagent.memory;

import com.unityagent.memory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of {@link MemoryRepository}.
 * Uses plain JDBC PreparedStatements — no JPA/Hibernate dependency.
 * All queries include project_id for multi-project isolation.
 */
@Repository
public class SQLiteMemoryRepository implements MemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(SQLiteMemoryRepository.class);
    private final MemoryDatabase db;

    public SQLiteMemoryRepository(MemoryDatabase db) {
        this.db = db;
    }

    // ── Projects ───────────────────────────────────────────────────────

    @Override
    public void upsertProject(ProjectMemory p) {
        String sql = """
            INSERT INTO projects (project_id, project_name, unity_version, platform,
                render_pipeline, extension_version, project_fingerprint, created_at,
                last_connected_at, last_agent_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(project_id) DO UPDATE SET
                project_name = excluded.project_name,
                unity_version = excluded.unity_version,
                platform = excluded.platform,
                render_pipeline = excluded.render_pipeline,
                extension_version = excluded.extension_version,
                project_fingerprint = excluded.project_fingerprint,
                last_connected_at = excluded.last_connected_at,
                last_agent_run_id = COALESCE(excluded.last_agent_run_id, last_agent_run_id)
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getProjectId());
            ps.setString(2, p.getProjectName());
            ps.setString(3, p.getUnityVersion());
            ps.setString(4, p.getPlatform());
            ps.setString(5, p.getRenderPipeline());
            ps.setString(6, p.getExtensionVersion());
            ps.setString(7, p.getProjectFingerprint());
            ps.setString(8, toIsoString(p.getCreatedAt()));
            ps.setString(9, toIsoString(p.getLastConnectedAt()));
            ps.setString(10, p.getLastAgentRunId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert project {}: {}", p.getProjectId(), e.getMessage());
        }
    }

    @Override
    public Optional<ProjectMemory> findProject(String projectId) {
        String sql = "SELECT * FROM projects WHERE project_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapProject(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find project {}: {}", projectId, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<ProjectMemory> findAllProjects() {
        List<ProjectMemory> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM projects ORDER BY last_connected_at DESC")) {
            while (rs.next()) {
                result.add(mapProject(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to list projects: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public void deleteProject(String projectId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM projects WHERE project_id = ?")) {
            ps.setString(1, projectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete project {}: {}", projectId, e.getMessage());
        }
    }

    // ── Conversations ──────────────────────────────────────────────────

    @Override
    public void insertConversation(ConversationRecord r) {
        String sql = """
            INSERT INTO conversations (session_id, project_id, agent_run_id, user_goal,
                status, tool_count, summary, created_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getSessionId());
            ps.setString(2, r.getProjectId());
            ps.setString(3, r.getAgentRunId());
            ps.setString(4, r.getUserGoal());
            ps.setString(5, r.getStatus());
            ps.setInt(6, r.getToolCount());
            ps.setString(7, r.getSummary());
            ps.setString(8, toIsoString(r.getCreatedAt()));
            ps.setString(9, toIsoString(r.getCompletedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert conversation for project {}: {}", r.getProjectId(), e.getMessage());
        }
    }

    @Override
    public List<ConversationRecord> findConversationsByProject(String projectId, int limit) {
        List<ConversationRecord> result = new ArrayList<>();
        String sql = "SELECT * FROM conversations WHERE project_id = ? ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapConversation(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find conversations for project {}: {}", projectId, e.getMessage());
        }
        return result;
    }

    @Override
    public Optional<ConversationRecord> findConversationBySession(String sessionId) {
        String sql = "SELECT * FROM conversations WHERE session_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapConversation(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find conversation by session {}: {}", sessionId, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<ConversationRecord> findConversationByRunId(String agentRunId) {
        String sql = "SELECT * FROM conversations WHERE agent_run_id = ? LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agentRunId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapConversation(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find conversation by runId {}: {}", agentRunId, e.getMessage());
        }
        return Optional.empty();
    }

    // ── Memory Entries ─────────────────────────────────────────────────

    @Override
    public void upsertMemoryEntry(String projectId, String category, String key,
                                    String value, String source, double confidence) {
        String sql = """
            INSERT INTO memory_entries (project_id, category, key, value, source, confidence,
                created_at, updated_at, last_verified_at)
            VALUES (?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'), datetime('now'))
            ON CONFLICT(id) DO UPDATE SET
                value = excluded.value,
                source = excluded.source,
                confidence = excluded.confidence,
                updated_at = datetime('now'),
                last_verified_at = datetime('now')
            """;
        // Since memory_entries has no natural unique key (project_id + category + key),
        // we need a two-step approach: check then insert/update
        String checkSql = "SELECT id FROM memory_entries WHERE project_id = ? AND category = ? AND key = ? LIMIT 1";
        try (Connection conn = db.getConnection()) {
            Long existingId = null;
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setString(1, projectId);
                check.setString(2, category);
                check.setString(3, key);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) existingId = rs.getLong("id");
                }
            }

            if (existingId != null) {
                String updateSql = """
                    UPDATE memory_entries SET value = ?, source = ?, confidence = ?,
                        updated_at = datetime('now'), last_verified_at = datetime('now')
                    WHERE id = ?
                    """;
                try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                    update.setString(1, value);
                    update.setString(2, source);
                    update.setDouble(3, confidence);
                    update.setLong(4, existingId);
                    update.executeUpdate();
                }
            } else {
                String insertSql = """
                    INSERT INTO memory_entries (project_id, category, key, value, source,
                        confidence, created_at, updated_at, last_verified_at)
                    VALUES (?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'), datetime('now'))
                    """;
                try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                    insert.setString(1, projectId);
                    insert.setString(2, category);
                    insert.setString(3, key);
                    insert.setString(4, value);
                    insert.setString(5, source);
                    insert.setDouble(6, confidence);
                    insert.executeUpdate();
                }
            }
        } catch (SQLException e) {
            log.error("Failed to upsert memory entry [{}/{}] for project {}: {}",
                    category, key, projectId, e.getMessage());
        }
    }

    @Override
    public List<MemoryEntry> findMemoryEntries(String projectId, String category) {
        List<MemoryEntry> result = new ArrayList<>();
        String sql = "SELECT * FROM memory_entries WHERE project_id = ? AND category = ? ORDER BY updated_at DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, category);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapMemoryEntry(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find memory entries for project {}/{}: {}", projectId, category, e.getMessage());
        }
        return result;
    }

    @Override
    public List<MemoryEntry> findMemoryEntriesByKey(String projectId, String key) {
        List<MemoryEntry> result = new ArrayList<>();
        String sql = "SELECT * FROM memory_entries WHERE project_id = ? AND key = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapMemoryEntry(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find memory by key {}: {}", key, e.getMessage());
        }
        return result;
    }

    @Override
    public void deleteMemoryEntries(String projectId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM memory_entries WHERE project_id = ?")) {
            ps.setString(1, projectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete memory entries for project {}: {}", projectId, e.getMessage());
        }
    }

    @Override
    public void updateLastVerified(String projectId, String category, String key) {
        String sql = "UPDATE memory_entries SET last_verified_at = datetime('now') WHERE project_id = ? AND category = ? AND key = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, category);
            ps.setString(3, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update verified timestamp: {}", e.getMessage());
        }
    }

    // ── Scripts ────────────────────────────────────────────────────────

    @Override
    public void upsertScript(ScriptMemory s) {
        String sql = """
            INSERT INTO scripts (project_id, script_path, class_name, content_hash,
                attached_objects, dependencies, last_modified_at, source, confidence)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(project_id, script_path) DO UPDATE SET
                class_name = excluded.class_name,
                content_hash = excluded.content_hash,
                attached_objects = excluded.attached_objects,
                dependencies = excluded.dependencies,
                last_modified_at = excluded.last_modified_at,
                source = excluded.source,
                confidence = excluded.confidence
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getProjectId());
            ps.setString(2, s.getScriptPath());
            ps.setString(3, s.getClassName());
            ps.setString(4, s.getContentHash());
            ps.setString(5, s.getAttachedObjects());
            ps.setString(6, s.getDependencies());
            ps.setString(7, toIsoString(s.getLastModifiedAt()));
            ps.setString(8, s.getSource());
            ps.setDouble(9, s.getConfidence());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert script {} for project {}: {}", s.getScriptPath(), s.getProjectId(), e.getMessage());
        }
    }

    @Override
    public List<ScriptMemory> findScripts(String projectId) {
        List<ScriptMemory> result = new ArrayList<>();
        String sql = "SELECT * FROM scripts WHERE project_id = ? ORDER BY script_path";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapScript(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find scripts for project {}: {}", projectId, e.getMessage());
        }
        return result;
    }

    @Override
    public Optional<ScriptMemory> findScriptByPath(String projectId, String path) {
        String sql = "SELECT * FROM scripts WHERE project_id = ? AND script_path = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, path);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapScript(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find script by path {}: {}", path, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<ScriptMemory> findScriptByClass(String projectId, String className) {
        String sql = "SELECT * FROM scripts WHERE project_id = ? AND class_name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, className);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapScript(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find script by class {}: {}", className, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void deleteScript(String projectId, String path) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM scripts WHERE project_id = ? AND script_path = ?")) {
            ps.setString(1, projectId);
            ps.setString(2, path);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete script {}: {}", path, e.getMessage());
        }
    }

    // ── Assets ─────────────────────────────────────────────────────────

    @Override
    public void upsertAsset(AssetMemory a) {
        String sql = """
            INSERT INTO assets (project_id, asset_path, asset_type, asset_guid, metadata, last_modified_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(project_id, asset_path) DO UPDATE SET
                asset_type = excluded.asset_type,
                asset_guid = excluded.asset_guid,
                metadata = excluded.metadata,
                last_modified_at = excluded.last_modified_at
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.getProjectId());
            ps.setString(2, a.getAssetPath());
            ps.setString(3, a.getAssetType());
            ps.setString(4, a.getAssetGuid());
            ps.setString(5, a.getMetadata());
            ps.setString(6, toIsoString(a.getLastModifiedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert asset {}: {}", a.getAssetPath(), e.getMessage());
        }
    }

    @Override
    public List<AssetMemory> findAssets(String projectId) {
        List<AssetMemory> result = new ArrayList<>();
        String sql = "SELECT * FROM assets WHERE project_id = ? ORDER BY asset_path";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapAsset(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find assets for project {}: {}", projectId, e.getMessage());
        }
        return result;
    }

    @Override
    public List<AssetMemory> findAssetsByType(String projectId, String assetType) {
        List<AssetMemory> result = new ArrayList<>();
        String sql = "SELECT * FROM assets WHERE project_id = ? AND asset_type = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, assetType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapAsset(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find assets by type {}: {}", assetType, e.getMessage());
        }
        return result;
    }

    @Override
    public void deleteAsset(String projectId, String path) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM assets WHERE project_id = ? AND asset_path = ?")) {
            ps.setString(1, projectId);
            ps.setString(2, path);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete asset {}: {}", path, e.getMessage());
        }
    }

    // ── Architecture ───────────────────────────────────────────────────

    @Override
    public void insertArchitectureSnapshot(String projectId, int version, String architectureJson) {
        String sql = """
            INSERT INTO architecture_snapshots (project_id, snapshot_version, architecture_json, created_at)
            VALUES (?, ?, ?, datetime('now'))
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setInt(2, version);
            ps.setString(3, architectureJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert architecture snapshot v{} for {}: {}", version, projectId, e.getMessage());
        }
    }

    @Override
    public Optional<ArchitectureSnapshot> findLatestArchitecture(String projectId) {
        String sql = "SELECT * FROM architecture_snapshots WHERE project_id = ? ORDER BY snapshot_version DESC LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ArchitectureSnapshot s = new ArchitectureSnapshot();
                    s.setId(rs.getLong("id"));
                    s.setProjectId(rs.getString("project_id"));
                    s.setSnapshotVersion(rs.getInt("snapshot_version"));
                    s.setArchitectureJson(rs.getString("architecture_json"));
                    s.setCreatedAt(parseInstant(rs.getString("created_at")));
                    return Optional.of(s);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find latest architecture for {}: {}", projectId, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public int getNextSnapshotVersion(String projectId) {
        String sql = "SELECT COALESCE(MAX(snapshot_version), 0) + 1 AS next_version FROM architecture_snapshots WHERE project_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("next_version");
            }
        } catch (SQLException e) {
            log.error("Failed to get next snapshot version for {}: {}", projectId, e.getMessage());
        }
        return 1;
    }

    // ── Preferences ────────────────────────────────────────────────────

    @Override
    public void upsertPreference(String projectId, String key, String value) {
        String checkSql = "SELECT id FROM user_preferences WHERE (project_id = ? OR (project_id IS NULL AND ? IS NULL)) AND preference_key = ? LIMIT 1";
        try (Connection conn = db.getConnection()) {
            Long existingId = null;
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setString(1, projectId);
                check.setString(2, projectId);
                check.setString(3, key);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) existingId = rs.getLong("id");
                }
            }
            if (existingId != null) {
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE user_preferences SET preference_value = ?, updated_at = datetime('now') WHERE id = ?")) {
                    update.setString(1, value);
                    update.setLong(2, existingId);
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO user_preferences (project_id, preference_key, preference_value, created_at, updated_at) VALUES (?, ?, ?, datetime('now'), datetime('now'))")) {
                    insert.setString(1, projectId);
                    insert.setString(2, key);
                    insert.setString(3, value);
                    insert.executeUpdate();
                }
            }
        } catch (SQLException e) {
            log.error("Failed to upsert preference {}: {}", key, e.getMessage());
        }
    }

    @Override
    public List<UserPreference> findPreferences(String projectId) {
        List<UserPreference> result = new ArrayList<>();
        String sql = "SELECT * FROM user_preferences WHERE project_id = ? OR project_id IS NULL";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserPreference pref = new UserPreference();
                    pref.setId(rs.getLong("id"));
                    pref.setProjectId(rs.getString("project_id"));
                    pref.setPreferenceKey(rs.getString("preference_key"));
                    pref.setPreferenceValue(rs.getString("preference_value"));
                    pref.setCreatedAt(parseInstant(rs.getString("created_at")));
                    pref.setUpdatedAt(parseInstant(rs.getString("updated_at")));
                    result.add(pref);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find preferences for project {}: {}", projectId, e.getMessage());
        }
        return result;
    }

    // ── Bulk Operations ────────────────────────────────────────────────

    @Override
    public void deleteAllProjectData(String projectId) {
        String[] tables = {"user_preferences", "architecture_snapshots", "assets", "scripts",
                            "memory_entries", "conversations", "projects"};
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (String table : tables) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM " + table + " WHERE project_id = ?")) {
                        ps.setString(1, projectId);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
                log.info("Deleted all memory data for project {}", projectId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to delete all project data for {}: {}", projectId, e.getMessage());
        }
    }

    // ── Row Mappers ────────────────────────────────────────────────────

    private ProjectMemory mapProject(ResultSet rs) throws SQLException {
        ProjectMemory p = new ProjectMemory();
        p.setProjectId(rs.getString("project_id"));
        p.setProjectName(rs.getString("project_name"));
        p.setUnityVersion(rs.getString("unity_version"));
        p.setPlatform(rs.getString("platform"));
        p.setRenderPipeline(rs.getString("render_pipeline"));
        p.setExtensionVersion(rs.getString("extension_version"));
        p.setProjectFingerprint(rs.getString("project_fingerprint"));
        p.setCreatedAt(parseInstant(rs.getString("created_at")));
        p.setLastConnectedAt(parseInstant(rs.getString("last_connected_at")));
        p.setLastAgentRunId(rs.getString("last_agent_run_id"));
        return p;
    }

    private ConversationRecord mapConversation(ResultSet rs) throws SQLException {
        ConversationRecord r = new ConversationRecord();
        r.setId(rs.getLong("id"));
        r.setSessionId(rs.getString("session_id"));
        r.setProjectId(rs.getString("project_id"));
        r.setAgentRunId(rs.getString("agent_run_id"));
        r.setUserGoal(rs.getString("user_goal"));
        r.setStatus(rs.getString("status"));
        r.setToolCount(rs.getInt("tool_count"));
        r.setSummary(rs.getString("summary"));
        r.setCreatedAt(parseInstant(rs.getString("created_at")));
        r.setCompletedAt(parseInstant(rs.getString("completed_at")));
        return r;
    }

    private MemoryEntry mapMemoryEntry(ResultSet rs) throws SQLException {
        MemoryEntry e = new MemoryEntry();
        e.setId(rs.getLong("id"));
        e.setProjectId(rs.getString("project_id"));
        e.setCategory(rs.getString("category"));
        e.setKey(rs.getString("key"));
        e.setValue(rs.getString("value"));
        e.setSource(rs.getString("source"));
        e.setConfidence(rs.getDouble("confidence"));
        e.setCreatedAt(parseInstant(rs.getString("created_at")));
        e.setUpdatedAt(parseInstant(rs.getString("updated_at")));
        e.setLastVerifiedAt(parseInstant(rs.getString("last_verified_at")));
        return e;
    }

    private ScriptMemory mapScript(ResultSet rs) throws SQLException {
        ScriptMemory s = new ScriptMemory();
        s.setId(rs.getLong("id"));
        s.setProjectId(rs.getString("project_id"));
        s.setScriptPath(rs.getString("script_path"));
        s.setClassName(rs.getString("class_name"));
        s.setContentHash(rs.getString("content_hash"));
        s.setAttachedObjects(rs.getString("attached_objects"));
        s.setDependencies(rs.getString("dependencies"));
        s.setLastModifiedAt(parseInstant(rs.getString("last_modified_at")));
        s.setSource(rs.getString("source"));
        s.setConfidence(rs.getDouble("confidence"));
        return s;
    }

    private AssetMemory mapAsset(ResultSet rs) throws SQLException {
        AssetMemory a = new AssetMemory();
        a.setId(rs.getLong("id"));
        a.setProjectId(rs.getString("project_id"));
        a.setAssetPath(rs.getString("asset_path"));
        a.setAssetType(rs.getString("asset_type"));
        a.setAssetGuid(rs.getString("asset_guid"));
        a.setMetadata(rs.getString("metadata"));
        a.setLastModifiedAt(parseInstant(rs.getString("last_modified_at")));
        return a;
    }

    // ── Utilities ──────────────────────────────────────────────────────

    private static String toIsoString(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            // SQLite datetime('now') format: "2024-01-01 12:00:00"
            try {
                return java.time.LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atZone(java.time.ZoneOffset.UTC).toInstant();
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
