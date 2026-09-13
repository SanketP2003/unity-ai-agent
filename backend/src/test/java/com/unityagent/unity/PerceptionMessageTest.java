package com.unityagent.unity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.tools.ToolMode;
import com.unityagent.tools.ToolPermission;
import com.unityagent.tools.ToolRegistry;
import com.unityagent.tools.UnityTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated tests for Phase 3: Unity Perception tool results,
 * payload serializations, objectId handling, and parameter validation.
 */
class PerceptionMessageTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void testHierarchySerialization() throws Exception {
        String json = """
                {
                    "sceneName": "MainScene",
                    "rootCount": 1,
                    "roots": [
                        {
                            "objectId": "obj_1001",
                            "instanceId": 1001,
                            "name": "Arena",
                            "activeSelf": true,
                            "tag": "Untagged",
                            "layer": 0,
                            "components": ["Transform"],
                            "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                            "rotation": {"x": 0.0, "y": 0.0, "z": 0.0},
                            "scale": {"x": 1.0, "y": 1.0, "z": 1.0},
                            "children": [
                                {
                                    "objectId": "obj_1002",
                                    "instanceId": 1002,
                                    "name": "Ground",
                                    "activeSelf": true,
                                    "tag": "Untagged",
                                    "layer": 0,
                                    "components": ["Transform", "MeshFilter", "MeshRenderer", "BoxCollider"],
                                    "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                                    "rotation": {"x": 0.0, "y": 0.0, "z": 0.0},
                                    "scale": {"x": 20.0, "y": 1.0, "z": 20.0},
                                    "children": []
                                },
                                {
                                    "objectId": "obj_1003",
                                    "instanceId": 1003,
                                    "name": "Player",
                                    "activeSelf": true,
                                    "tag": "Player",
                                    "layer": 0,
                                    "components": ["Transform", "CapsuleCollider", "Rigidbody"],
                                    "position": {"x": 0.0, "y": 1.0, "z": 0.0},
                                    "rotation": {"x": 0.0, "y": 0.0, "z": 0.0},
                                    "scale": {"x": 1.0, "y": 1.0, "z": 1.0},
                                    "children": [
                                        {
                                            "objectId": "obj_1004",
                                            "instanceId": 1004,
                                            "name": "Camera",
                                            "activeSelf": true,
                                            "tag": "MainCamera",
                                            "layer": 0,
                                            "components": ["Transform", "Camera"],
                                            "children": []
                                        }
                                    ]
                                }
                            ]
                        }
                    ]
                }
                """;

        Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
        assertEquals("MainScene", map.get("sceneName"));
        assertEquals(1, map.get("rootCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roots = (List<Map<String, Object>>) map.get("roots");
        assertEquals(1, roots.size());
        Map<String, Object> arena = roots.get(0);
        assertEquals("obj_1001", arena.get("objectId"));
        assertEquals("Arena", arena.get("name"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> arenaChildren = (List<Map<String, Object>>) arena.get("children");
        assertEquals(2, arenaChildren.size());

        Map<String, Object> ground = arenaChildren.get(0);
        assertEquals("obj_1002", ground.get("objectId"));
        assertEquals("Ground", ground.get("name"));

        Map<String, Object> player = arenaChildren.get(1);
        assertEquals("obj_1003", player.get("objectId"));
        assertEquals("Player", player.get("name"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> playerChildren = (List<Map<String, Object>>) player.get("children");
        assertEquals(1, playerChildren.size());
        assertEquals("obj_1004", playerChildren.get(0).get("objectId"));
        assertEquals("Camera", playerChildren.get(0).get("name"));
    }

    @Test
    void testActiveSceneSerialization() throws Exception {
        String json = """
                {
                    "name": "SampleScene",
                    "path": "Assets/Scenes/SampleScene.unity",
                    "isLoaded": true,
                    "isDirty": false,
                    "rootCount": 3,
                    "buildIndex": 0
                }
                """;

        Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
        assertEquals("SampleScene", map.get("name"));
        assertEquals("Assets/Scenes/SampleScene.unity", map.get("path"));
        assertEquals(true, map.get("isLoaded"));
        assertEquals(false, map.get("isDirty"));
        assertEquals(3, map.get("rootCount"));
        assertEquals(0, map.get("buildIndex"));
    }

    @Test
    void testSelectedObjectEmptyState() throws Exception {
        String json = """
                {
                    "hasSelection": false,
                    "message": "No GameObject is currently selected in the Unity Editor."
                }
                """;

        Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
        assertEquals(false, map.get("hasSelection"));
        assertTrue(map.get("message").toString().contains("No GameObject"));
    }

    @Test
    void testSelectedObjectPopulatedState() throws Exception {
        String json = """
                {
                    "hasSelection": true,
                    "objectId": "obj_4567",
                    "name": "Player",
                    "hierarchyPath": "Arena/Player",
                    "activeSelf": true,
                    "scene": "MainScene",
                    "components": ["Transform", "CapsuleCollider", "Rigidbody"],
                    "transform": {
                        "position": {"x": 0.0, "y": 1.0, "z": 0.0},
                        "rotation": {"x": 0.0, "y": 90.0, "z": 0.0},
                        "scale": {"x": 1.0, "y": 1.0, "z": 1.0},
                        "localPosition": {"x": 0.0, "y": 1.0, "z": 0.0},
                        "localRotation": {"x": 0.0, "y": 90.0, "z": 0.0},
                        "localScale": {"x": 1.0, "y": 1.0, "z": 1.0}
                    },
                    "parent": "Arena",
                    "childCount": 2
                }
                """;

        Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
        assertEquals(true, map.get("hasSelection"));
        assertEquals("obj_4567", map.get("objectId"));
        assertEquals("Player", map.get("name"));
        assertEquals("Arena/Player", map.get("hierarchyPath"));
        assertEquals("Arena", map.get("parent"));
        assertEquals(2, map.get("childCount"));

        @SuppressWarnings("unchecked")
        Map<String, Object> transform = (Map<String, Object>) map.get("transform");
        assertNotNull(transform);
        assertNotNull(transform.get("position"));
        assertNotNull(transform.get("localPosition"));
    }

    @Test
    void testComponentSerialization() throws Exception {
        String json = """
                {
                    "objectId": "obj_200",
                    "name": "Enemy",
                    "scene": "Level1",
                    "components": ["Transform", "MeshRenderer", "SphereCollider"],
                    "componentDetails": [
                        {"type": "Transform", "enabled": true},
                        {"type": "MeshRenderer", "enabled": true},
                        {"type": "SphereCollider", "enabled": false}
                    ]
                }
                """;

        Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
        assertEquals("obj_200", map.get("objectId"));
        assertEquals("Enemy", map.get("name"));

        @SuppressWarnings("unchecked")
        List<String> comps = (List<String>) map.get("components");
        assertEquals(3, comps.size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) map.get("componentDetails");
        assertEquals(3, details.size());
        assertEquals("Transform", details.get(0).get("type"));
        assertEquals(true, details.get(0).get("enabled"));
        assertEquals("SphereCollider", details.get(2).get("type"));
        assertEquals(false, details.get(2).get("enabled"));
    }

    @Test
    void testTransformSerialization() throws Exception {
        String json = """
                {
                    "objectId": "obj_888",
                    "name": "Turret",
                    "position": {"x": 10.0, "y": 0.0, "z": 5.0},
                    "rotation": {"x": 0.0, "y": 45.0, "z": 0.0},
                    "scale": {"x": 2.0, "y": 2.0, "z": 2.0},
                    "localPosition": {"x": 0.0, "y": 0.0, "z": 0.0},
                    "localRotation": {"x": 0.0, "y": 0.0, "z": 0.0},
                    "localScale": {"x": 1.0, "y": 1.0, "z": 1.0},
                    "forward": {"x": 0.707, "y": 0.0, "z": 0.707},
                    "up": {"x": 0.0, "y": 1.0, "z": 0.0},
                    "right": {"x": 0.707, "y": 0.0, "z": -0.707},
                    "parent": "Fortress",
                    "parentObjectId": "obj_777",
                    "childCount": 1
                }
                """;

        Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
        assertEquals("obj_888", map.get("objectId"));
        assertEquals("Turret", map.get("name"));
        assertEquals("Fortress", map.get("parent"));
        assertEquals("obj_777", map.get("parentObjectId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> fwd = (Map<String, Object>) map.get("forward");
        assertEquals(0.707, (Double) fwd.get("x"), 0.001);
    }

    @Test
    void testPlayModeStateSerialization() throws Exception {
        String json = """
                {
                    "isPlaying": false,
                    "isPaused": false,
                    "isCompiling": false,
                    "state": "EditMode"
                }
                """;

        Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
        assertEquals(false, map.get("isPlaying"));
        assertEquals(false, map.get("isPaused"));
        assertEquals(false, map.get("isCompiling"));
        assertEquals("EditMode", map.get("state"));
    }

    @Test
    void testConsoleErrorSerialization() throws Exception {
        String json = """
                {
                    "errorCount": 2,
                    "errors": [
                        {
                            "type": "Error",
                            "message": "NullReferenceException: Object reference not set",
                            "stackTrace": "PlayerController.Update() at line 42",
                            "timestamp": "14:32:00.123"
                        },
                        {
                            "type": "Exception",
                            "message": "IndexOutOfRangeException",
                            "stackTrace": "Inventory.GetItem() at line 12",
                            "timestamp": "14:32:01.456"
                        }
                    ]
                }
                """;

        Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
        assertEquals(2, map.get("errorCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) map.get("errors");
        assertEquals(2, errors.size());
        assertEquals("Error", errors.get(0).get("type"));
        assertEquals("Exception", errors.get(1).get("type"));
        assertNotNull(errors.get(0).get("stackTrace"));
        assertNotNull(errors.get(0).get("timestamp"));
    }

    @Test
    void testToolRegistryRegistrationForPerceptionTools() {
        var allTools = List.of(
                new UnityTools.GetActiveScene(),
                new UnityTools.GetSelectedObject(),
                new UnityTools.GetObjectComponents(),
                new UnityTools.GetObjectTransform(),
                new UnityTools.GetPlayModeState(),
                new UnityTools.GetConsoleErrors()
        );

        ToolRegistry registry = new ToolRegistry(allTools);
        assertEquals(6, registry.size());

        assertTrue(registry.hasTool("get_active_scene"));
        assertTrue(registry.hasTool("get_selected_object"));
        assertTrue(registry.hasTool("get_object_components"));
        assertTrue(registry.hasTool("get_object_transform"));
        assertTrue(registry.hasTool("get_play_mode_state"));
        assertTrue(registry.hasTool("get_console_errors"));

        // Verify permissions are SAFE
        for (var tool : allTools) {
            assertEquals(ToolPermission.SAFE, tool.permission(), "Perception tool must be SAFE: " + tool.name());
        }

        // Verify OpenAI tool definitions generation includes all SAFE perception tools
        List<Map<String, Object>> openAiTools = registry.getOpenAIToolDefinitions();
        assertEquals(6, openAiTools.size());
    }

    @Test
    void testObjectIdHandlingAndTargetValidation() {
        var getComponents = new UnityTools.GetObjectComponents();
        var getTransform = new UnityTools.GetObjectTransform();

        // 1. Lookup by objectId is valid
        assertNull(getComponents.validate(Map.of("objectId", "obj_123")));
        assertNull(getTransform.validate(Map.of("objectId", "obj_123")));

        // 2. Lookup by target is valid
        assertNull(getComponents.validate(Map.of("target", "Player")));
        assertNull(getTransform.validate(Map.of("target", "Player")));

        // 3. Both target and objectId provided is valid
        assertNull(getComponents.validate(Map.of("target", "Player", "objectId", "obj_123")));

        // 4. Missing both target and objectId is invalid
        assertNotNull(getComponents.validate(Map.of()));
        assertNotNull(getTransform.validate(Map.of()));

        // 5. Blank string is invalid
        assertNotNull(getComponents.validate(Map.of("objectId", "   ")));
        assertNotNull(getTransform.validate(Map.of("target", "")));
    }

    @Test
    void testMalformedToolParameters() {
        var consoleErrors = new UnityTools.GetConsoleErrors();

        // Negative count
        assertNotNull(consoleErrors.validate(Map.of("count", -10)));

        // Zero count
        assertNotNull(consoleErrors.validate(Map.of("count", 0)));

        // String for count
        assertNotNull(consoleErrors.validate(Map.of("count", "not-a-number")));

        // Valid count
        assertNull(consoleErrors.validate(Map.of("count", 50)));
    }
}
