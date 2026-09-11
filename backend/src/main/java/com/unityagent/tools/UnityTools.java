package com.unityagent.tools;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Registry of tool definitions for the Autonomous Unity Agent.
 * Contains tool metadata, documentation, and parameter validation.
 * Each inner class is a Spring @Component that self-registers into ToolRegistry.
 */
public class UnityTools {

    // --- Validation Utilities ---

    private static String checkTarget(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("target") || parameters.get("target") == null) {
            return "Parameter 'target' (GameObject name or path) is required";
        }
        if (!(parameters.get("target") instanceof String s) || s.isBlank()) {
            return "Parameter 'target' must be a non-empty string";
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
        public String validate(Map<String, Object> parameters) {
            if (parameters != null && parameters.containsKey("rootOnly")) {
                if (!(parameters.get("rootOnly") instanceof Boolean)) {
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
            return "Creates a new scene in the Unity Editor with optional default setup (EmptyScene or DefaultGame).";
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
        public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("play")) {
                return "Parameter 'play' (boolean) is required";
            }
            if (!(parameters.get("play") instanceof Boolean)) {
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
}
