package com.unityagent.tools;

import java.util.Map;

/**
 * Interface for a Unity tool that can be executed via the WebSocket bridge.
 * Each tool has a unique name, description, parameter validation, and is
 * dispatched to the Unity C# side for execution.
 */
public interface Tool {

    /**
     * @return unique tool identifier (e.g., "create_test_cube")
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
}
