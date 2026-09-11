package com.unityagent.unity;

import com.unityagent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Executes Unity tool commands through the WebSocket connection.
 * Validates tools against the registry before sending to Unity.
 */
@Service
public class UnityCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(UnityCommandExecutor.class);

    private final UnityConnection connection;
    private final ToolRegistry toolRegistry;

    public UnityCommandExecutor(UnityConnection connection, ToolRegistry toolRegistry) {
        this.connection = connection;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Execute a tool command on Unity.
     *
     * @param toolName   the registered tool name (e.g., "create_test_cube")
     * @param parameters the tool parameters (may be null or empty)
     * @return the structured response from Unity
     * @throws IllegalArgumentException if the tool is not registered
     * @throws IllegalStateException    if Unity is not READY
     * @throws TimeoutException         if Unity does not respond in time
     */
    public UnityMessage execute(String toolName, Map<String, Object> parameters)
            throws TimeoutException, ExecutionException, InterruptedException {

        // Validate tool exists
        if (!toolRegistry.hasTool(toolName)) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        // Validate parameters
        var tool = toolRegistry.getTool(toolName);
        String validationError = tool.validate(parameters);
        if (validationError != null) {
            throw new IllegalArgumentException("Invalid parameters for tool '" + toolName + "': " + validationError);
        }

        // Build and send request
        UnityMessage request = UnityMessage.toolRequest(toolName, parameters);
        log.info("Executing tool: {} (operationId={})", toolName, request.getOperationId());

        UnityMessage response = connection.sendToolRequest(request);

        if (Boolean.TRUE.equals(response.getSuccess())) {
            log.info("Tool '{}' completed successfully (operationId={})", toolName, response.getOperationId());
        } else {
            log.warn("Tool '{}' failed (operationId={}): {}", toolName, response.getOperationId(), response.getErrors());
        }

        return response;
    }
}
