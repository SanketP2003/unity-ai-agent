package com.unityagent.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TrellisToolTest {

    @Test
    void testGenerateTrellisMeshDefinitionAndValidation() {
        UnityTools.GenerateTrellisMesh tool = new UnityTools.GenerateTrellisMesh();

        assertEquals("generate_trellis_mesh", tool.name());
        assertEquals(ToolPermission.SAFE, tool.permission());
        assertEquals(Set.of(ToolMode.BOTH), tool.allowedModes());
        assertEquals("ASSETS", tool.domain());
        assertEquals(180, tool.timeoutSeconds());

        Map<String, Object> schema = tool.inputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("prompt"));
        assertTrue(props.containsKey("image_path"));
        assertTrue(props.containsKey("asset_name"));
        assertTrue(props.containsKey("position"));
        assertTrue(props.containsKey("rotation"));
        assertTrue(props.containsKey("scale"));
        assertTrue(props.containsKey("add_collider"));

        // Validation: Missing asset_name
        assertNotNull(tool.validate(Map.of("prompt", "alien statue")));

        // Validation: Missing both prompt and image_path
        assertNotNull(tool.validate(Map.of("asset_name", "Statue")));

        // Validation: Valid prompt
        assertNull(tool.validate(Map.of("asset_name", "Statue", "prompt", "alien statue")));

        // Validation: Valid image
        assertNull(tool.validate(Map.of("asset_name", "Statue", "image_path", "Assets/ref.png")));
    }

    @Test
    void testGenerateTrellisMeshInToolRegistry() {
        UnityTools.GenerateTrellisMesh trellisTool = new UnityTools.GenerateTrellisMesh();
        ToolRegistry registry = new ToolRegistry(List.of(trellisTool));

        assertTrue(registry.hasTool("generate_trellis_mesh"));
        List<Map<String, Object>> openAiTools = registry.getOpenAIToolDefinitions();
        assertEquals(1, openAiTools.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) openAiTools.get(0).get("function");
        assertEquals("generate_trellis_mesh", function.get("name"));
        assertTrue(((String) function.get("description")).contains("TRELLIS"));
    }
}
