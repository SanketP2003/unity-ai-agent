package com.unityagent.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolRegistry: registration, lookup, duplicate rejection, unknown tool.
 */
class ToolRegistryTest {

    private Tool createTool(String name) {
        return new Tool() {
            @Override
            public String name() { return name; }

            @Override
            public String description() { return "Test tool: " + name; }

            @Override
            public String validate(Map<String, Object> parameters) { return null; }
        };
    }

    @Test
    void testRegistrationViaConstructor() {
        Tool tool1 = createTool("tool_a");
        Tool tool2 = createTool("tool_b");
        ToolRegistry registry = new ToolRegistry(List.of(tool1, tool2));

        assertEquals(2, registry.size());
        assertTrue(registry.hasTool("tool_a"));
        assertTrue(registry.hasTool("tool_b"));
    }

    @Test
    void testLookup() {
        Tool tool = createTool("my_tool");
        ToolRegistry registry = new ToolRegistry(List.of(tool));

        Tool found = registry.getTool("my_tool");
        assertEquals("my_tool", found.name());
    }

    @Test
    void testUnknownToolThrows() {
        ToolRegistry registry = new ToolRegistry(List.of());

        assertFalse(registry.hasTool("nonexistent"));
        assertThrows(IllegalArgumentException.class, () -> registry.getTool("nonexistent"));
    }

    @Test
    void testDuplicateRegistrationThrows() {
        Tool tool1 = createTool("duplicate");
        Tool tool2 = createTool("duplicate");

        assertThrows(IllegalArgumentException.class, () -> new ToolRegistry(List.of(tool1, tool2)));
    }

    @Test
    void testGetAllTools() {
        Tool tool1 = createTool("alpha");
        Tool tool2 = createTool("beta");
        ToolRegistry registry = new ToolRegistry(List.of(tool1, tool2));

        var all = registry.getAllTools();
        assertEquals(2, all.size());
        // Should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> all.add(createTool("gamma")));
    }

    @Test
    void testEmptyRegistry() {
        ToolRegistry registry = new ToolRegistry(List.of());
        assertEquals(0, registry.size());
        assertFalse(registry.hasTool("anything"));
    }

    @Test
    void testUnregisterAndFind() {
        Tool tool = createTool("temp_tool");
        ToolRegistry registry = new ToolRegistry(List.of(tool));
        assertTrue(registry.find("temp_tool").isPresent());
        assertEquals("temp_tool", registry.find("temp_tool").get().name());

        assertTrue(registry.unregister("temp_tool"));
        assertFalse(registry.hasTool("temp_tool"));
        assertTrue(registry.find("temp_tool").isEmpty());
        assertFalse(registry.unregister("temp_tool"));
    }

    @Test
    void testListDefinitionsAndOpenAISchemas() {
        Tool safeTool = new Tool() {
            @Override public String name() { return "safe_tool"; }
            @Override public String description() { return "Safe tool"; }
            @Override public ToolPermission permission() { return ToolPermission.SAFE; }
            @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR); }
            @Override public String validate(Map<String, Object> parameters) { return null; }
            @Override public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "properties", Map.of("key", Map.of("type", "string")));
            }
        };

        Tool blockedTool = new Tool() {
            @Override public String name() { return "blocked_tool"; }
            @Override public String description() { return "Blocked tool"; }
            @Override public ToolPermission permission() { return ToolPermission.BLOCKED; }
            @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
            @Override public String validate(Map<String, Object> parameters) { return null; }
        };

        ToolRegistry registry = new ToolRegistry(List.of(safeTool, blockedTool));
        assertEquals(2, registry.listDefinitions().size());

        // Validate blocked tool is rejected
        String blockedVal = registry.validate("blocked_tool", Map.of());
        assertNotNull(blockedVal);
        assertTrue(blockedVal.contains("BLOCKED"));

        // OpenAI tools generation excludes BLOCKED
        List<Map<String, Object>> openAiTools = registry.getOpenAIToolDefinitions();
        assertEquals(1, openAiTools.size());
        assertEquals("function", openAiTools.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> func = (Map<String, Object>) openAiTools.get(0).get("function");
        assertEquals("safe_tool", func.get("name"));
        assertEquals("Safe tool", func.get("description"));

        // Filter by PLAY_MODE should exclude safe_tool (which is EDITOR only)
        List<Map<String, Object>> playTools = registry.getOpenAIToolDefinitions(ToolMode.PLAY_MODE, java.util.EnumSet.of(ToolPermission.SAFE));
        assertEquals(0, playTools.size());
    }

    @Test
    void testToolMetadataAndGenericPrerequisiteQuery() {
        Tool scriptTool = new Tool() {
            @Override public String name() { return "custom_script_tool"; }
            @Override public String description() { return "Creates custom scripts"; }
            @Override public String validate(Map<String, Object> parameters) { return null; }
            @Override public String domain() { return "SCRIPT"; }
            @Override public List<String> produces() { return List.of("SCRIPT"); }
            @Override public String validationRequired() { return "COMPILE_SUCCESS"; }
        };

        Tool playTool = new Tool() {
            @Override public String name() { return "custom_play_tool"; }
            @Override public String description() { return "Enters play mode"; }
            @Override public String validate(Map<String, Object> parameters) { return null; }
            @Override public String domain() { return "PLAY_MODE"; }
            @Override public List<String> prerequisites() { return List.of("COMPILE_SUCCESS"); }
            @Override public List<String> produces() { return List.of("PLAY_MODE"); }
        };

        ToolRegistry registry = new ToolRegistry(List.of(scriptTool, playTool));

        // Generic query by domain
        List<Tool> scriptTools = registry.getToolsByDomain("SCRIPT");
        assertEquals(1, scriptTools.size());
        assertEquals("custom_script_tool", scriptTools.get(0).name());

        // Generic query by produced artifact
        List<Tool> producingPlayMode = registry.getToolsProducing("PLAY_MODE");
        assertEquals(1, producingPlayMode.size());
        assertEquals("custom_play_tool", producingPlayMode.get(0).name());

        // Generic query by prerequisite
        List<Tool> requiringCompile = registry.getToolsRequiring("COMPILE_SUCCESS");
        assertEquals(1, requiringCompile.size());
        assertEquals("custom_play_tool", requiringCompile.get(0).name());
    }
}
