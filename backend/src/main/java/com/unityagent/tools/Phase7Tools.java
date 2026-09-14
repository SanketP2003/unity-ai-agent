package com.unityagent.tools;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Spring Component definitions for all Phase 7 Advanced Unity Tools across 16 domains:
 * Scene, GameObject, Generic Components, Prefabs, Materials & Visuals, Lighting,
 * Cameras, UI Systems, Physics, Navigation, Animation, Audio, Input, Assets,
 * Project Configuration, and Build Pipeline.
 */
public class Phase7Tools {

    // --- Schema Builder Utilities ---

    private static Map<String, Object> vector2Schema(String description) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("x", Map.of("type", "number", "description", "X coordinate"));
        props.put("y", Map.of("type", "number", "description", "Y coordinate"));

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("description", description);
        s.put("properties", props);
        s.put("required", List.of("x", "y"));
        return s;
    }

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
                "description", description + " (Hex color string e.g. '#FF0000' or '#33AA33FF')"
        );
    }

    // ==========================================
    // 1. Scene Management Tools
    // ==========================================

    @Component
    public static class OpenScene implements Tool {
        @Override public String name() { return "open_scene"; }
        @Override public String description() {
            return "Opens an existing scene asset by its asset path (e.g. 'Assets/Scenes/Main.unity') or scene name.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("scenePath", Map.of("type", "string", "description", "Relative asset path to scene file, e.g. 'Assets/Scenes/SampleScene.unity'"));
            props.put("sceneName", Map.of("type", "string", "description", "Optional name of the scene to find and open"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || (!parameters.containsKey("scenePath") && !parameters.containsKey("sceneName") && !parameters.containsKey("name"))) {
                return "Parameter 'scenePath' or 'sceneName' is required";
            }
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Scene", List.of("SceneRecovery"));
        }
    }

    @Component
    public static class GetSceneInfo implements Tool {
        @Override public String name() { return "get_scene_info"; }
        @Override public String description() {
            return "Get detailed information about the active scene (name, path, isDirty, rootCount, cameraCount, lightCount, fog, ambientLight).";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Scene", List.of());
        }
    }

    // ==========================================
    // 2. GameObject Management Tools
    // ==========================================

    @Component
    public static class CreateGameObject implements Tool {
        @Override public String name() { return "create_gameobject"; }
        @Override public String description() {
            return "Creates a GameObject with optional primitive type, transform, parent, tag, layer, and idempotency.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Name of the GameObject to create"));
            props.put("primitiveType", Map.of("type", "string", "description", "Optional primitive type: Cube, Sphere, Capsule, Cylinder, Plane, Quad, or Empty"));
            props.put("position", vector3Schema("Initial world/local position"));
            props.put("rotation", vector3Schema("Initial rotation Euler angles"));
            props.put("scale", vector3Schema("Initial local scale (defaults to 1,1,1)"));
            props.put("parent", Map.of("type", "string", "description", "Optional parent GameObject name or ID"));
            props.put("tag", Map.of("type", "string", "description", "Optional Unity tag"));
            props.put("layer", Map.of("type", "string", "description", "Optional layer name or index"));
            props.put("active", Map.of("type", "boolean", "description", "Active state (defaults to true)"));
            props.put("idempotent", Map.of("type", "boolean", "description", "If true, updates existing object if already present instead of creating duplicate"));
            return Map.of("type", "object", "properties", props, "required", List.of("name"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("name") || !(parameters.get("name") instanceof String s) || s.isBlank()) {
                return "Parameter 'name' is required and must be non-empty";
            }
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "GameObject", List.of("ObjectRecovery"));
        }
    }

    @Component
    public static class DuplicateGameObject implements Tool {
        @Override public String name() { return "duplicate_gameobject"; }
        @Override public String description() {
            return "Duplicates an existing GameObject with all its components and children.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID to duplicate"));
            props.put("newName", Map.of("type", "string", "description", "Optional new name for the duplicated GameObject"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "GameObject", List.of("ObjectRecovery"));
        }
    }

    @Component
    public static class RenameGameObject implements Tool {
        @Override public String name() { return "rename_gameobject"; }
        @Override public String description() {
            return "Renames an existing GameObject.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID"));
            props.put("newName", Map.of("type", "string", "description", "New name for the GameObject"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "newName"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("newName")) return "Parameter 'newName' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "GameObject", List.of("ObjectRecovery"));
        }
    }

    @Component
    public static class MoveGameObject implements Tool {
        @Override public String name() { return "move_gameobject"; }
        @Override public String description() {
            return "Moves or offsets a GameObject in world or local space.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID"));
            props.put("position", vector3Schema("Absolute target position"));
            props.put("isLocal", Map.of("type", "boolean", "description", "Whether position/offsets are in local space"));
            props.put("dx", Map.of("type", "number", "description", "Delta X offset"));
            props.put("dy", Map.of("type", "number", "description", "Delta Y offset"));
            props.put("dz", Map.of("type", "number", "description", "Delta Z offset"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "GameObject", List.of("TransformRecovery"));
        }
    }

    @Component
    public static class FindGameObjects implements Tool {
        @Override public String name() { return "find_gameobjects"; }
        @Override public String description() {
            return "Finds GameObjects in the active scene by name pattern, tag, layer, or component type.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("namePattern", Map.of("type", "string", "description", "Substring or pattern to match object names"));
            props.put("tag", Map.of("type", "string", "description", "Tag to filter by"));
            props.put("layer", Map.of("type", "string", "description", "Layer name or number to filter by"));
            props.put("componentType", Map.of("type", "string", "description", "Component class name required on the object"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "GameObject", List.of());
        }
    }

    // ==========================================
    // 3. Generic Component System Tools
    // ==========================================

    @Component
    public static class GetComponent implements Tool {
        @Override public String name() { return "get_component"; }
        @Override public String description() {
            return "Inspects whether a specific component exists on a GameObject and returns its high-level status.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID"));
            props.put("componentType", Map.of("type", "string", "description", "Component class name (e.g. 'Rigidbody', 'Camera', 'PlayerController')"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "componentType"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("componentType")) return "Parameter 'componentType' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Component", List.of());
        }
    }

    @Component
    public static class GetComponentProperties implements Tool {
        @Override public String name() { return "get_component_properties"; }
        @Override public String description() {
            return "Reflects all public fields and properties of a component on a GameObject with their current values and types.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID"));
            props.put("componentType", Map.of("type", "string", "description", "Component class name (e.g. 'Light', 'BoxCollider', 'AudioSource')"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "componentType"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("componentType")) return "Parameter 'componentType' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Component", List.of());
        }
    }

    // ==========================================
    // 4. Prefab Lifecycle Tools
    // ==========================================

    @Component
    public static class CreatePrefab implements Tool {
        @Override public String name() { return "create_prefab"; }
        @Override public String description() {
            return "Saves a GameObject from the scene as a reusable Prefab asset at the specified project path.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID in the scene"));
            props.put("assetPath", Map.of("type", "string", "description", "Destination asset path, e.g. 'Assets/Prefabs/Player.prefab'"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "assetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("assetPath")) return "Parameter 'assetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Prefab", List.of("AssetRecovery"));
        }
    }

    @Component
    public static class OpenPrefab implements Tool {
        @Override public String name() { return "open_prefab"; }
        @Override public String description() {
            return "Opens a Prefab asset in Prefab Isolation Mode for direct editing.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("prefabAssetPath", Map.of("type", "string", "description", "Path to prefab asset, e.g. 'Assets/Prefabs/Enemy.prefab'"));
            return Map.of("type", "object", "properties", props, "required", List.of("prefabAssetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("prefabAssetPath")) return "Parameter 'prefabAssetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Prefab", List.of());
        }
    }

    @Component
    public static class UpdatePrefab implements Tool {
        @Override public String name() { return "update_prefab"; }
        @Override public String description() {
            return "Applies modifications to a Prefab asset on disk.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("prefabAssetPath", Map.of("type", "string", "description", "Path to prefab asset"));
            props.put("modifications", Map.of("type", "object", "description", "Dictionary of property modifications"));
            return Map.of("type", "object", "properties", props, "required", List.of("prefabAssetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("prefabAssetPath")) return "Parameter 'prefabAssetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Prefab", List.of());
        }
    }

    @Component
    public static class InstantiatePrefab implements Tool {
        @Override public String name() { return "instantiate_prefab"; }
        @Override public String description() {
            return "Instantiates a Prefab asset into the active scene at the given position, rotation, and parent.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("prefabAssetPath", Map.of("type", "string", "description", "Path to prefab asset (e.g. 'Assets/Prefabs/Enemy.prefab')"));
            props.put("position", vector3Schema("Spawn position"));
            props.put("rotation", vector3Schema("Spawn Euler rotation"));
            props.put("parent", Map.of("type", "string", "description", "Optional parent GameObject"));
            props.put("instanceName", Map.of("type", "string", "description", "Optional custom name for the instantiated GameObject"));
            return Map.of("type", "object", "properties", props, "required", List.of("prefabAssetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("prefabAssetPath")) return "Parameter 'prefabAssetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Prefab", List.of("ObjectRecovery"));
        }
    }

    @Component
    public static class GetPrefabInfo implements Tool {
        @Override public String name() { return "get_prefab_info"; }
        @Override public String description() {
            return "Retrieves information about a Prefab asset (root GameObject name, components, hierarchy).";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("prefabAssetPath", Map.of("type", "string", "description", "Path to prefab asset"));
            return Map.of("type", "object", "properties", props, "required", List.of("prefabAssetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("prefabAssetPath")) return "Parameter 'prefabAssetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Prefab", List.of());
        }
    }

    @Component
    public static class ApplyPrefabChanges implements Tool {
        @Override public String name() { return "apply_prefab_changes"; }
        @Override public String description() {
            return "Applies overrides from a scene prefab instance back to its source Prefab asset.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("instanceTarget", Map.of("type", "string", "description", "GameObject instance in scene"));
            return Map.of("type", "object", "properties", props, "required", List.of("instanceTarget"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || (!parameters.containsKey("instanceTarget") && !parameters.containsKey("target"))) {
                return "Parameter 'instanceTarget' is required";
            }
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Prefab", List.of());
        }
    }

    // ==========================================
    // 5. Materials & Visual Tools
    // ==========================================

    @Component
    public static class SetMaterialProperty implements Tool {
        @Override public String name() { return "set_material_property"; }
        @Override public String description() {
            return "Sets a shader property on a Material asset (Color, Float, Vector, Texture, Int).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("materialAssetPath", Map.of("type", "string", "description", "Asset path to Material, e.g. 'Assets/Materials/PlayerMat.mat'"));
            props.put("propertyName", Map.of("type", "string", "description", "Shader property name, e.g. '_Color', '_Metallic', '_Smoothness'"));
            props.put("value", Map.of("type", "string", "description", "Value to set (color hex string, float, or vector string)"));
            props.put("valueType", Map.of("type", "string", "description", "Type of value: Color, Float, Vector, Int, or Texture"));
            return Map.of("type", "object", "properties", props, "required", List.of("materialAssetPath", "propertyName", "value"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null) return "Parameters required";
            if (!parameters.containsKey("materialAssetPath") && !parameters.containsKey("materialPath") 
                    && !parameters.containsKey("path") && !parameters.containsKey("target")) {
                return "Parameter 'materialAssetPath' or 'target' is required";
            }
            if (!parameters.containsKey("propertyName") && !parameters.containsKey("property")) return "Parameter 'propertyName' is required";
            if (!parameters.containsKey("value")) return "Parameter 'value' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Material", List.of("MaterialRecovery"));
        }
    }

    @Component
    public static class GetMaterialProperties implements Tool {
        @Override public String name() { return "get_material_properties"; }
        @Override public String description() {
            return "Reads all exposed shader properties and colors from a Material asset.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("materialAssetPath", Map.of("type", "string", "description", "Path to Material asset"));
            return Map.of("type", "object", "properties", props, "required", List.of("materialAssetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null) return "Parameters required";
            if (!parameters.containsKey("materialAssetPath") && !parameters.containsKey("materialPath") 
                    && !parameters.containsKey("path") && !parameters.containsKey("target")) {
                return "Parameter 'materialAssetPath' or 'target' is required";
            }
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Material", List.of());
        }
    }

    @Component
    public static class AssignMaterial implements Tool {
        @Override public String name() { return "assign_material"; }
        @Override public String description() {
            return "Assigns a Material asset to the Renderer component of a GameObject.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID"));
            props.put("materialAssetPath", Map.of("type", "string", "description", "Asset path to Material"));
            props.put("slotIndex", Map.of("type", "integer", "description", "Material slot index (default 0)"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "materialAssetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("materialAssetPath") && !parameters.containsKey("materialPath") 
                    && !parameters.containsKey("materialName") && !parameters.containsKey("path")) {
                return "Parameter 'materialAssetPath' or 'materialPath' is required";
            }
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Material", List.of("MaterialRecovery"));
        }
    }

    @Component
    public static class SetRendererMaterial implements Tool {
        @Override public String name() { return "set_renderer_material"; }
        @Override public String description() {
            return "Assigns a Material to a Renderer at a specific slot index.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject"));
            props.put("materialAssetPath", Map.of("type", "string", "description", "Material asset path"));
            props.put("slotIndex", Map.of("type", "integer", "description", "Slot index"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "materialAssetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("materialAssetPath") && !parameters.containsKey("materialPath") 
                    && !parameters.containsKey("materialName") && !parameters.containsKey("path")) {
                return "Parameter 'materialAssetPath' or 'materialPath' is required";
            }
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Material", List.of("MaterialRecovery"));
        }
    }

    @Component
    public static class CreateShaderGraphAsset implements Tool {
        @Override public String name() { return "create_shader_graph_asset"; }
        @Override public String description() {
            return "Creates a new Shader Graph or custom shader asset at the specified path.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("assetPath", Map.of("type", "string", "description", "Destination asset path"));
            props.put("shaderType", Map.of("type", "string", "description", "Shader type: Lit, Unlit, Decal, Sprite"));
            return Map.of("type", "object", "properties", props, "required", List.of("assetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("assetPath")) return "Parameter 'assetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Material", List.of());
        }
    }

    // ==========================================
    // 6. Lighting & Environment Tools
    // ==========================================

    @Component
    public static class ConfigureLight implements Tool {
        @Override public String name() { return "configure_light"; }
        @Override public String description() {
            return "Configures an existing Light component (type, color, intensity, range, spotAngle, shadows).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID with Light component"));
            props.put("lightType", Map.of("type", "string", "description", "Directional, Point, Spot, or Area"));
            props.put("color", colorSchema("Light color"));
            props.put("intensity", Map.of("type", "number", "description", "Light intensity"));
            props.put("range", Map.of("type", "number", "description", "Point/Spot light range"));
            props.put("spotAngle", Map.of("type", "number", "description", "Spot light cone angle"));
            props.put("shadows", Map.of("type", "string", "description", "None, Hard, or Soft"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Lighting", List.of("LightingRecovery"));
        }
    }

    @Component
    public static class GetLights implements Tool {
        @Override public String name() { return "get_lights"; }
        @Override public String description() {
            return "Lists all Light components in the active scene with their types, colors, and intensities.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Lighting", List.of());
        }
    }

    @Component
    public static class ConfigureEnvironmentLighting implements Tool {
        @Override public String name() { return "configure_environment_lighting"; }
        @Override public String description() {
            return "Configures ambient lighting, ambient color, skybox material, and intensity multiplier in RenderSettings.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("ambientMode", Map.of("type", "string", "description", "Skybox, Trilight, Flat"));
            props.put("ambientColor", colorSchema("Ambient light color"));
            props.put("skyboxMaterialPath", Map.of("type", "string", "description", "Asset path to skybox material"));
            props.put("intensityMultiplier", Map.of("type", "number", "description", "Ambient intensity multiplier"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Lighting", List.of("LightingRecovery"));
        }
    }

    @Component
    public static class ConfigureFog implements Tool {
        @Override public String name() { return "configure_fog"; }
        @Override public String description() {
            return "Configures scene fog parameters (enabled, color, mode, density, startDistance, endDistance).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("enabled", Map.of("type", "boolean", "description", "Enable or disable fog"));
            props.put("color", colorSchema("Fog color"));
            props.put("mode", Map.of("type", "string", "description", "Linear, Exponential, ExponentialSquared"));
            props.put("density", Map.of("type", "number", "description", "Fog density for exponential modes"));
            props.put("startDistance", Map.of("type", "number", "description", "Linear fog start distance"));
            props.put("endDistance", Map.of("type", "number", "description", "Linear fog end distance"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Lighting", List.of());
        }
    }

    // ==========================================
    // 7. Camera Systems Tools
    // ==========================================

    @Component
    public static class ConfigureCamera implements Tool {
        @Override public String name() { return "configure_camera"; }
        @Override public String description() {
            return "Configures an existing Camera component (projection, FOV, clipping planes, clear flags, background).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID with Camera component"));
            props.put("projection", Map.of("type", "string", "description", "Perspective or Orthographic"));
            props.put("fieldOfView", Map.of("type", "number", "description", "Field of view in degrees"));
            props.put("orthographicSize", Map.of("type", "number", "description", "Orthographic camera half-size"));
            props.put("nearClip", Map.of("type", "number", "description", "Near clipping plane distance"));
            props.put("farClip", Map.of("type", "number", "description", "Far clipping plane distance"));
            props.put("clearFlags", Map.of("type", "string", "description", "Skybox, SolidColor, Depth, Nothing"));
            props.put("backgroundColor", colorSchema("Background clear color"));
            props.put("depth", Map.of("type", "number", "description", "Camera rendering depth order"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Camera", List.of("CameraRecovery"));
        }
    }

    @Component
    public static class SetActiveCamera implements Tool {
        @Override public String name() { return "set_active_camera"; }
        @Override public String description() {
            return "Sets the designated Camera GameObject as the active main camera and enables it.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target Camera GameObject name or ID"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Camera", List.of("CameraRecovery"));
        }
    }

    @Component
    public static class FollowTarget implements Tool {
        @Override public String name() { return "follow_target"; }
        @Override public String description() {
            return "Attaches or configures a smooth camera follower component to follow a target GameObject with an offset.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("cameraTarget", Map.of("type", "string", "description", "Camera GameObject name"));
            props.put("followTarget", Map.of("type", "string", "description", "Target GameObject to follow (e.g. 'Player')"));
            props.put("offset", vector3Schema("Camera position offset relative to target"));
            props.put("smoothSpeed", Map.of("type", "number", "description", "Follow interpolation speed (default 5.0)"));
            return Map.of("type", "object", "properties", props, "required", List.of("cameraTarget", "followTarget"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("cameraTarget")) return "Parameter 'cameraTarget' is required";
            if (!parameters.containsKey("followTarget")) return "Parameter 'followTarget' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Camera", List.of("CameraRecovery"));
        }
    }

    @Component
    public static class LookAtTarget implements Tool {
        @Override public String name() { return "look_at_target"; }
        @Override public String description() {
            return "Rotates a camera or GameObject to point directly at a target GameObject.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("cameraTarget", Map.of("type", "string", "description", "GameObject to rotate"));
            props.put("lookAtTarget", Map.of("type", "string", "description", "Target GameObject to look at"));
            return Map.of("type", "object", "properties", props, "required", List.of("cameraTarget", "lookAtTarget"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("cameraTarget")) return "Parameter 'cameraTarget' is required";
            if (!parameters.containsKey("lookAtTarget")) return "Parameter 'lookAtTarget' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Camera", List.of());
        }
    }

    @Component
    public static class GetCameraInfo implements Tool {
        @Override public String name() { return "get_camera_info"; }
        @Override public String description() {
            return "Returns camera parameters (FOV, projection, clipping planes, clear flags) for a Camera.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Camera GameObject name or ID (optional, defaults to MainCamera)"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Camera", List.of());
        }
    }

    // ==========================================
    // 8. UI Systems Tools
    // ==========================================

    @Component
    public static class CreateCanvas implements Tool {
        @Override public String name() { return "create_canvas"; }
        @Override public String description() {
            return "Creates a Canvas with CanvasScaler, GraphicRaycaster, and EventSystem for UI rendering.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Name of the Canvas GameObject (default 'Canvas')"));
            props.put("renderMode", Map.of("type", "string", "description", "ScreenSpaceOverlay, ScreenSpaceCamera, or WorldSpace"));
            props.put("referenceResolution", vector2Schema("Reference resolution (e.g. 1920, 1080)"));
            props.put("matchWidthOrHeight", Map.of("type", "number", "description", "Match scale (0 = width, 1 = height)"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    @Component
    public static class CreatePanel implements Tool {
        @Override public String name() { return "create_panel"; }
        @Override public String description() {
            return "Creates a UI Panel (Image background) under a Canvas or parent UI element.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Panel GameObject name"));
            props.put("parent", Map.of("type", "string", "description", "Parent Canvas or UI element name"));
            props.put("color", colorSchema("Panel background color"));
            props.put("anchorPreset", Map.of("type", "string", "description", "Stretch, Top, Bottom, Left, Right, Center"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    @Component
    public static class CreateText implements Tool {
        @Override public String name() { return "create_text"; }
        @Override public String description() {
            return "Creates a UI Text element (supporting Legacy Text or TextMeshProUGUI).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Text GameObject name"));
            props.put("parent", Map.of("type", "string", "description", "Parent UI element or Canvas"));
            props.put("text", Map.of("type", "string", "description", "Text string to display"));
            props.put("fontSize", Map.of("type", "integer", "description", "Font size"));
            props.put("color", colorSchema("Text color"));
            props.put("alignment", Map.of("type", "string", "description", "MiddleCenter, UpperLeft, LowerRight, etc."));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    @Component
    public static class CreateImage implements Tool {
        @Override public String name() { return "create_image"; }
        @Override public String description() {
            return "Creates a UI Image element under a parent UI element or Canvas.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Image GameObject name"));
            props.put("parent", Map.of("type", "string", "description", "Parent UI element"));
            props.put("spritePath", Map.of("type", "string", "description", "Optional asset path to Sprite"));
            props.put("color", colorSchema("Image tint color"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    @Component
    public static class CreateButton implements Tool {
        @Override public String name() { return "create_button"; }
        @Override public String description() {
            return "Creates an interactive UI Button with child text label and customizable transition colors.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Button GameObject name"));
            props.put("parent", Map.of("type", "string", "description", "Parent UI element"));
            props.put("buttonText", Map.of("type", "string", "description", "Label text on the button"));
            props.put("normalColor", colorSchema("Normal state color"));
            props.put("highlightedColor", colorSchema("Hover state color"));
            props.put("pressedColor", colorSchema("Pressed state color"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    @Component
    public static class CreateSlider implements Tool {
        @Override public String name() { return "create_slider"; }
        @Override public String description() {
            return "Creates a UI Slider element for health bars, volume controls, or progress bars.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Slider GameObject name"));
            props.put("parent", Map.of("type", "string", "description", "Parent UI element"));
            props.put("minValue", Map.of("type", "number", "description", "Minimum value (default 0)"));
            props.put("maxValue", Map.of("type", "number", "description", "Maximum value (default 100)"));
            props.put("defaultValue", Map.of("type", "number", "description", "Initial value (default 100)"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    @Component
    public static class CreateProgressBar implements Tool {
        @Override public String name() { return "create_progress_bar"; }
        @Override public String description() {
            return "Creates a progress / health bar with a background and filled foreground image.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", Map.of("type", "string", "description", "Progress bar GameObject name"));
            props.put("parent", Map.of("type", "string", "description", "Parent UI element"));
            props.put("fillRatio", Map.of("type", "number", "description", "Initial fill ratio from 0.0 to 1.0"));
            props.put("fillColor", colorSchema("Color of the filled portion"));
            props.put("backgroundColor", colorSchema("Background color"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    @Component
    public static class CreateUIElement implements Tool {
        @Override public String name() { return "create_ui_element"; }
        @Override public String description() {
            return "Creates a generic UI element of specified type (Canvas, Panel, Text, Image, Button, Slider, ProgressBar).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("elementType", Map.of("type", "string", "description", "Canvas, Panel, Text, Image, Button, Slider, or ProgressBar"));
            props.put("name", Map.of("type", "string", "description", "Element name"));
            props.put("parent", Map.of("type", "string", "description", "Parent GameObject name"));
            return Map.of("type", "object", "properties", props, "required", List.of("elementType"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("elementType")) return "Parameter 'elementType' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    @Component
    public static class SetUIProperty implements Tool {
        @Override public String name() { return "set_ui_property"; }
        @Override public String description() {
            return "Sets a property on a UI component (text, color, fontSize, fillAmount, value, interactable).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target UI GameObject name"));
            props.put("propertyName", Map.of("type", "string", "description", "Property name (text, color, fontSize, value, fillAmount, etc.)"));
            props.put("value", Map.of("type", "string", "description", "Property value string"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "propertyName", "value"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("propertyName")) return "Parameter 'propertyName' is required";
            if (!parameters.containsKey("value")) return "Parameter 'value' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    @Component
    public static class SetUILayout implements Tool {
        @Override public String name() { return "set_ui_layout"; }
        @Override public String description() {
            return "Sets RectTransform layout properties: anchorMin, anchorMax, pivot, anchoredPosition, and sizeDelta.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target UI GameObject"));
            props.put("anchorMin", vector2Schema("Anchor Min (e.g. 0,0 for bottom-left)"));
            props.put("anchorMax", vector2Schema("Anchor Max (e.g. 1,1 for top-right)"));
            props.put("pivot", vector2Schema("Pivot point (e.g. 0.5, 0.5 for center)"));
            props.put("anchoredPosition", vector2Schema("Anchored position offset"));
            props.put("sizeDelta", vector2Schema("Size delta width and height"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    @Component
    public static class BindUIEvent implements Tool {
        @Override public String name() { return "bind_ui_event"; }
        @Override public String description() {
            return "Binds a UI UnityEvent (like Button.onClick) to a method on a target component.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Button or UI GameObject with event"));
            props.put("eventName", Map.of("type", "string", "description", "Event name (default 'onClick')"));
            props.put("targetObject", Map.of("type", "string", "description", "GameObject containing listener component"));
            props.put("targetComponent", Map.of("type", "string", "description", "Component containing method"));
            props.put("targetMethod", Map.of("type", "string", "description", "Method name to invoke"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "targetObject", "targetComponent", "targetMethod"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("targetObject")) return "Parameter 'targetObject' is required";
            if (!parameters.containsKey("targetComponent")) return "Parameter 'targetComponent' is required";
            if (!parameters.containsKey("targetMethod")) return "Parameter 'targetMethod' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "UI", List.of("UIRecovery"));
        }
    }

    // ==========================================
    // 9. Physics Systems Tools
    // ==========================================

    @Component
    public static class ConfigureRigidbody implements Tool {
        @Override public String name() { return "configure_rigidbody"; }
        @Override public String description() {
            return "Configures a Rigidbody (mass, drag, useGravity, isKinematic, collisionDetectionMode, constraints).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID"));
            props.put("mass", Map.of("type", "number", "description", "Rigidbody mass"));
            props.put("drag", Map.of("type", "number", "description", "Linear drag"));
            props.put("angularDrag", Map.of("type", "number", "description", "Angular drag"));
            props.put("useGravity", Map.of("type", "boolean", "description", "Enable gravity"));
            props.put("isKinematic", Map.of("type", "boolean", "description", "Kinematic body"));
            props.put("collisionDetectionMode", Map.of("type", "string", "description", "Discrete, Continuous, ContinuousDynamic, ContinuousSpeculative"));
            props.put("freezePositionX", Map.of("type", "boolean", "description", "Freeze X position"));
            props.put("freezePositionY", Map.of("type", "boolean", "description", "Freeze Y position"));
            props.put("freezePositionZ", Map.of("type", "boolean", "description", "Freeze Z position"));
            props.put("freezeRotationX", Map.of("type", "boolean", "description", "Freeze X rotation"));
            props.put("freezeRotationY", Map.of("type", "boolean", "description", "Freeze Y rotation"));
            props.put("freezeRotationZ", Map.of("type", "boolean", "description", "Freeze Z rotation"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Physics", List.of("PhysicsRecovery"));
        }
    }

    @Component
    public static class ConfigureCollider implements Tool {
        @Override public String name() { return "configure_collider"; }
        @Override public String description() {
            return "Configures a Collider component (Box, Sphere, Capsule, Mesh, isTrigger, center, size, radius, height, material).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name or ID"));
            props.put("colliderType", Map.of("type", "string", "description", "Box, Sphere, Capsule, Mesh"));
            props.put("isTrigger", Map.of("type", "boolean", "description", "Whether collider acts as a trigger"));
            props.put("center", vector3Schema("Center offset"));
            props.put("size", vector3Schema("BoxCollider size"));
            props.put("radius", Map.of("type", "number", "description", "Sphere/Capsule collider radius"));
            props.put("height", Map.of("type", "number", "description", "Capsule collider height"));
            props.put("direction", Map.of("type", "integer", "description", "Capsule direction (0=X, 1=Y, 2=Z)"));
            props.put("physicsMaterialPath", Map.of("type", "string", "description", "Asset path to PhysicsMaterial"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Physics", List.of("PhysicsRecovery"));
        }
    }

    @Component
    public static class CreatePhysicsMaterial implements Tool {
        @Override public String name() { return "create_physics_material"; }
        @Override public String description() {
            return "Creates a PhysicsMaterial asset with dynamic/static friction and bounciness.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("assetPath", Map.of("type", "string", "description", "Destination asset path, e.g. 'Assets/Physics/Bouncy.physicMaterial'"));
            props.put("dynamicFriction", Map.of("type", "number", "description", "Dynamic friction (0.0 to 1.0)"));
            props.put("staticFriction", Map.of("type", "number", "description", "Static friction (0.0 to 1.0)"));
            props.put("bounciness", Map.of("type", "number", "description", "Bounciness (0.0 to 1.0)"));
            props.put("frictionCombine", Map.of("type", "string", "description", "Average, Minimum, Multiply, Maximum"));
            props.put("bounceCombine", Map.of("type", "string", "description", "Average, Minimum, Multiply, Maximum"));
            return Map.of("type", "object", "properties", props, "required", List.of("assetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("assetPath")) return "Parameter 'assetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Physics", List.of());
        }
    }

    @Component
    public static class ConfigureJoint implements Tool {
        @Override public String name() { return "configure_joint"; }
        @Override public String description() {
            return "Configures a Joint component (FixedJoint, HingeJoint, SpringJoint, CharacterJoint, ConfigurableJoint).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject"));
            props.put("jointType", Map.of("type", "string", "description", "FixedJoint, HingeJoint, SpringJoint, CharacterJoint, ConfigurableJoint"));
            props.put("connectedBody", Map.of("type", "string", "description", "Connected Rigidbody GameObject name"));
            props.put("anchor", vector3Schema("Joint anchor point"));
            props.put("axis", vector3Schema("Joint rotation axis"));
            props.put("breakForce", Map.of("type", "number", "description", "Break force threshold"));
            props.put("breakTorque", Map.of("type", "number", "description", "Break torque threshold"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Physics", List.of());
        }
    }

    @Component
    public static class SetGravity implements Tool {
        @Override public String name() { return "set_gravity"; }
        @Override public String description() {
            return "Sets the global physics gravity vector (Physics.gravity).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("gravity", vector3Schema("Gravity vector (default is 0, -9.81, 0)"));
            return Map.of("type", "object", "properties", props, "required", List.of("gravity"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("gravity")) return "Parameter 'gravity' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Physics", List.of());
        }
    }

    // ==========================================
    // 10. Navigation & AI Systems Tools
    // ==========================================

    @Component
    public static class ConfigureNavigation implements Tool {
        @Override public String name() { return "configure_navigation"; }
        @Override public String description() {
            return "Configures static navigation flags and area name for an environment object (e.g. Walkable, Not Walkable).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name"));
            props.put("areaName", Map.of("type", "string", "description", "NavMesh area name (Walkable, Not Walkable, Jump)"));
            props.put("isNavigationStatic", Map.of("type", "boolean", "description", "Whether object is static for NavMesh baking"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Navigation", List.of("NavigationRecovery"));
        }
    }

    @Component
    public static class BuildNavigation implements Tool {
        @Override public String name() { return "build_navigation"; }
        @Override public String description() {
            return "Triggers NavMesh baking for the active scene geometry.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Navigation", List.of("NavigationRecovery"));
        }
    }

    @Component
    public static class CreateNavMeshAgent implements Tool {
        @Override public String name() { return "create_navmesh_agent"; }
        @Override public String description() {
            return "Adds and configures a NavMeshAgent component on a GameObject (speed, angularSpeed, stoppingDistance, radius, height).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name"));
            props.put("speed", Map.of("type", "number", "description", "Agent movement speed"));
            props.put("angularSpeed", Map.of("type", "number", "description", "Agent turning speed"));
            props.put("acceleration", Map.of("type", "number", "description", "Agent acceleration"));
            props.put("stoppingDistance", Map.of("type", "number", "description", "Agent stopping distance"));
            props.put("radius", Map.of("type", "number", "description", "Agent collision radius"));
            props.put("height", Map.of("type", "number", "description", "Agent height"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Navigation", List.of("NavigationRecovery"));
        }
    }

    @Component
    public static class ConfigureNavMeshAgent implements Tool {
        @Override public String name() { return "configure_navmesh_agent"; }
        @Override public String description() {
            return "Configures NavMeshAgent properties or sets a destination target.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject with NavMeshAgent"));
            props.put("speed", Map.of("type", "number", "description", "Agent speed"));
            props.put("angularSpeed", Map.of("type", "number", "description", "Agent turning speed"));
            props.put("acceleration", Map.of("type", "number", "description", "Agent acceleration"));
            props.put("stoppingDistance", Map.of("type", "number", "description", "Stopping distance"));
            props.put("destination", vector3Schema("Target destination position"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Navigation", List.of("NavigationRecovery"));
        }
    }

    // ==========================================
    // 11. Animation Systems Tools
    // ==========================================

    @Component
    public static class CreateAnimatorController implements Tool {
        @Override public String name() { return "create_animator_controller"; }
        @Override public String description() {
            return "Creates an AnimatorController asset at the specified project path.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("assetPath", Map.of("type", "string", "description", "Destination asset path, e.g. 'Assets/Animations/PlayerController.controller'"));
            return Map.of("type", "object", "properties", props, "required", List.of("assetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("assetPath")) return "Parameter 'assetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Animation", List.of("AnimationRecovery"));
        }
    }

    @Component
    public static class CreateAnimationState implements Tool {
        @Override public String name() { return "create_animation_state"; }
        @Override public String description() {
            return "Adds an animation state (e.g. Idle, Run, Jump, Attack) to an AnimatorController.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("controllerPath", Map.of("type", "string", "description", "Path to AnimatorController asset"));
            props.put("stateName", Map.of("type", "string", "description", "State name, e.g. 'Idle' or 'Walk'"));
            props.put("motionClipPath", Map.of("type", "string", "description", "Optional path to AnimationClip asset"));
            return Map.of("type", "object", "properties", props, "required", List.of("controllerPath", "stateName"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("controllerPath")) return "Parameter 'controllerPath' is required";
            if (!parameters.containsKey("stateName")) return "Parameter 'stateName' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Animation", List.of("AnimationRecovery"));
        }
    }

    @Component
    public static class SetAnimationParameter implements Tool {
        @Override public String name() { return "set_animation_parameter"; }
        @Override public String description() {
            return "Adds or updates an AnimatorController parameter (Float, Int, Bool, Trigger).";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("controllerPath", Map.of("type", "string", "description", "Path to AnimatorController asset"));
            props.put("parameterName", Map.of("type", "string", "description", "Parameter name (e.g. 'Speed', 'IsGrounded', 'Attack')"));
            props.put("parameterType", Map.of("type", "string", "description", "Float, Int, Bool, or Trigger"));
            props.put("defaultValue", Map.of("type", "string", "description", "Optional initial value"));
            return Map.of("type", "object", "properties", props, "required", List.of("controllerPath", "parameterName", "parameterType"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("controllerPath")) return "Parameter 'controllerPath' is required";
            if (!parameters.containsKey("parameterName")) return "Parameter 'parameterName' is required";
            if (!parameters.containsKey("parameterType")) return "Parameter 'parameterType' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Animation", List.of("AnimationRecovery"));
        }
    }

    @Component
    public static class CreateAnimationTransition implements Tool {
        @Override public String name() { return "create_animation_transition"; }
        @Override public String description() {
            return "Creates a transition between animation states with optional exit time and condition parameters.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("controllerPath", Map.of("type", "string", "description", "Path to AnimatorController asset"));
            props.put("fromState", Map.of("type", "string", "description", "Source state name (or 'AnyState')"));
            props.put("toState", Map.of("type", "string", "description", "Destination state name"));
            props.put("hasExitTime", Map.of("type", "boolean", "description", "Whether transition uses exit time"));
            props.put("duration", Map.of("type", "number", "description", "Transition duration in seconds"));
            props.put("conditionParam", Map.of("type", "string", "description", "Parameter name triggering condition"));
            props.put("conditionMode", Map.of("type", "string", "description", "Greater, Less, Equals, NotEqual, If, IfNot"));
            props.put("conditionThreshold", Map.of("type", "number", "description", "Threshold value"));
            return Map.of("type", "object", "properties", props, "required", List.of("controllerPath", "fromState", "toState"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("controllerPath")) return "Parameter 'controllerPath' is required";
            if (!parameters.containsKey("fromState")) return "Parameter 'fromState' is required";
            if (!parameters.containsKey("toState")) return "Parameter 'toState' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Animation", List.of("AnimationRecovery"));
        }
    }

    @Component
    public static class AssignAnimatorController implements Tool {
        @Override public String name() { return "assign_animator_controller"; }
        @Override public String description() {
            return "Assigns an AnimatorController asset to the Animator component on a GameObject.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name"));
            props.put("controllerPath", Map.of("type", "string", "description", "Path to AnimatorController asset"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "controllerPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("controllerPath")) return "Parameter 'controllerPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Animation", List.of("AnimationRecovery"));
        }
    }

    @Component
    public static class GetAnimatorInfo implements Tool {
        @Override public String name() { return "get_animator_info"; }
        @Override public String description() {
            return "Reads the states and parameters configured on a GameObject's Animator.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Animation", List.of());
        }
    }

    // ==========================================
    // 12. Audio Systems Tools
    // ==========================================

    @Component
    public static class CreateAudioSource implements Tool {
        @Override public String name() { return "create_audio_source"; }
        @Override public String description() {
            return "Adds and configures an AudioSource component on a GameObject.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject name"));
            props.put("audioClipPath", Map.of("type", "string", "description", "Optional asset path to AudioClip"));
            props.put("playOnAwake", Map.of("type", "boolean", "description", "Play immediately on scene load"));
            props.put("loop", Map.of("type", "boolean", "description", "Loop playback"));
            props.put("volume", Map.of("type", "number", "description", "Volume (0.0 to 1.0)"));
            props.put("pitch", Map.of("type", "number", "description", "Pitch (default 1.0)"));
            props.put("spatialBlend", Map.of("type", "number", "description", "0.0 for 2D, 1.0 for 3D spatial"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Audio", List.of("AudioRecovery"));
        }
    }

    @Component
    public static class ConfigureAudioSource implements Tool {
        @Override public String name() { return "configure_audio_source"; }
        @Override public String description() {
            return "Configures volume, pitch, loop, spatial blend, and falloff distance on an AudioSource.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject with AudioSource"));
            props.put("volume", Map.of("type", "number", "description", "Volume level"));
            props.put("pitch", Map.of("type", "number", "description", "Pitch level"));
            props.put("loop", Map.of("type", "boolean", "description", "Looping"));
            props.put("spatialBlend", Map.of("type", "number", "description", "Spatial blend (0=2D, 1=3D)"));
            props.put("minDistance", Map.of("type", "number", "description", "3D sound min distance"));
            props.put("maxDistance", Map.of("type", "number", "description", "3D sound max distance"));
            return Map.of("type", "object", "properties", props, "required", List.of("target"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Audio", List.of("AudioRecovery"));
        }
    }

    @Component
    public static class AssignAudioClip implements Tool {
        @Override public String name() { return "assign_audio_clip"; }
        @Override public String description() {
            return "Assigns an AudioClip asset to an AudioSource component.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject"));
            props.put("audioClipPath", Map.of("type", "string", "description", "Asset path to audio file, e.g. 'Assets/Audio/BGM.mp3'"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "audioClipPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("audioClipPath")) return "Parameter 'audioClipPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Audio", List.of("AudioRecovery"));
        }
    }

    @Component
    public static class CreateAudioMixer implements Tool {
        @Override public String name() { return "create_audio_mixer"; }
        @Override public String description() {
            return "Creates an AudioMixer asset at the specified project path.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("assetPath", Map.of("type", "string", "description", "Destination asset path"));
            return Map.of("type", "object", "properties", props, "required", List.of("assetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("assetPath")) return "Parameter 'assetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Audio", List.of());
        }
    }

    @Component
    public static class ConfigureAudioMixer implements Tool {
        @Override public String name() { return "configure_audio_mixer"; }
        @Override public String description() {
            return "Configures volume and properties for an AudioMixer group.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("mixerAssetPath", Map.of("type", "string", "description", "Path to AudioMixer asset"));
            props.put("groupName", Map.of("type", "string", "description", "Mixer group name (e.g. 'Master', 'SFX', 'Music')"));
            props.put("volume", Map.of("type", "number", "description", "Volume in decibels"));
            return Map.of("type", "object", "properties", props, "required", List.of("mixerAssetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("mixerAssetPath")) return "Parameter 'mixerAssetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Audio", List.of());
        }
    }

    // ==========================================
    // 13. Input Handling Tools
    // ==========================================

    @Component
    public static class CreateInputAction implements Tool {
        @Override public String name() { return "create_input_action"; }
        @Override public String description() {
            return "Creates an Input Actions asset or adds an Action map with specified action type.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("assetPath", Map.of("type", "string", "description", "Asset path, e.g. 'Assets/Input/PlayerInput.inputactions'"));
            props.put("actionName", Map.of("type", "string", "description", "Action name (e.g. 'Move', 'Jump', 'Fire')"));
            props.put("actionType", Map.of("type", "string", "description", "Button, Value, or PassThrough"));
            return Map.of("type", "object", "properties", props, "required", List.of("assetPath", "actionName"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("assetPath")) return "Parameter 'assetPath' is required";
            if (!parameters.containsKey("actionName")) return "Parameter 'actionName' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Input", List.of("InputRecovery"));
        }
    }

    @Component
    public static class ConfigureInputAction implements Tool {
        @Override public String name() { return "configure_input_action"; }
        @Override public String description() {
            return "Configures a control binding for an input action (e.g. '<Keyboard>/space', '<Gamepad>/buttonSouth').";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("assetPath", Map.of("type", "string", "description", "Path to InputActions asset"));
            props.put("actionName", Map.of("type", "string", "description", "Action name"));
            props.put("bindingPath", Map.of("type", "string", "description", "Control path (e.g. '<Keyboard>/w', '<Mouse>/leftButton')"));
            return Map.of("type", "object", "properties", props, "required", List.of("assetPath", "actionName", "bindingPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("assetPath")) return "Parameter 'assetPath' is required";
            if (!parameters.containsKey("actionName")) return "Parameter 'actionName' is required";
            if (!parameters.containsKey("bindingPath")) return "Parameter 'bindingPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Input", List.of("InputRecovery"));
        }
    }

    @Component
    public static class GetInputActions implements Tool {
        @Override public String name() { return "get_input_actions"; }
        @Override public String description() {
            return "Lists all configured actions and bindings from an InputActions asset.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("assetPath", Map.of("type", "string", "description", "Path to InputActions asset"));
            return Map.of("type", "object", "properties", props, "required", List.of("assetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("assetPath")) return "Parameter 'assetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Input", List.of());
        }
    }

    @Component
    public static class BindInputAction implements Tool {
        @Override public String name() { return "bind_input_action"; }
        @Override public String description() {
            return "Binds an input action callback to a method on a player controller component.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject"));
            props.put("actionName", Map.of("type", "string", "description", "Action name"));
            props.put("callbackMethod", Map.of("type", "string", "description", "Method name to invoke"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "actionName", "callbackMethod"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("actionName")) return "Parameter 'actionName' is required";
            if (!parameters.containsKey("callbackMethod")) return "Parameter 'callbackMethod' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Input", List.of("InputRecovery"));
        }
    }

    // ==========================================
    // 14. Asset Management Tools
    // ==========================================

    @Component
    public static class ListAssets implements Tool {
        @Override public String name() { return "list_assets"; }
        @Override public String description() {
            return "Lists all asset files in a project directory with optional filter pattern.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("folderPath", Map.of("type", "string", "description", "Project folder (default 'Assets')"));
            props.put("filter", Map.of("type", "string", "description", "Filter string or type, e.g. 't:Prefab', 't:Material'"));
            props.put("recursive", Map.of("type", "boolean", "description", "Search subdirectories recursively"));
            return Map.of("type", "object", "properties", props);
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Asset", List.of());
        }
    }

    @Component
    public static class FindAsset implements Tool {
        @Override public String name() { return "find_asset"; }
        @Override public String description() {
            return "Finds asset paths in the Unity project by name or type search pattern.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("searchPattern", Map.of("type", "string", "description", "Search query (e.g. 'Player t:Prefab' or 'FloorMat')"));
            props.put("assetType", Map.of("type", "string", "description", "Optional type filter: Scene, Prefab, Material, Script, Texture"));
            return Map.of("type", "object", "properties", props, "required", List.of("searchPattern"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || (!parameters.containsKey("searchPattern") && !parameters.containsKey("query") && !parameters.containsKey("name"))) {
                return "Parameter 'searchPattern' or 'query' is required";
            }
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Asset", List.of());
        }
    }

    @Component
    public static class GetAssetInfo implements Tool {
        @Override public String name() { return "get_asset_info"; }
        @Override public String description() {
            return "Inspects an asset on disk: size, GUID, type, and import settings.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("assetPath", Map.of("type", "string", "description", "Asset path to inspect, e.g. 'Assets/Materials/Floor.mat'"));
            return Map.of("type", "object", "properties", props, "required", List.of("assetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || (!parameters.containsKey("assetPath") && !parameters.containsKey("path"))) {
                return "Parameter 'assetPath' or 'path' is required";
            }
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Asset", List.of());
        }
    }

    @Component
    public static class CreateAssetFolder implements Tool {
        @Override public String name() { return "create_asset_folder"; }
        @Override public String description() {
            return "Creates a new folder inside the project Assets directory.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("folderPath", Map.of("type", "string", "description", "Path to create, e.g. 'Assets/Prefabs/Enemies'"));
            return Map.of("type", "object", "properties", props, "required", List.of("folderPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("folderPath")) return "Parameter 'folderPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Asset", List.of());
        }
    }

    @Component
    public static class MoveAsset implements Tool {
        @Override public String name() { return "move_asset"; }
        @Override public String description() {
            return "Moves an asset or folder to a new location inside the project.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("sourcePath", Map.of("type", "string", "description", "Source asset path"));
            props.put("destinationPath", Map.of("type", "string", "description", "Destination asset path"));
            return Map.of("type", "object", "properties", props, "required", List.of("sourcePath", "destinationPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("sourcePath")) return "Parameter 'sourcePath' is required";
            if (!parameters.containsKey("destinationPath")) return "Parameter 'destinationPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Asset", List.of());
        }
    }

    @Component
    public static class RenameAsset implements Tool {
        @Override public String name() { return "rename_asset"; }
        @Override public String description() {
            return "Renames an existing asset file.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("assetPath", Map.of("type", "string", "description", "Asset path to rename"));
            props.put("newName", Map.of("type", "string", "description", "New name (without extension or with same extension)"));
            return Map.of("type", "object", "properties", props, "required", List.of("assetPath", "newName"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("assetPath")) return "Parameter 'assetPath' is required";
            if (!parameters.containsKey("newName")) return "Parameter 'newName' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Asset", List.of());
        }
    }

    @Component
    public static class DeleteAsset implements Tool {
        @Override public String name() { return "delete_asset"; }
        @Override public String description() {
            return "Deletes an asset from the project (Protected: only assets inside Assets/ can be deleted; Packages and internal dirs are blocked).";
        }
        @Override public ToolPermission permission() { return ToolPermission.DESTRUCTIVE; }
        @Override public boolean isDestructive() { return true; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("assetPath", Map.of("type", "string", "description", "Asset path to delete, e.g. 'Assets/Temp/OldModel.fbx'"));
            return Map.of("type", "object", "properties", props, "required", List.of("assetPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("assetPath")) return "Parameter 'assetPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), true, timeoutSeconds(), "Asset", List.of("AssetRecovery"));
        }
    }

    // ==========================================
    // 15. Project Configuration Tools
    // ==========================================

    @Component
    public static class GetProjectSettings implements Tool {
        @Override public String name() { return "get_project_settings"; }
        @Override public String description() {
            return "Reads project settings (product name, company name, bundle version, target graphics API, script backend).";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "ProjectConfig", List.of());
        }
    }

    @Component
    public static class GetBuildSettings implements Tool {
        @Override public String name() { return "get_build_settings"; }
        @Override public String description() {
            return "Reads scenes in build, active build target, and player settings.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "ProjectConfig", List.of());
        }
    }

    @Component
    public static class SetBuildSetting implements Tool {
        @Override public String name() { return "set_build_setting"; }
        @Override public String description() {
            return "Modifies a build or player setting (e.g. productName, companyName, scenesInBuild).";
        }
        @Override public ToolPermission permission() { return ToolPermission.PROJECT_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("settingName", Map.of("type", "string", "description", "Setting name (productName, companyName, bundleVersion, addScene)"));
            props.put("value", Map.of("type", "string", "description", "Setting value"));
            return Map.of("type", "object", "properties", props, "required", List.of("settingName", "value"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("settingName")) return "Parameter 'settingName' is required";
            if (!parameters.containsKey("value")) return "Parameter 'value' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "ProjectConfig", List.of());
        }
    }

    @Component
    public static class GetLayers implements Tool {
        @Override public String name() { return "get_layers"; }
        @Override public String description() {
            return "Lists all configured Unity layers (names and indexes).";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "ProjectConfig", List.of());
        }
    }

    @Component
    public static class SetLayer implements Tool {
        @Override public String name() { return "set_layer"; }
        @Override public String description() {
            return "Assigns a layer to a GameObject and optionally its children.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", Map.of("type", "string", "description", "Target GameObject"));
            props.put("layerName", Map.of("type", "string", "description", "Layer name or index"));
            props.put("recursive", Map.of("type", "boolean", "description", "Apply to all child transforms"));
            return Map.of("type", "object", "properties", props, "required", List.of("target", "layerName"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("target")) return "Parameter 'target' is required";
            if (!parameters.containsKey("layerName")) return "Parameter 'layerName' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "ProjectConfig", List.of());
        }
    }

    @Component
    public static class GetTags implements Tool {
        @Override public String name() { return "get_tags"; }
        @Override public String description() {
            return "Lists all defined GameObject tags in the project.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "ProjectConfig", List.of());
        }
    }

    @Component
    public static class CreateTag implements Tool {
        @Override public String name() { return "create_tag"; }
        @Override public String description() {
            return "Creates a new custom tag in TagManager.";
        }
        @Override public ToolPermission permission() { return ToolPermission.SAFE_WRITE; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("tagName", Map.of("type", "string", "description", "Tag name to register (e.g. 'Enemy', 'Hazard', 'Goal')"));
            return Map.of("type", "object", "properties", props, "required", List.of("tagName"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || (!parameters.containsKey("tagName") && !parameters.containsKey("tag"))) return "Parameter 'tagName' is required";
            if (parameters.containsKey("tag") && !parameters.containsKey("tagName")) {
                parameters.put("tagName", parameters.get("tag"));
            }
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "ProjectConfig", List.of());
        }
    }

    // ==========================================
    // 16. Build Pipeline Tools
    // ==========================================

    @Component
    public static class BuildProject implements Tool {
        @Override public String name() { return "build_project"; }
        @Override public String description() {
            return "Builds a standalone executable player from the current project scenes.";
        }
        @Override public ToolPermission permission() { return ToolPermission.BUILD; }
        @Override public int timeoutSeconds() { return 300; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.EDITOR, ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("buildTarget", Map.of("type", "string", "description", "StandaloneWindows64, StandaloneOSX, StandaloneLinux64, etc."));
            props.put("outputPath", Map.of("type", "string", "description", "Destination file path, e.g. 'Builds/Game.exe'"));
            props.put("scenes", Map.of("type", "array", "description", "Optional list of scene asset paths to include"));
            props.put("options", Map.of("type", "array", "description", "Optional build options, e.g. Development, AutoRunPlayer"));
            return Map.of("type", "object", "properties", props, "required", List.of("outputPath"));
        }
        @Override public String validate(Map<String, Object> parameters) {
            if (parameters == null || !parameters.containsKey("outputPath")) return "Parameter 'outputPath' is required";
            return null;
        }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Build", List.of("BuildRecovery"));
        }
    }

    @Component
    public static class GetBuildResult implements Tool {
        @Override public String name() { return "get_build_result"; }
        @Override public String description() {
            return "Reads the result and error log from the last standalone build attempt.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Build", List.of());
        }
    }

    @Component
    public static class ValidateBuildSettings implements Tool {
        @Override public String name() { return "validate_build_settings"; }
        @Override public String description() {
            return "Validates that scenes are configured in Build Settings and build target SDK is available.";
        }
        @Override public ToolPermission permission() { return ToolPermission.READ_ONLY; }
        @Override public Set<ToolMode> allowedModes() { return Set.of(ToolMode.BOTH); }
        @Override public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }
        @Override public String validate(Map<String, Object> parameters) { return null; }
        @Override public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), inputSchema(), null, permission(), allowedModes(), false, timeoutSeconds(), "Build", List.of());
        }
    }
}
