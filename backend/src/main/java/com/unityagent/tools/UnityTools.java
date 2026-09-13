package com.unityagent.tools;

import com.unityagent.agent.security.ScriptSafetyValidator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of tool definitions for the Autonomous Unity Agent.
 * Contains tool metadata, documentation, parameter validation, execution permissions,
 * and JSON schemas for LLM tool calling.
 */
public class UnityTools {

    // --- Schema Builder Utilities ---

    private static Map<String, Object> vector3Schema(String description) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("x", Map.of("type", "number", "description", "X coordinate"));
        props.put("y", Map.of("type", "number", "description", "Y coordinate"));
        props.put("z", Map.of("type", "number", "description", "Z coordinate"));

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("description", description);
        s.put("properties", props);
        s.put("required", List.of("x", "y", "z"));
        return s;
    }

    private static Map<String, Object> colorSchema(String description) {
        return Map.of(
                "type", "string",
                "description", description + " (Hex color string e.g. '#FF0000' or '#33AA33')"
        );
    }

    // --- Validation Utilities ---

    private static String checkTarget(Map<String, Object> parameters) {
        if (parameters == null || (!parameters.containsKey("target") && !parameters.containsKey("objectId"))) {
            return "Parameter 'target' or 'objectId' is required";
        }
        Object obj = parameters.get("objectId");
        if (obj == null) obj = parameters.get("target");
        if (!(obj instanceof String s) || s.isBlank()) {
            return "Parameter 'target' or 'objectId' must be a non-empty string";
        }
        return null;
    }

    private static String checkVector3(Object val, String name) {
        if (val == null) return null;
        if (!(val instanceof Map<?, ?> m)) {
            return "Parameter '" + name + "' must be an object with numeric x, y, z fields";
        }
        if (!m.containsKey("x") || !m.containsKey("y") || !m.containsKey("z")) {
            return "Parameter '" + name + "' must contain x, y, and z keys";
        }
        if (!(m.get("x") instanceof Number) || !(m.get("y") instanceof Number) || !(m.get("z") instanceof Number)) {
            return "Parameter '" + name + "' fields x, y, and z must be numbers";
        }
        return null;
    }

    private static String checkColor(Object val, String name) {
        if (val == null) return null;
        if (val instanceof String s) {
            if (!s.matches("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")) {
                return "Parameter '" + name + "' hex color string must match format #RGB, #RRGGBB, or #RRGGBBAA";
            }
            return null;
        }
        if (val instanceof Map<?, ?> m) {
            if (!m.containsKey("r") || !m.containsKey("g") || !m.containsKey("b")) {
                return "Parameter '" + name + "' color object must contain r, g, and b fields (0.0 - 1.0)";
            }
            return null;
        }
        return "Parameter '" + name + "' must be a hex string or an RGBA map object";
    }

    // ==========================================
    // Phase 4: Acceptance Test Tool
    // ==========================================

    @Component
    public static class CreateTestCube implements Tool {
        @Override
        public String name() { return "create_test_cube"; }

        @Override
        public String description() {
            return "Creates a test cube named 'AIAgent_TestCube' at (0,0,0). " +
                   "Used for verifying the end-to-end Java ↔ Unity communication pipeline.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            return null;
        }
    }

    // ==========================================
    // Phase 5: Scene & Hierarchy Management Tools
    // ==========================================

    @Component
    public static class GetSceneHierarchy implements Tool {
        @Override
        public String name() { return "get_scene_hierarchy"; }

        @Override
        public String description() {
            return "Returns the full hierarchy tree of GameObjects in the active scene, " +
                   "including transforms, active state, tags, layers, and attached components.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("rootOnly", Map.of("type", "boolean", "description", "If true, only returns root-level GameObjects"));
            return Map.of("type", "object", "properties", props);
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            if (parameters != null && parameters.containsKey("rootOnly")) {
                Object val = parameters.get("rootOnly");
                if (!(val instanceof Boolean) && !"true".equalsIgnoreCase(String.valueOf(val)) && !"false".equalsIgnoreCase(String.valueOf(val))) {
                    return "Parameter 'rootOnly' must be a boolean";
                }
            }
            return null;
        }
    }

    @Component
    public static class CreateScene implements Tool {
        @Override
        public String name() { return "create_scene"; }

        @Override
        public String description() {
            return "Creates a new scene in the Unity Editor with optional default setup (EmptyScene or DefaultGameObjects).";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SUPERVISED; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("setup", Map.of(
                    "type", "string",
                    "enum", List.of("EmptyScene", "DefaultGameObjects"),
                    "description", "Scene template setup: 'EmptyScene' or 'DefaultGameObjects'"
            ));
            return Map.of("type", "object", "properties", props);
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            if (parameters != null && parameters.containsKey("setup")) {
                String setup = String.valueOf(parameters.get("setup"));
                if (!Set.of("EmptyScene", "DefaultGameObjects", "DefaultGame", "DefaultScene").contains(setup)) {
                    return "Parameter 'setup' must be either 'EmptyScene' or 'DefaultGameObjects'";
                }
            }
            return null;
        }
    }

    @Component
    public static class SaveScene implements Tool {
        @Override
        public String name() { return "save_scene"; }

        @Override
        public String description() {
            return "Saves the active Unity scene to the asset database (optional scenePath, e.g. 'Assets/Scenes/Main.unity').";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("scenePath", Map.of("type", "string", "description", "Target path ending with .unity (optional)"));
            return Map.of("type", "object", "properties", props);
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            if (parameters != null && parameters.containsKey("scenePath")) {
                if (!(parameters.get("scenePath") instanceof String s) || s.isBlank()) {
                    return "Parameter 'scenePath' must be a valid path string ending with .unity";
                }
            }
            return null;
        }
    }

    // ==========================================
    // Phase 5: GameObject & Transform Tools
    // ==========================================

    @Component
    public static class CreatePrimitive implements Tool {
        private static final Set<String> ALLOWED_TYPES = Set.of(
                "Cube", "Sphere", "Capsule", "Cylinder", "Plane", "Quad"
        );

        @Override
        public String name() { return "create_primitive"; }

        @Override
        public String description() {
            return "Creates a standard 3D primitive (Cube, Sphere, Capsule, Cylinder, Plane, Quad) " +
                   "with customizable name, position, rotation, scale, and optional parent.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public String domain() { return "GAMEOBJECT"; }

        @Override
        public List<String> produces() { return List.of("GAMEOBJECT"); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("type", Map.of(
                    "type", "string",
                    "enum", List.of("Cube", "Sphere", "Capsule", "Cylinder", "Plane", "Quad"),
                    "description", "Type of 3D primitive"
            ));
            props.put("name", Map.of("type", "string", "description", "Unique name for the GameObject"));
            props.put("position", vector3Schema("World position vector"));
            props.put("rotation", vector3Schema("Euler angles rotation vector in degrees"));
            props.put("scale", vector3Schema("Scale vector (default 1,1,1)"));
            props.put("parent", Map.of("type", "string", "description", "Optional name of parent GameObject"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("type"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("type")) {
                return "Parameter 'type' is required (Cube, Sphere, Capsule, Cylinder, Plane, Quad)";
            }
            String type = String.valueOf(parameters.get("type"));
            if (!ALLOWED_TYPES.contains(type)) {
                return "Invalid primitive type '" + type + "'. Allowed: " + ALLOWED_TYPES;
            }

            String posErr = checkVector3(parameters.get("position"), "position");
            if (posErr != null) return posErr;

            String rotErr = checkVector3(parameters.get("rotation"), "rotation");
            if (rotErr != null) return rotErr;

            String scaleErr = checkVector3(parameters.get("scale"), "scale");
            if (scaleErr != null) return scaleErr;

            return null;
        }
    }

    @Component
    public static class CreateEmptyGameObject implements Tool {
        @Override
        public String name() { return "create_empty_gameobject"; }

        @Override
        public String description() {
            return "Creates an empty GameObject with a name, optional position, rotation, scale, and parent.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Name for the empty GameObject"));
            props.put("position", vector3Schema("World position"));
            props.put("rotation", vector3Schema("Euler rotation in degrees"));
            props.put("scale", vector3Schema("Scale vector"));
            props.put("parent", Map.of("type", "string", "description", "Optional parent name"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("name"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("name")) {
                return "Parameter 'name' is required";
            }
            if (!(parameters.get("name") instanceof String s) || s.isBlank()) {
                return "Parameter 'name' must be a non-empty string";
            }

            String posErr = checkVector3(parameters.get("position"), "position");
            if (posErr != null) return posErr;

            String rotErr = checkVector3(parameters.get("rotation"), "rotation");
            if (rotErr != null) return rotErr;

            String scaleErr = checkVector3(parameters.get("scale"), "scale");
            if (scaleErr != null) return scaleErr;

            return null;
        }
    }

    @Component
    public static class SetTransform implements Tool {
        @Override
        public String name() { return "set_transform"; }

        @Override
        public String description() {
            return "Updates the position, rotation (Euler angles), and/or scale of an existing GameObject.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or path"));
            props.put("position", vector3Schema("Position vector"));
            props.put("rotation", vector3Schema("Euler rotation in degrees"));
            props.put("scale", vector3Schema("Scale vector"));
            props.put("space", Map.of("type", "string", "enum", List.of("World", "Local"), "description", "Coordinate space ('World' or 'Local')"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("target"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            String targetErr = checkTarget(parameters);
            if (targetErr != null) return targetErr;

            if (!parameters.containsKey("position") && !parameters.containsKey("rotation") && !parameters.containsKey("scale")) {
                return "At least one of 'position', 'rotation', or 'scale' must be provided";
            }

            String posErr = checkVector3(parameters.get("position"), "position");
            if (posErr != null) return posErr;

            String rotErr = checkVector3(parameters.get("rotation"), "rotation");
            if (rotErr != null) return rotErr;

            String scaleErr = checkVector3(parameters.get("scale"), "scale");
            if (scaleErr != null) return scaleErr;

            if (parameters.containsKey("space")) {
                String space = String.valueOf(parameters.get("space"));
                if (!Set.of("World", "Local").contains(space)) {
                    return "Parameter 'space' must be either 'World' or 'Local'";
                }
            }

            return null;
        }
    }

    @Component
    public static class DestroyGameObject implements Tool {
        @Override
        public String name() { return "destroy_gameobject"; }

        @Override
        public String description() {
            return "Destroys a GameObject by name or hierarchy path.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.DESTRUCTIVE; }

        @Override
        public boolean isDestructive() { return true; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Name or path of the GameObject to destroy"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("target"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            return checkTarget(parameters);
        }
    }

    @Component
    public static class SetParent implements Tool {
        @Override
        public String name() { return "set_parent"; }

        @Override
        public String description() {
            return "Sets the parent GameObject for a target GameObject, or unparents if parent is null/empty.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Child GameObject to reparent"));
            props.put("parent", Map.of("type", "string", "description", "Parent GameObject name (empty to unparent)"));
            props.put("worldPositionStays", Map.of("type", "boolean", "description", "Keep world position (default true)"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("target"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            return checkTarget(parameters);
        }
    }

    // ==========================================
    // Phase 5: Components & Physics Tools
    // ==========================================

    @Component
    public static class AddComponent implements Tool {
        @Override
        public String name() { return "add_component"; }

        @Override
        public String description() {
            return "Adds a component (e.g. Rigidbody, BoxCollider, AudioSource, etc.) to the target GameObject.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name"));
            props.put("componentType", Map.of("type", "string", "description", "Component class name (e.g. 'Rigidbody', 'BoxCollider', 'SphereCollider', 'AudioSource')"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("target", "componentType"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            String targetErr = checkTarget(parameters);
            if (targetErr != null) return targetErr;

            if (!parameters.containsKey("componentType") || parameters.get("componentType") == null) {
                return "Parameter 'componentType' is required (e.g. 'Rigidbody', 'BoxCollider')";
            }
            if (!(parameters.get("componentType") instanceof String s) || s.isBlank()) {
                return "Parameter 'componentType' must be a non-empty string";
            }
            return null;
        }
    }

    @Component
    public static class SetComponentProperty implements Tool {
        @Override
        public String name() { return "set_component_property"; }

        @Override
        public String description() {
            return "Sets a property or field on an attached component (e.g. isKinematic, mass, useGravity, isTrigger).";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name"));
            props.put("componentType", Map.of("type", "string", "description", "Component type name (e.g. 'Rigidbody')"));
            props.put("property", Map.of("type", "string", "description", "Field or property name (e.g. 'isKinematic', 'mass', 'useGravity', 'isTrigger')"));
            props.put("value", Map.of("description", "Value to assign (boolean, number, string, or vector)"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("target", "componentType", "property", "value"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            String targetErr = checkTarget(parameters);
            if (targetErr != null) return targetErr;

            if (!parameters.containsKey("componentType") || parameters.get("componentType") == null) {
                return "Parameter 'componentType' is required";
            }
            if (!parameters.containsKey("property") || parameters.get("property") == null) {
                return "Parameter 'property' is required";
            }
            if (!parameters.containsKey("value")) {
                return "Parameter 'value' is required";
            }
            return null;
        }
    }

    @Component
    public static class RemoveComponent implements Tool {
        @Override
        public String name() { return "remove_component"; }

        @Override
        public String description() {
            return "Removes an attached component from the target GameObject.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.DESTRUCTIVE; }

        @Override
        public boolean isDestructive() { return true; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name"));
            props.put("componentType", Map.of("type", "string", "description", "Component type name to remove"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("target", "componentType"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            String targetErr = checkTarget(parameters);
            if (targetErr != null) return targetErr;

            if (!parameters.containsKey("componentType") || parameters.get("componentType") == null) {
                return "Parameter 'componentType' is required";
            }
            return null;
        }
    }

    // ==========================================
    // Phase 5: Materials & Visuals Tools
    // ==========================================

    @Component
    public static class SetMaterialColor implements Tool {
        @Override
        public String name() { return "set_material_color"; }

        @Override
        public String description() {
            return "Sets the color of a GameObject's Renderer material directly using hex (#RRGGBB) or RGBA.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name"));
            props.put("color", colorSchema("Color for material"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("target", "color"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            String targetErr = checkTarget(parameters);
            if (targetErr != null) return targetErr;

            if (!parameters.containsKey("color") || parameters.get("color") == null) {
                return "Parameter 'color' is required";
            }
            return checkColor(parameters.get("color"), "color");
        }
    }

    @Component
    public static class CreateMaterial implements Tool {
        @Override
        public String name() { return "create_material"; }

        @Override
        public String description() {
            return "Creates a Material asset under Assets/Materials with color, metallic, and smoothness.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Material asset name (e.g. 'RedGlow')"));
            props.put("color", colorSchema("Base material color"));
            props.put("metallic", Map.of("type", "number", "description", "Metallic value (0.0 to 1.0)"));
            props.put("smoothness", Map.of("type", "number", "description", "Smoothness value (0.0 to 1.0)"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("name"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("name")) {
                return "Parameter 'name' is required";
            }
            if (!(parameters.get("name") instanceof String s) || s.isBlank()) {
                return "Parameter 'name' must be a non-empty string";
            }

            if (parameters.containsKey("color")) {
                String colErr = checkColor(parameters.get("color"), "color");
                if (colErr != null) return colErr;
            }

            if (parameters.containsKey("metallic") && !(parameters.get("metallic") instanceof Number)) {
                return "Parameter 'metallic' must be a number between 0.0 and 1.0";
            }
            if (parameters.containsKey("smoothness") && !(parameters.get("smoothness") instanceof Number)) {
                return "Parameter 'smoothness' must be a number between 0.0 and 1.0";
            }

            return null;
        }
    }

    @Component
    public static class ApplyMaterial implements Tool {
        @Override
        public String name() { return "apply_material"; }

        @Override
        public String description() {
            return "Assigns an existing material asset (e.g. 'Assets/Materials/Red.mat') to a GameObject's Renderer.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name"));
            props.put("materialPath", Map.of("type", "string", "description", "Material asset path (e.g. 'Assets/Materials/Red.mat')"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("target", "materialPath"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            String targetErr = checkTarget(parameters);
            if (targetErr != null) return targetErr;

            if (!parameters.containsKey("materialPath") || parameters.get("materialPath") == null) {
                return "Parameter 'materialPath' is required";
            }
            if (!(parameters.get("materialPath") instanceof String s) || s.isBlank()) {
                return "Parameter 'materialPath' must be a valid asset path string";
            }
            return null;
        }
    }

    // ==========================================
    // Phase 5: Lighting & Camera Tools
    // ==========================================

    @Component
    public static class CreateLight implements Tool {
        private static final Set<String> ALLOWED_TYPES = Set.of("Directional", "Point", "Spot");

        @Override
        public String name() { return "create_light"; }

        @Override
        public String description() {
            return "Creates a light (Directional, Point, Spot) with configurable color, intensity, range, and transform.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("lightType", Map.of(
                    "type", "string",
                    "enum", List.of("Directional", "Point", "Spot"),
                    "description", "Light type"
            ));
            props.put("name", Map.of("type", "string", "description", "Light GameObject name"));
            props.put("color", colorSchema("Light color"));
            props.put("intensity", Map.of("type", "number", "description", "Light intensity"));
            props.put("range", Map.of("type", "number", "description", "Light range (Point/Spot)"));
            props.put("position", vector3Schema("Light position"));
            props.put("rotation", vector3Schema("Light Euler rotation"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("lightType"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("lightType")) {
                return "Parameter 'lightType' is required (Directional, Point, Spot)";
            }
            String lightType = String.valueOf(parameters.get("lightType"));
            if (!ALLOWED_TYPES.contains(lightType)) {
                return "Invalid lightType '" + lightType + "'. Allowed: " + ALLOWED_TYPES;
            }

            if (parameters.containsKey("color")) {
                String colErr = checkColor(parameters.get("color"), "color");
                if (colErr != null) return colErr;
            }

            if (parameters.containsKey("intensity") && !(parameters.get("intensity") instanceof Number)) {
                return "Parameter 'intensity' must be a number";
            }

            String posErr = checkVector3(parameters.get("position"), "position");
            if (posErr != null) return posErr;

            String rotErr = checkVector3(parameters.get("rotation"), "rotation");
            if (rotErr != null) return rotErr;

            return null;
        }
    }

    @Component
    public static class CreateCamera implements Tool {
        @Override
        public String name() { return "create_camera"; }

        @Override
        public String description() {
            return "Creates or configures a Camera with position, rotation, FOV, clear flags, and tag.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Camera GameObject name"));
            props.put("position", vector3Schema("Camera position"));
            props.put("rotation", vector3Schema("Camera Euler rotation"));
            props.put("fieldOfView", Map.of("type", "number", "description", "Field of view angle"));
            props.put("isMainCamera", Map.of("type", "boolean", "description", "Tag as MainCamera"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            String posErr = checkVector3(parameters.get("position"), "position");
            if (posErr != null) return posErr;

            String rotErr = checkVector3(parameters.get("rotation"), "rotation");
            if (rotErr != null) return rotErr;

            if (parameters.containsKey("fieldOfView") && !(parameters.get("fieldOfView") instanceof Number)) {
                return "Parameter 'fieldOfView' must be a number";
            }

            return null;
        }
    }

    // ==========================================
    // Phase 5: Diagnostic & Simulation Tools
    // ==========================================

    @Component
    public static class SetPlayMode implements Tool {
        @Override
        public String name() { return "set_play_mode"; }

        @Override
        public String description() {
            return "Toggles Unity Play Mode (play: true/false) to test game simulation or return to edit mode.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("play", Map.of("type", "boolean", "description", "True to enter play mode, false to return to edit mode"));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", List.of("play"));
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("play")) {
                return "Parameter 'play' (boolean) is required";
            }
            Object val = parameters.get("play");
            if (!(val instanceof Boolean) && !"true".equalsIgnoreCase(String.valueOf(val)) && !"false".equalsIgnoreCase(String.valueOf(val))) {
                return "Parameter 'play' must be a boolean";
            }
            return null;
        }
    }

    @Component
    public static class GetConsoleLogs implements Tool {
        @Override
        public String name() { return "get_console_logs"; }

        @Override
        public String description() {
            return "Retrieves recent console messages, warnings, and errors from the Unity Editor console.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("count", Map.of("type", "integer", "description", "Maximum number of logs to retrieve (default 50)"));
            props.put("logType", Map.of(
                    "type", "string",
                    "enum", List.of("All", "Error", "Warning", "Log"),
                    "description", "Filter logs by type"
            ));

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            if (parameters != null && parameters.containsKey("count")) {
                if (!(parameters.get("count") instanceof Number)) {
                    return "Parameter 'count' must be a positive number";
                }
            }
            if (parameters != null && parameters.containsKey("logType")) {
                String t = String.valueOf(parameters.get("logType"));
                if (!Set.of("Error", "Warning", "Log", "All").contains(t)) {
                    return "Parameter 'logType' must be one of: 'Error', 'Warning', 'Log', 'All'";
                }
            }
            return null;
        }
    }

    // ==========================================
    // Phase 3: Unity Perception Tools
    // ==========================================

    @Component
    public static class GetActiveScene implements Tool {
        @Override
        public String name() { return "get_active_scene"; }

        @Override
        public String description() {
            return "Get details about the currently active Unity scene, including name, path, dirty state, and root object count.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            return null;
        }
    }

    @Component
    public static class GetSelectedObject implements Tool {
        @Override
        public String name() { return "get_selected_object"; }

        @Override
        public String description() {
            return "Get details about the currently selected GameObject in the Unity Editor (objectId, name, hierarchy path, active state, components, and transform).";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR); }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            return null;
        }
    }

    @Component
    public static class GetObjectComponents implements Tool {
        @Override
        public String name() { return "get_object_components"; }

        @Override
        public String description() {
            return "Get the list of components attached to a GameObject specified by name or objectId.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "GameObject name, hierarchy path, or objectId (e.g. 'obj_123')"));
            props.put("objectId", Map.of("type", "string", "description", "Optional explicit objectId (e.g. 'obj_123')"));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            return checkTarget(parameters);
        }
    }

    @Component
    public static class GetObjectTransform implements Tool {
        @Override
        public String name() { return "get_object_transform"; }

        @Override
        public String description() {
            return "Get complete transform information (position, rotation, scale, local transform, forward/up/right vectors, parent, child count) of a GameObject by name or objectId.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "GameObject name, hierarchy path, or objectId (e.g. 'obj_123')"));
            props.put("objectId", Map.of("type", "string", "description", "Optional explicit objectId (e.g. 'obj_123')"));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            return checkTarget(parameters);
        }
    }

    @Component
    public static class GetPlayModeState implements Tool {
        @Override
        public String name() { return "get_play_mode_state"; }

        @Override
        public String description() {
            return "Get the current Unity Editor execution state (isPlaying, isPaused, isCompiling, state).";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            return null;
        }
    }

    @Component
    public static class GetConsoleErrors implements Tool {
        @Override
        public String name() { return "get_console_errors"; }

        @Override
        public String description() {
            return "Get recent console errors and exceptions from the Unity Editor console.";
        }

        @Override
        public ToolPermission permission() { return ToolPermission.SAFE; }

        @Override
        public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("count", Map.of("type", "integer", "description", "Maximum number of recent errors to retrieve (default 20, max 100)"));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", props);
            return schema;
        }

        @Override
        public String validate(Map<String, Object> parameters) {
            if (parameters != null && parameters.containsKey("count")) {
                Object val = parameters.get("count");
                if (!(val instanceof Number n) || n.intValue() <= 0) {
                    return "Parameter 'count' must be a positive integer";
                }
            }
            return null;
        }
    }

    // ==========================================
    // Phase 6 Tools: Script Management
    // ==========================================

    @Component
    public static class CreateScript implements Tool {
        private static final ScriptSafetyValidator validator = new ScriptSafetyValidator();

        @Override public String name() { return "create_script"; }
        @Override public String description() {
            return "Create a new C# script inside Assets/ with strict path sandboxing and dangerous C# API safety validation.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SUPERVISED; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public String domain() { return "SCRIPT"; }
        @Override public ToolRiskLevel riskLevel() { return ToolRiskLevel.MEDIUM; }
        @Override public List<String> produces() { return List.of("SCRIPT"); }
        @Override public String validationRequired() { return "COMPILE_SUCCESS"; }

        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("path", Map.of("type", "string", "description", "Script path starting with Assets/ and ending with .cs (e.g. 'Assets/Scripts/PlayerController.cs')"));
            props.put("content", Map.of("type", "string", "description", "Complete C# source code"));
            return Map.of("type", "object", "properties", props, "required", List.of("path", "content"));
        }

        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("path") || !parameters.containsKey("content")) {
                return "Parameters 'path' and 'content' are required";
            }
            String path = String.valueOf(parameters.get("path"));
            String content = String.valueOf(parameters.get("content"));
            ScriptSafetyValidator.ValidationResult res = validator.validate(path, content);
            return res.isValid() ? null : res.getViolationMessage();
        }
    }

    @Component
    public static class ReadScript implements Tool {
        private static final ScriptSafetyValidator validator = new ScriptSafetyValidator();

        @Override public String name() { return "read_script"; }
        @Override public String description() {
            return "Read the C# source code, line count, and SHA-256 hash of a script inside Assets/.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public String domain() { return "SCRIPT"; }

        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string", "description", "Script path in Assets/")), "required", List.of("path"));
        }

        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("path")) return "Parameter 'path' is required";
            ScriptSafetyValidator.ValidationResult res = validator.validatePath(String.valueOf(parameters.get("path")));
            return res.isValid() ? null : res.getViolationMessage();
        }
    }

    @Component
    public static class UpdateScript implements Tool {
        private static final ScriptSafetyValidator validator = new ScriptSafetyValidator();

        @Override public String name() { return "update_script"; }
        @Override public String description() {
            return "Update an existing C# script using optimistic concurrency. Requires previousHash matching current file hash.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SUPERVISED; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public String domain() { return "SCRIPT"; }
        @Override public ToolRiskLevel riskLevel() { return ToolRiskLevel.MEDIUM; }
        @Override public List<String> modifies() { return List.of("SCRIPT"); }
        @Override public String validationRequired() { return "COMPILE_SUCCESS"; }

        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("path", Map.of("type", "string", "description", "Script path in Assets/"));
            props.put("previousHash", Map.of("type", "string", "description", "Current SHA-256 hash of the script from read_script"));
            props.put("content", Map.of("type", "string", "description", "Updated C# source code"));
            return Map.of("type", "object", "properties", props, "required", List.of("path", "content"));
        }

        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("path") || !parameters.containsKey("content")) {
                return "Parameters 'path' and 'content' are required";
            }
            String path = String.valueOf(parameters.get("path"));
            String content = String.valueOf(parameters.get("content"));
            ScriptSafetyValidator.ValidationResult res = validator.validate(path, content);
            return res.isValid() ? null : res.getViolationMessage();
        }
    }

    @Component
    public static class DeleteScript implements Tool {
        private static final ScriptSafetyValidator validator = new ScriptSafetyValidator();

        @Override public String name() { return "delete_script"; }
        @Override public String description() {
            return "Delete a C# script inside Assets/. Inspects active scene for attached components before deletion.";
        }
        @Override public ToolPermission permission() { return ToolPermission.DESTRUCTIVE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }

        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string", "description", "Script path in Assets/")), "required", List.of("path"));
        }

        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("path")) return "Parameter 'path' is required";
            ScriptSafetyValidator.ValidationResult res = validator.validatePath(String.valueOf(parameters.get("path")));
            return res.isValid() ? null : res.getViolationMessage();
        }
    }

    @Component
    public static class ListScripts implements Tool {
        @Override public String name() { return "list_scripts"; }
        @Override public String description() {
            return "List all C# scripts in Assets/ or a subfolder with their paths, hashes, and sizes.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("path", Map.of("type", "string", "description", "Folder path (default 'Assets')"));
            props.put("recursive", Map.of("type", "boolean", "description", "Whether to search subdirectories (default true)"));
            return Map.of("type", "object", "properties", props);
        }

        @Override public String validate(Map<String, Object> parameters) { return null; }
    }

    // ==========================================
    // Phase 6 Tools: Compilation & Runtime
    // ==========================================

    @Component
    public static class GetProjectInfo implements Tool {
        @Override public String name() { return "get_project_info"; }
        @Override public String description() {
            return "Retrieve Unity version, project path, active scene, compilation status, and Play Mode state.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override public String validate(Map<String, Object> parameters) { return null; }
    }

    @Component
    public static class CompileProject implements Tool {
        @Override public String name() { return "compile_project"; }
        @Override public String description() {
            return "Trigger synchronous Unity asset refresh and script compilation. Waits for completion and returns structured diagnostics (CS error codes, file, line, message).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public String domain() { return "SCRIPT"; }
        @Override public List<String> produces() { return List.of("COMPILE_SUCCESS"); }

        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override public String validate(Map<String, Object> parameters) { return null; }
    }

    @Component
    public static class EnterPlayMode implements Tool {
        @Override public String name() { return "enter_play_mode"; }
        @Override public String description() {
            return "Enter Unity Play Mode. Verifies compilation is successful first and records baseline console errors.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public String domain() { return "PLAY_MODE"; }
        @Override public List<String> prerequisites() { return List.of("COMPILE_SUCCESS"); }
        @Override public List<String> produces() { return List.of("PLAY_MODE"); }

        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("runtimeTestId", Map.of("type", "string", "description", "Optional unique test identifier"));
            return Map.of("type", "object", "properties", props);
        }

        @Override public String validate(Map<String, Object> parameters) { return null; }
    }

    @Component
    public static class ExitPlayMode implements Tool {
        @Override public String name() { return "exit_play_mode"; }
        @Override public String description() {
            return "Exit Unity Play Mode and collect errors and exceptions generated during the test session.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.PLAY_MODE, ToolMode.BOTH); }
        @Override public String domain() { return "PLAY_MODE"; }

        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override public String validate(Map<String, Object> parameters) { return null; }
    }

    @Component
    public static class RunGameTest implements Tool {
        @Override public String name() { return "run_game_test"; }
        @Override public String description() {
            return "Run behavioral runtime test on a GameObject in Play Mode (e.g. testing player movement response to simulated input).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.PLAY_MODE, ToolMode.BOTH); }
        @Override public String domain() { return "TESTING"; }
        @Override public List<String> prerequisites() { return List.of("PLAY_MODE"); }

        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject to test (e.g. 'Player')"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }

        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
    }

    @Component
    public static class ValidateGameState implements Tool {
        @Override public String name() { return "validate_game_state"; }
        @Override public String description() {
            return "Objectively validate scene state, GameObjects, components, scripts, compilation, and runtime errors against goal requirements.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }

        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("requirements", Map.of("type", "string", "description", "Optional JSON array of requirement specifications to evaluate"));
            return Map.of("type", "object", "properties", props);
        }

        @Override public String validate(Map<String, Object> parameters) { return null; }
    }
}

