package com.unityagent.phase14;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ConversationRecord;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.memory.model.ScriptMemory;
import com.unityagent.memory.service.MemoryContext;
import com.unityagent.memory.service.MemoryRetriever;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CrossProjectMemorySecurityTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private MemoryRetriever retriever;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("phase14_mem_sec.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        retriever = new MemoryRetriever(repository);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testMemoryIsStrictlyIsolatedPerProject() {
        // Register Project Alpha & Beta
        ProjectMemory memAlpha = new ProjectMemory("proj_alpha", "Alpha Game", "6000.0.0f1", "Windows", "URP", "1.0.0", "fp_alpha");
        ProjectMemory memBeta = new ProjectMemory("proj_beta", "Beta Game", "6000.0.0f1", "Windows", "URP", "1.0.0", "fp_beta");

        repository.upsertProject(memAlpha);
        repository.upsertProject(memBeta);

        // Store script and conversation for Alpha only
        ScriptMemory scriptAlpha = new ScriptMemory(
                "proj_alpha",
                "Assets/Scripts/AlphaBoss.cs",
                "AlphaBoss",
                "hash123",
                "[]",
                "[]"
        );
        repository.upsertScript(scriptAlpha);

        ConversationRecord convAlpha = new ConversationRecord(
                "sess_alpha",
                "proj_alpha",
                "run_alpha",
                "Build secret alpha weapon",
                "COMPLETED",
                5,
                "Secret Alpha Weapon created"
        );
        repository.insertConversation(convAlpha);

        // Query memory for Alpha
        MemoryContext ctxAlpha = retriever.getRelevantContext("proj_alpha", "AlphaBoss");
        assertNotNull(ctxAlpha);
        assertTrue(ctxAlpha.hasContent());
        assertTrue(ctxAlpha.format().contains("AlphaBoss"));

        // Query memory for Beta
        MemoryContext ctxBeta = retriever.getRelevantContext("proj_beta", "AlphaBoss");
        if (ctxBeta != null) {
            assertTrue(ctxBeta.getScriptSummaries().isEmpty());
            assertFalse(ctxBeta.format().contains("AlphaBoss"));
        }

        // Direct repository isolation verification
        assertEquals(1, repository.findScripts("proj_alpha").size());
        assertEquals(0, repository.findScripts("proj_beta").size());
        assertEquals(1, repository.findConversationsByProject("proj_alpha", 10).size());
        assertEquals(0, repository.findConversationsByProject("proj_beta", 10).size());
    }
}
