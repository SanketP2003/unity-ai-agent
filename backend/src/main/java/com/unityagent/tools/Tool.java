package com.unityagent.tools;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for a Unity tool that can be executed via the WebSocket bridge.
 * Each tool has a unique name, description, input schema, parameter validation,
 * execution permissions, mode constraints, domain classification, prerequisites, and effects.
 */
public interface Tool {

    /**
     * @return unique tool identifier (e.g., "create_primitive")
     */
    String name();

    /**
     * @return human-readable description of what this tool does
     */
    String description();

    /**
     * Validate the given parameters for this tool.
     *
     * @param parameters the parameters to validate (may be null)
     * @return null if valid, or an error message describing the validation failure
     */
    String validate(Map<String, Object> parameters);

    /**
     * @return the safety permission level for this tool
     */
    default ToolPermission permission() {
        return ToolPermission.SAFE;
    }

    /**
     * @return the Unity execution modes in which this tool is allowed
     */
    default Set<ToolMode> allowedModes() {
        return Set.of(ToolMode.BOTH);
    }

    /**
     * @return true if this tool permanently alters or deletes project/scene assets
     */
    default boolean isDestructive() {
        return false;
    }

    /**
     * @return execution timeout in seconds
     */
    default int timeoutSeconds() {
        return 30;
    }

    /**
     * @return functional domain category (e.g., "SCRIPT", "GAMEOBJECT", "SCENE", "PLAY_MODE", "UI")
     */
    default String domain() {
        return "General";
    }

    /**
     * @return operational risk level
     */
    default ToolRiskLevel riskLevel() {
        return isDestructive() ? ToolRiskLevel.HIGH : ToolRiskLevel.LOW;
    }

    /**
     * @return true if changes performed by this tool can be easily reverted
     */
    default boolean isReversible() {
        return !isDestructive();
    }

    /**
     * @return list of prerequisites required before executing this tool (e.g. "COMPILE_SUCCESS", "PLAY_MODE")
     */
    default List<String> prerequisites() {
        return List.of();
    }

    /**
     * @return artifacts, systems, or states produced by this tool (e.g. "SCRIPT", "GAMEOBJECT", "PLAY_MODE")
     */
    default List<String> produces() {
        return List.of();
    }

    /**
     * @return entities or components modified by this tool (e.g. "COMPONENT", "TRANSFORM", "SCENE")
     */
    default List<String> modifies() {
        return List.of();
    }

    /**
     * @return validation type or condition required after executing this tool (e.g. "COMPILE_SUCCESS")
     */
    default String validationRequired() {
        return null;
    }

    /**
     * @return JSON Schema describing the input parameters for LLM tool calling
     */
    default Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of()
        );
    }

    /**
     * @return complete tool definition object
     */
    default ToolDefinition definition() {
        return new ToolDefinition(
                name(),
                description(),
                inputSchema(),
                permission(),
                allowedModes(),
                isDestructive(),
                timeoutSeconds(),
                domain(),
                riskLevel(),
                isReversible(),
                prerequisites(),
                produces(),
                modifies(),
                validationRequired()
        );
    }
}
