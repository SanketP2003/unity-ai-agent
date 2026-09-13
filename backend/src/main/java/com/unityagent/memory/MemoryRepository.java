package com.unityagent.memory;

import com.unityagent.memory.model.*;

import java.util.List;
import java.util.Optional;

/**
 * Database-independent repository contract for persistent agent memory.
 * All queries require projectId for multi-project isolation.
 * Implementations must never store API keys, tokens, or credentials.
 */
public interface MemoryRepository {

    // ── Projects ───────────────────────────────────────────────────────

    void upsertProject(ProjectMemory project);

    Optional<ProjectMemory> findProject(String projectId);

    List<ProjectMemory> findAllProjects();

    void deleteProject(String projectId);

    // ── Conversations ──────────────────────────────────────────────────

    void insertConversation(ConversationRecord record);

    List<ConversationRecord> findConversationsByProject(String projectId, int limit);

    Optional<ConversationRecord> findConversationBySession(String sessionId);

    Optional<ConversationRecord> findConversationByRunId(String agentRunId);

    // ── Memory Entries ─────────────────────────────────────────────────

    void upsertMemoryEntry(String projectId, String category, String key,
                            String value, String source, double confidence);

    List<MemoryEntry> findMemoryEntries(String projectId, String category);

    List<MemoryEntry> findMemoryEntriesByKey(String projectId, String key);

    void deleteMemoryEntries(String projectId);

    void updateLastVerified(String projectId, String category, String key);

    // ── Scripts ────────────────────────────────────────────────────────

    void upsertScript(ScriptMemory script);

    List<ScriptMemory> findScripts(String projectId);

    Optional<ScriptMemory> findScriptByPath(String projectId, String path);

    Optional<ScriptMemory> findScriptByClass(String projectId, String className);

    void deleteScript(String projectId, String path);

    // ── Assets ─────────────────────────────────────────────────────────

    void upsertAsset(AssetMemory asset);

    List<AssetMemory> findAssets(String projectId);

    List<AssetMemory> findAssetsByType(String projectId, String assetType);

    void deleteAsset(String projectId, String path);

    // ── Architecture ───────────────────────────────────────────────────

    void insertArchitectureSnapshot(String projectId, int version, String architectureJson);

    Optional<ArchitectureSnapshot> findLatestArchitecture(String projectId);

    int getNextSnapshotVersion(String projectId);

    // ── Preferences ────────────────────────────────────────────────────

    void upsertPreference(String projectId, String key, String value);

    List<UserPreference> findPreferences(String projectId);

    // ── Bulk Operations ────────────────────────────────────────────────

    /** Delete all memory data for a project. Does NOT delete Unity assets. */
    void deleteAllProjectData(String projectId);
}
