package com.unityagent.memory.listener;

import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.model.ToolExecutionResult;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ConversationRecord;
import com.unityagent.memory.model.MemoryEntry;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.memory.model.ScriptMemory;
import com.unityagent.memory.service.MemorySummarizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MemoryEventListenerTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private SQLiteMemoryRepository repository;
    private MemorySummarizer summarizer;
    private MemoryEventListener listener;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("listener_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        summarizer = new MemorySummarizer();
        listener = new MemoryEventListener(repository, summarizer);

        // Seed project
        repository.upsertProject(new ProjectMemory("proj_listener", "TestGame", "6000.4.7f1", null, null, "1.0", null));
    }

    @AfterEach
    void tearDown() {
        if (db != null) db.shutdown();
    }

    @Test
    void testToolEventsRecording() {
        listener.setRunContext("proj_listener", "sess_L1", "run_L1", "Create player and ground");

        // Tool completed: create_primitive
        ToolExecutionResult createGoResult = ToolExecutionResult.success("c1", "op1", "create_primitive", Map.of("name", "GroundPlane"));
        listener.onToolCompleted("run_L1", "c1", "create_primitive", createGoResult);

        List<MemoryEntry> arch = repository.findMemoryEntries("proj_listener", "ARCHITECTURE");
        assertEquals(1, arch.size());
        assertEquals("GroundPlane", arch.get(0).getValue());

        // Tool completed: create_script
        ToolExecutionResult createScriptResult = ToolExecutionResult.success("c2", "op2", "create_script", Map.of("path", "Assets/Scripts/Player.cs", "hash", "hash_init"));
        listener.onToolCompleted("run_L1", "c2", "create_script", createScriptResult);

        Optional<ScriptMemory> script = repository.findScriptByPath("proj_listener", "Assets/Scripts/Player.cs");
        assertTrue(script.isPresent());
        assertEquals("Player", script.get().getClassName());
        assertEquals("hash_init", script.get().getContentHash());

        // Tool completed: update_script
        ToolExecutionResult updateScriptResult = ToolExecutionResult.success("c3", "op3", "update_script", Map.of("path", "Assets/Scripts/Player.cs", "hash", "hash_v2"));
        listener.onToolCompleted("run_L1", "c3", "update_script", updateScriptResult);

        Optional<ScriptMemory> updatedScript = repository.findScriptByPath("proj_listener", "Assets/Scripts/Player.cs");
        assertTrue(updatedScript.isPresent());
        assertEquals("hash_v2", updatedScript.get().getContentHash());

        // Tool completed: compile_project
        ToolExecutionResult compileResult = ToolExecutionResult.success("c4", "op4", "compile_project", Map.of("errorCount", 0));
        listener.onToolCompleted("run_L1", "c4", "compile_project", compileResult);

        List<MemoryEntry> validation = repository.findMemoryEntries("proj_listener", "VALIDATION");
        assertFalse(validation.isEmpty());

        // Tool completed: validate_game_state
        ToolExecutionResult validateResult = ToolExecutionResult.success("c5", "op5", "validate_game_state", "{\"allRequirementsSatisfied\":true}");
        listener.onToolCompleted("run_L1", "c5", "validate_game_state", validateResult);

        List<MemoryEntry> byKey = repository.findMemoryEntriesByKey("proj_listener", "last_validation");
        assertEquals(1, byKey.size());
        assertEquals("ALL_SATISFIED", byKey.get(0).getValue());
    }

    @Test
    void testToolFailedRecording() {
        listener.setRunContext("proj_listener", "sess_L2", "run_L2", "Fix compile error");

        ToolExecutionResult compileFail = ToolExecutionResult.failure("c1", "op1", "compile_project", com.unityagent.agent.model.ErrorType.COMPILE_ERROR, "CS0246: The type 'Player' could not be found");
        listener.onToolFailed("run_L2", "c1", "compile_project", compileFail);

        List<MemoryEntry> failures = repository.findMemoryEntries("proj_listener", "FAILURE");
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).getValue().contains("CS0246"));
    }

    @Test
    void testRunCompletedPersistence() {
        listener.setRunContext("proj_listener", "sess_L3", "run_L3", "Build full game");

        ToolExecutionResult res = ToolExecutionResult.success("c1", "op1", "create_primitive", Map.of("name", "Hero"));
        listener.onToolCompleted("run_L3", "c1", "create_primitive", res);

        AgentRunResult runResult = AgentRunResult.success("sess_L3", "run_L3", "Game is complete!", 2, 1, List.of(res), "Build full game");
        listener.onRunCompleted("sess_L3", "run_L3", runResult);

        List<ConversationRecord> convs = repository.findConversationsByProject("proj_listener", 10);
        assertEquals(1, convs.size());
        ConversationRecord rec = convs.get(0);
        assertEquals("sess_L3", rec.getSessionId());
        assertEquals("run_L3", rec.getAgentRunId());
        assertEquals("COMPLETED", rec.getStatus());
        assertEquals(1, rec.getToolCount());
        assertNotNull(rec.getSummary());

        // Verify project updated last run id
        ProjectMemory proj = repository.findProject("proj_listener").orElseThrow();
        assertEquals("run_L3", proj.getLastAgentRunId());
    }

    @Test
    void testRunFailedPersistence() {
        listener.setRunContext("proj_listener", "sess_L4", "run_L4", "Flawed goal");
        listener.onRunFailed("sess_L4", "run_L4", "Timeout reached");

        List<ConversationRecord> convs = repository.findConversationsByProject("proj_listener", 10);
        assertEquals(1, convs.size());
        assertEquals("FAILED", convs.get(0).getStatus());

        List<MemoryEntry> failures = repository.findMemoryEntries("proj_listener", "FAILURE");
        assertEquals(1, failures.size());
    }
}
