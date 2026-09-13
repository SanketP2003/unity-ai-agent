package com.unityagent.tools;

import java.util.Map;
import java.util.Set;

/**
 * Interface for a Unity tool that can be executed via the WebSocket bridge.
 * Each tool has a unique name, description, input schema, parameter validation,
 * execution permissions, and mode constraints.
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
                timeoutSeconds()
        );
    }
}
