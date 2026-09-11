package com.unityagent.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
}
