package com.unityagent.memory;

import com.unityagent.memory.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteMemoryRepositoryTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private SQLiteMemoryRepository repository;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("test_memory.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    void testProjectLifecycle() {
        ProjectMemory p1 = new ProjectMemory(
                "proj_alpha", "AlphaGame", "6000.4.7f1", "Windows",
                "URP", "1.0.0", "fp_123"
        );
        p1.setLastAgentRunId("run_001");
        repository.upsertProject(p1);

        Optional<ProjectMemory> fetched = repository.findProject("proj_alpha");
        assertTrue(fetched.isPresent());
        assertEquals("AlphaGame", fetched.get().getProjectName());
        assertEquals("6000.4.7f1", fetched.get().getUnityVersion());
        assertEquals("run_001", fetched.get().getLastAgentRunId());

        // Update project
        p1.setProjectName("AlphaGame Updated");
        p1.setLastAgentRunId("run_002");
        repository.upsertProject(p1);

        Optional<ProjectMemory> updated = repository.findProject("proj_alpha");
        assertTrue(updated.isPresent());
        assertEquals("AlphaGame Updated", updated.get().getProjectName());
        assertEquals("run_002", updated.get().getLastAgentRunId());

        List<ProjectMemory> all = repository.findAllProjects();
        assertEquals(1, all.size());

        repository.deleteProject("proj_alpha");
        assertTrue(repository.findProject("proj_alpha").isEmpty());
    }

    @Test
    void testConversationOperations() {
        ProjectMemory p = new ProjectMemory("proj_1", "Game1", "6000.4.7f1", null, null, "1.0", null);
        repository.upsertProject(p);

        ConversationRecord c1 = new ConversationRecord(
                "sess_1", "proj_1", "run_1", "Create player", "COMPLETED", 5, "{\"status\":\"OK\"}"
        );
        c1.setCompletedAt(Instant.now());
        repository.insertConversation(c1);

        ConversationRecord c2 = new ConversationRecord(
                "sess_2", "proj_1", "run_2", "Create enemies", "COMPLETED", 8, "{\"status\":\"OK\"}"
        );
        c2.setCompletedAt(Instant.now());
        repository.insertConversation(c2);

        List<ConversationRecord> convs = repository.findConversationsByProject("proj_1", 10);
        assertEquals(2, convs.size());

        Optional<ConversationRecord> bySession = repository.findConversationBySession("sess_1");
        assertTrue(bySession.isPresent());
        assertEquals("run_1", bySession.get().getAgentRunId());
        assertEquals("Create player", bySession.get().getUserGoal());

        Optional<ConversationRecord> byRun = repository.findConversationByRunId("run_2");
        assertTrue(byRun.isPresent());
        assertEquals("sess_2", byRun.get().getSessionId());
        assertEquals(8, byRun.get().getToolCount());
    }

    @Test
    void testMemoryEntriesUpsertAndQuery() {
        ProjectMemory p = new ProjectMemory("proj_1", "Game1", "6000.4.7f1", null, null, "1.0", null);
        repository.upsertProject(p);

        repository.upsertMemoryEntry("proj_1", "ARCHITECTURE", "object:Player", "Player with Rigidbody", "AGENT", 1.0);
        repository.upsertMemoryEntry("proj_1", "ARCHITECTURE", "object:Ground", "Ground cube", "AGENT", 1.0);
        repository.upsertMemoryEntry("proj_1", "VALIDATION", "last_compilation", "SUCCESS", "VALIDATION", 1.0);

        List<MemoryEntry> arch = repository.findMemoryEntries("proj_1", "ARCHITECTURE");
        assertEquals(2, arch.size());

        List<MemoryEntry> byKey = repository.findMemoryEntriesByKey("proj_1", "object:Player");
        assertEquals(1, byKey.size());
        assertEquals("Player with Rigidbody", byKey.get(0).getValue());

        // Update existing entry
        repository.upsertMemoryEntry("proj_1", "ARCHITECTURE", "object:Player", "Player updated with CharacterController", "AGENT", 0.9);
        List<MemoryEntry> updated = repository.findMemoryEntriesByKey("proj_1", "object:Player");
        assertEquals(1, updated.size());
        assertEquals("Player updated with CharacterController", updated.get(0).getValue());

        // Update verified timestamp
        repository.updateLastVerified("proj_1", "ARCHITECTURE", "object:Player");
        assertNotNull(repository.findMemoryEntriesByKey("proj_1", "object:Player").get(0).getLastVerifiedAt());

        // Delete entries
        repository.deleteMemoryEntries("proj_1");
        assertTrue(repository.findMemoryEntries("proj_1", "ARCHITECTURE").isEmpty());
    }

    @Test
    void testScriptTracking() {
        ProjectMemory p = new ProjectMemory("proj_1", "Game1", "6000.4.7f1", null, null, "1.0", null);
        repository.upsertProject(p);

        ScriptMemory s1 = new ScriptMemory();
        s1.setProjectId("proj_1");
        s1.setScriptPath("Assets/Scripts/PlayerController.cs");
        s1.setClassName("PlayerController");
        s1.setContentHash("hash_abc123");
        s1.setAttachedObjects("Player");
        s1.setDependencies("UnityEngine");
        s1.setSource("AGENT_RESULT");
        s1.setConfidence(1.0);
        repository.upsertScript(s1);

        Optional<ScriptMemory> byPath = repository.findScriptByPath("proj_1", "Assets/Scripts/PlayerController.cs");
        assertTrue(byPath.isPresent());
        assertEquals("PlayerController", byPath.get().getClassName());
        assertEquals("hash_abc123", byPath.get().getContentHash());

        Optional<ScriptMemory> byClass = repository.findScriptByClass("proj_1", "PlayerController");
        assertTrue(byClass.isPresent());

        // Update hash
        s1.setContentHash("hash_new456");
        repository.upsertScript(s1);
        assertEquals("hash_new456", repository.findScriptByPath("proj_1", "Assets/Scripts/PlayerController.cs").get().getContentHash());

        List<ScriptMemory> all = repository.findScripts("proj_1");
        assertEquals(1, all.size());

        repository.deleteScript("proj_1", "Assets/Scripts/PlayerController.cs");
        assertTrue(repository.findScriptByPath("proj_1", "Assets/Scripts/PlayerController.cs").isEmpty());
    }

    @Test
    void testAssetTracking() {
        ProjectMemory p = new ProjectMemory("proj_1", "Game1", "6000.4.7f1", null, null, "1.0", null);
        repository.upsertProject(p);

        AssetMemory a1 = new AssetMemory();
        a1.setProjectId("proj_1");
        a1.setAssetPath("Assets/Materials/GreenGround.mat");
        a1.setAssetType("Material");
        a1.setAssetGuid("guid_mat_001");
        a1.setMetadata("{\"color\":\"#00FF00\"}");
        repository.upsertAsset(a1);

        List<AssetMemory> assets = repository.findAssets("proj_1");
        assertEquals(1, assets.size());

        List<AssetMemory> mats = repository.findAssetsByType("proj_1", "Material");
        assertEquals(1, mats.size());
        assertEquals("guid_mat_001", mats.get(0).getAssetGuid());

        repository.deleteAsset("proj_1", "Assets/Materials/GreenGround.mat");
        assertTrue(repository.findAssets("proj_1").isEmpty());
    }

    @Test
    void testArchitectureSnapshots() {
        ProjectMemory p = new ProjectMemory("proj_1", "Game1", "6000.4.7f1", null, null, "1.0", null);
        repository.upsertProject(p);

        assertEquals(1, repository.getNextSnapshotVersion("proj_1"));
        repository.insertArchitectureSnapshot("proj_1", 1, "{\"roots\":[\"Main Camera\",\"Directional Light\"]}");

        assertEquals(2, repository.getNextSnapshotVersion("proj_1"));
        repository.insertArchitectureSnapshot("proj_1", 2, "{\"roots\":[\"Main Camera\",\"Directional Light\",\"Player\"]}");

        Optional<ArchitectureSnapshot> latest = repository.findLatestArchitecture("proj_1");
        assertTrue(latest.isPresent());
        assertEquals(2, latest.get().getSnapshotVersion());
        assertTrue(latest.get().getArchitectureJson().contains("Player"));
    }

    @Test
    void testUserPreferences() {
        ProjectMemory p = new ProjectMemory("proj_1", "Game1", "6000.4.7f1", null, null, "1.0", null);
        repository.upsertProject(p);

        repository.upsertPreference("proj_1", "preferred_style", "low-poly");
        repository.upsertPreference("proj_1", "target_fps", "60");

        List<UserPreference> prefs = repository.findPreferences("proj_1");
        assertEquals(2, prefs.size());

        // Update preference
        repository.upsertPreference("proj_1", "target_fps", "120");
        List<UserPreference> updatedPrefs = repository.findPreferences("proj_1");
        UserPreference fps = updatedPrefs.stream()
                .filter(pr -> "target_fps".equals(pr.getPreferenceKey()))
                .findFirst().orElseThrow();
        assertEquals("120", fps.getPreferenceValue());
    }

    @Test
    void testMultiProjectIsolationAndCascadeWipe() {
        // Project Alpha
        ProjectMemory pA = new ProjectMemory("proj_A", "Game A", "6000.4.7f1", null, null, "1.0", null);
        repository.upsertProject(pA);
        repository.upsertMemoryEntry("proj_A", "ARCHITECTURE", "cube", "AlphaCube", "AGENT", 1.0);
        repository.insertConversation(new ConversationRecord("sess_A", "proj_A", "run_A", "Goal A", "COMPLETED", 1, "{}"));

        // Project Beta
        ProjectMemory pB = new ProjectMemory("proj_B", "Game B", "6000.4.7f1", null, null, "1.0", null);
        repository.upsertProject(pB);
        repository.upsertMemoryEntry("proj_B", "ARCHITECTURE", "sphere", "BetaSphere", "AGENT", 1.0);
        repository.insertConversation(new ConversationRecord("sess_B", "proj_B", "run_B", "Goal B", "COMPLETED", 2, "{}"));

        // Verify isolation
        List<MemoryEntry> entriesA = repository.findMemoryEntries("proj_A", "ARCHITECTURE");
        assertEquals(1, entriesA.size());
        assertEquals("AlphaCube", entriesA.get(0).getValue());

        List<MemoryEntry> entriesB = repository.findMemoryEntries("proj_B", "ARCHITECTURE");
        assertEquals(1, entriesB.size());
        assertEquals("BetaSphere", entriesB.get(0).getValue());

        // Delete Project A completely
        repository.deleteAllProjectData("proj_A");

        // Verify Project A is completely gone
        assertTrue(repository.findProject("proj_A").isEmpty());
        assertTrue(repository.findMemoryEntries("proj_A", "ARCHITECTURE").isEmpty());
        assertTrue(repository.findConversationsByProject("proj_A", 10).isEmpty());

        // Verify Project B is completely untouched
        assertTrue(repository.findProject("proj_B").isPresent());
        assertEquals(1, repository.findMemoryEntries("proj_B", "ARCHITECTURE").size());
        assertEquals(1, repository.findConversationsByProject("proj_B", 10).size());
    }
}
