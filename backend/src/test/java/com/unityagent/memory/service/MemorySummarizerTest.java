package com.unityagent.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.event.AgentEvent;
import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.model.ErrorType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemorySummarizerTest {

    private final MemorySummarizer summarizer = new MemorySummarizer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testExtractSummaryFromSuccessfulRun() throws Exception {
        AgentRunResult result = AgentRunResult.success("sess_1", "run_1", "Game built successfully", 3, 5, List.of(), "Build a small 3D platform game");

        List<AgentEvent> events = new ArrayList<>();
        events.add(AgentEvent.toolCompleted(1, "sess_1", "run_1", "call_1", "create_primitive", Map.of("name", "GroundCube")));
        events.add(AgentEvent.toolCompleted(2, "sess_1", "run_1", "call_2", "create_script", Map.of("path", "Assets/Scripts/Player.cs", "hash", "hash_p1")));
        events.add(AgentEvent.toolCompleted(3, "sess_1", "run_1", "call_3", "compile_project", Map.of("errorCount", 0)));
        events.add(AgentEvent.toolCompleted(4, "sess_1", "run_1", "call_4", "validate_game_state", Map.of("allRequirementsSatisfied", true)));

        String summaryJson = summarizer.extractSummary(result, events);
        assertNotNull(summaryJson);

        JsonNode root = mapper.readTree(summaryJson);
        assertEquals("Build a small 3D platform game", root.get("goal").asText());
        assertEquals("COMPLETED", root.get("status").asText());
        assertEquals(3, root.get("iterations").asInt());
        assertEquals(4, root.get("toolCount").asInt());

        JsonNode objects = root.get("createdObjects");
        assertTrue(objects.isArray());
        assertEquals(1, objects.size());
        assertEquals("GroundCube", objects.get(0).asText());

        JsonNode scripts = root.get("createdScripts");
        assertTrue(scripts.isArray());
        assertEquals(1, scripts.size());
        assertEquals("Assets/Scripts/Player.cs", scripts.get(0).asText());

        assertEquals("SUCCESS", root.get("compilationResult").asText());
    }

    @Test
    void testExtractSummaryFromFailedRun() throws Exception {
        AgentRunResult result = AgentRunResult.failure("sess_2", "run_2", ErrorType.RUNTIME_ERROR, "Exception in Play Mode", 4, 3, List.of(), "Test player jump");

        List<AgentEvent> events = new ArrayList<>();
        events.add(AgentEvent.toolCompleted(1, "sess_2", "run_2", "call_1", "create_script", Map.of("path", "Assets/Scripts/Jump.cs")));
        events.add(AgentEvent.toolFailed(2, "sess_2", "run_2", "call_2", "run_game_test", "NullReferenceException at Jump.cs:22"));
        events.add(AgentEvent.repairStarted(3, "sess_2", "run_2", "Assets/Scripts/Jump.cs"));
        events.add(AgentEvent.toolCompleted(4, "sess_2", "run_2", "call_3", "update_script", Map.of("path", "Assets/Scripts/Jump.cs", "hash", "hash_fixed")));

        String summaryJson = summarizer.extractSummary(result, events);
        assertNotNull(summaryJson);

        JsonNode root = mapper.readTree(summaryJson);
        assertEquals("Test player jump", root.get("goal").asText());
        assertEquals("FAILED", root.get("status").asText());

        JsonNode modified = root.get("modifiedScripts");
        assertEquals(1, modified.size());
        assertEquals("Assets/Scripts/Jump.cs", modified.get(0).asText());

        JsonNode errors = root.get("runtimeErrors");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).asText().contains("NullReferenceException"));

        JsonNode repairs = root.get("repairs");
        assertEquals(1, repairs.size());
    }

    @Test
    void testExtractSummaryWithFallbackGoal() throws Exception {
        AgentRunResult resultWithoutGoal = AgentRunResult.success("sess_3", "run_3", "Done", 1, 0, List.of());
        String summaryJson = summarizer.extractSummary("Fallback user goal", resultWithoutGoal, List.of());

        JsonNode root = mapper.readTree(summaryJson);
        assertEquals("Fallback user goal", root.get("goal").asText());
        assertEquals("COMPLETED", root.get("status").asText());
    }
}
