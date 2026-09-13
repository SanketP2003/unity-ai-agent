package com.unityagent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.AgentLimits;
import com.unityagent.agent.AgentLoop;
import com.unityagent.agent.CancellationToken;
import com.unityagent.agent.memory.ConversationMemory;
import com.unityagent.agent.model.*;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.provider.ProviderCapabilities;
import com.unityagent.agent.security.ScriptCheckpointRegistry;
import com.unityagent.agent.service.AgentRunState;
import com.unityagent.agent.service.AgentService;
import com.unityagent.memory.listener.MemoryEventListener;
import com.unityagent.memory.model.*;
import com.unityagent.memory.service.MemoryContext;
import com.unityagent.memory.service.MemoryRetriever;
import com.unityagent.memory.service.MemorySummarizer;
import com.unityagent.memory.service.ProjectMemoryService;
import com.unityagent.tools.ToolMode;
import com.unityagent.tools.ToolRegistry;
import com.unityagent.unity.UnityCommandExecutor;
import com.unityagent.unity.UnityConnection;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryEndToEndIntegrationTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private SQLiteMemoryRepository repository;
    private ProjectMemoryService projectService;
    private MemorySummarizer summarizer;
    private MemoryEventListener memoryListener;
    private MemoryRetriever retriever;

    private AIProvider mockAiProvider;
    private ToolRegistry toolRegistry;
    private UnityCommandExecutor mockUnityExecutor;
    private AgentLimits limits;
    private ConversationMemory conversationMemory;
    private ObjectMapper mapper;
    private ScriptCheckpointRegistry checkpointRegistry;
    private AgentLoop agentLoop;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("e2e_memory.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        projectService = new ProjectMemoryService(repository);
        summarizer = new MemorySummarizer();
        memoryListener = new MemoryEventListener(repository, summarizer);
        retriever = new MemoryRetriever(repository);

        mockAiProvider = mock(AIProvider.class);
        when(mockAiProvider.isConfigured()).thenReturn(true);
        when(mockAiProvider.getProviderName()).thenReturn("mock-ai");
        when(mockAiProvider.getCapabilities()).thenReturn(new ProviderCapabilities(true, false, false, false));

        toolRegistry = new ToolRegistry(List.of());
        mockUnityExecutor = mock(UnityCommandExecutor.class);
        limits = new AgentLimits(30, 5, 3, 30, 600);
        conversationMemory = new ConversationMemory();
        mapper = new ObjectMapper();
        checkpointRegistry = new ScriptCheckpointRegistry();

        agentLoop = new AgentLoop(mockAiProvider, toolRegistry, mockUnityExecutor, limits,
                conversationMemory, mapper, checkpointRegistry);

        agentService = new AgentService(agentLoop, conversationMemory, memoryListener, retriever, projectService);
    }

    @AfterEach
    void tearDown() {
        if (db != null) db.shutdown();
    }

    @Test
    void testEndToEndMemoryPersistenceAcrossRuns() throws Exception {
        String projectId = "proj_platformer";

        // Step 1: Register Project (as occurs on Unity handshake)
        ProjectMemory project = projectService.registerProject(
                projectId, "6000.4.7f1", "Platformer 3D", "1.0.0", Map.of("editorVersion", "6000.4.7f1")
        );
        assertNotNull(project);
        assertEquals("Platformer 3D", project.getProjectName());

        // Step 2: Simulate Run 1 — creating objects and scripts
        // Set up Mock AI to return a tool call to create a player primitive and then conclude
        ToolCall createCall = ToolCall.of("call_1", "create_primitive",
                Map.of("primitiveType", "Cube", "name", "PlayerHero"));
        AgentCompletion completion1 = AgentCompletion.toolCalls(null, List.of(createCall));
        AgentCompletion completion2 = AgentCompletion.text("Created PlayerHero successfully.");

        // Register dummy tool in registry
        com.unityagent.tools.Tool mockTool = mock(com.unityagent.tools.Tool.class);
        when(mockTool.name()).thenReturn("create_primitive");
        when(mockTool.allowedModes()).thenReturn(java.util.Set.of(ToolMode.BOTH));
        when(mockTool.permission()).thenReturn(com.unityagent.tools.ToolPermission.SAFE_WRITE);
        when(mockTool.definition()).thenReturn(new com.unityagent.tools.ToolDefinition(
                "create_primitive", "Creates primitive", Map.of(), null,
                com.unityagent.tools.ToolPermission.SAFE_WRITE, java.util.Set.of(ToolMode.BOTH), false, 10, "Core", List.of()
        ));
        toolRegistry.register(mockTool);

        when(mockUnityExecutor.execute(any(), any(), eq("create_primitive"), any()))
                .thenReturn(com.unityagent.unity.UnityMessage.toolResponse("op1", true, Map.of("name", "PlayerHero", "objectId", "obj_101")));

        when(mockAiProvider.generate(any()))
                .thenReturn(completion1)
                .thenReturn(completion2);

        AgentRunResult run1Result = agentService.runSync("sess_1", "run_1", "Build me a player hero", ToolMode.BOTH, projectId);
        assertTrue(run1Result.isSuccess());

        // Step 3: Verify that Run 1 events were written into SQLite persistent memory!
        List<MemoryEntry> archEntries = repository.findMemoryEntries(projectId, "ARCHITECTURE");
        assertEquals(1, archEntries.size());
        assertEquals("PlayerHero", archEntries.get(0).getValue());

        List<ConversationRecord> convs = repository.findConversationsByProject(projectId, 10);
        assertEquals(1, convs.size());
        assertEquals("run_1", convs.get(0).getAgentRunId());
        assertEquals("COMPLETED", convs.get(0).getStatus());

        // Verify project lastAgentRunId was updated in database
        ProjectMemory updatedProject = repository.findProject(projectId).orElseThrow();
        assertEquals("run_1", updatedProject.getLastAgentRunId());

        // Step 4: Run 2 — Retrieval of persistent memory
        // Query memory context for next instruction
        MemoryContext retrievedCtx = retriever.getRelevantContext(projectId, "Upgrade PlayerHero jump speed");
        assertNotNull(retrievedCtx);
        assertTrue(retrievedCtx.hasContent());
        String formatted = retrievedCtx.format();

        // Memory must contain project info, last run id, and recent history
        assertTrue(formatted.contains("Platformer 3D"));
        assertTrue(formatted.contains("Last agent run: run_1"));
        assertTrue(formatted.contains("Build me a player hero"));

        // Step 5: Multi-project isolation verification
        String otherProjectId = "proj_isolated_beta";
        projectService.registerProject(otherProjectId, "6000.4.7f1", "Other Game", "1.0.0", Map.of());

        MemoryContext otherCtx = retriever.getRelevantContext(otherProjectId, "Build player");
        assertNotNull(otherCtx);
        // Should NOT contain PlayerHero or Platformer 3D from the first project
        String otherFormatted = otherCtx.format();
        assertFalse(otherFormatted.contains("PlayerHero"));
        assertFalse(otherFormatted.contains("Platformer 3D"));
        assertFalse(otherFormatted.contains("run_1"));
    }
}
