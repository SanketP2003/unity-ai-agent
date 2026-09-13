package com.unityagent.memory.service;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MemoryRetrieverTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private SQLiteMemoryRepository repository;
    private MemoryRetriever retriever;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("retriever_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        retriever = new MemoryRetriever(repository);
    }

    @AfterEach
    void tearDown() {
        if (db != null) db.shutdown();
    }

    @Test
    void testGetRelevantContextReturnsCompleteStructure() {
        // Setup project
        ProjectMemory proj = new ProjectMemory("proj_test", "MyPlatformer", "6000.4.7f1", "Windows", "URP", "1.0.0", null);
        proj.setLastAgentRunId("run_100");
        repository.upsertProject(proj);

        // Architecture
        repository.insertArchitectureSnapshot("proj_test", 1, "{\"roots\":[\"Player\",\"Platform\"]}");

        // Scripts
        ScriptMemory s1 = new ScriptMemory();
        s1.setProjectId("proj_test");
        s1.setScriptPath("Assets/Scripts/PlayerController.cs");
        s1.setClassName("PlayerController");
        s1.setContentHash("hash_1");
        s1.setAttachedObjects("Player");
        s1.setLastModifiedAt(Instant.now());
        repository.upsertScript(s1);

        ScriptMemory s2 = new ScriptMemory();
        s2.setProjectId("proj_test");
        s2.setScriptPath("Assets/Scripts/EnemyAI.cs");
        s2.setClassName("EnemyAI");
        s2.setContentHash("hash_2");
        s2.setAttachedObjects("Enemy");
        s2.setLastModifiedAt(Instant.now());
        repository.upsertScript(s2);

        // Conversations
        ConversationRecord c1 = new ConversationRecord("sess_1", "proj_test", "run_100", "Build player", "COMPLETED", 4, "{\"status\":\"OK\"}");
        repository.insertConversation(c1);

        // Failures
        repository.upsertMemoryEntry("proj_test", "FAILURE", "compilation_failure", "CS1002 in PlayerController.cs:15", "AGENT", 1.0);

        // Preferences
        repository.upsertPreference("proj_test", "target_fps", "60");

        // Retrieve context for a player goal
        MemoryContext ctx = retriever.getRelevantContext("proj_test", "Fix player movement speed");
        assertNotNull(ctx);
        assertTrue(ctx.hasContent());

        String formatted = ctx.format();
        assertTrue(formatted.contains("=== PROJECT ==="));
        assertTrue(formatted.contains("MyPlatformer"));
        assertTrue(formatted.contains("6000.4.7f1"));
        assertTrue(formatted.contains("=== ARCHITECTURE ==="));
        assertTrue(formatted.contains("=== SCRIPTS ==="));
        assertTrue(formatted.contains("PlayerController.cs"));
        assertTrue(formatted.contains("=== RECENT HISTORY ==="));
        assertTrue(formatted.contains("=== KNOWN ISSUES ==="));
        assertTrue(formatted.contains("CS1002 in PlayerController.cs:15"));
        assertTrue(formatted.contains("=== PREFERENCES ==="));
        assertTrue(formatted.contains("target_fps: 60"));

        assertTrue(ctx.estimateTokens() > 0);
    }

    @Test
    void testNullOrEmptyProjectReturnsNull() {
        assertNull(retriever.getRelevantContext(null, "some goal"));
        assertNull(retriever.getRelevantContext("", "some goal"));
        assertNull(retriever.getRelevantContext("   ", "some goal"));
    }

    @Test
    void testUnknownProjectReturnsNull() {
        MemoryContext ctx = retriever.getRelevantContext("nonexistent_proj", "some goal");
        assertNull(ctx);
    }
}
