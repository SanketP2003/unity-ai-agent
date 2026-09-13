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
    private final com.unityagent.agent.reliability.ToolExecutionTracker tracker;
    private final com.unityagent.agent.security.ToolArgumentValidator argumentValidator;

    @org.springframework.beans.factory.annotation.Autowired
    public UnityCommandExecutor(UnityConnection connection, ToolRegistry toolRegistry,
                                @org.springframework.beans.factory.annotation.Autowired(required = false)
                                com.unityagent.agent.reliability.ToolExecutionTracker tracker,
                                @org.springframework.beans.factory.annotation.Autowired(required = false)
                                com.unityagent.agent.security.ToolArgumentValidator argumentValidator) {
        this.connection = connection;
        this.toolRegistry = toolRegistry;
        this.tracker = tracker;
        this.argumentValidator = argumentValidator != null ? argumentValidator : new com.unityagent.agent.security.ToolArgumentValidator();
    }

    public UnityCommandExecutor(UnityConnection connection, ToolRegistry toolRegistry) {
        this(connection, toolRegistry, null, null);
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
        return execute(null, toolName, parameters);
    }

    /**
     * Execute a tool command on Unity with an explicit correlated operationId.
     *
     * @param operationId explicit operation ID (or null to generate a new one)
     * @param toolName    the registered tool name
     * @param parameters  the tool parameters
     * @return the structured response from Unity
     */
    public UnityMessage execute(String operationId, String toolName, Map<String, Object> parameters)
            throws TimeoutException, ExecutionException, InterruptedException {
        return execute(null, operationId, toolName, parameters);
    }

    /**
     * Execute a tool command on a specific target Unity project with correlated operationId.
     *
     * @param projectId   the target project ID (or null for active project)
     * @param operationId explicit operation ID (or null to generate a new one)
     * @param toolName    the registered tool name
     * @param parameters  the tool parameters
     * @return the structured response from Unity
     */
    public UnityMessage execute(String projectId, String operationId, String toolName, Map<String, Object> parameters)
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

        // Security validation against path traversal and malicious arguments
        if (argumentValidator != null) {
            var secResult = argumentValidator.validate(toolName, parameters);
            if (!secResult.isValid()) {
                throw new SecurityException("Security validation failed for tool '" + toolName + "': " + secResult.getErrorMessage());
            }
        }

        // Build and send request with correlated operationId and optional projectId
        UnityMessage request = UnityMessage.toolRequest(operationId, toolName, parameters);
        if (projectId != null) {
            request.setProjectId(projectId);
        }
        log.info("Executing tool: {} (operationId={}, projectId={})", toolName, request.getOperationId(), projectId);

        com.unityagent.agent.reliability.ToolExecutionRecord record = null;
        if (tracker != null) {
            record = tracker.registerExecution(null, request.getOperationId(), projectId, null, toolName, parameters, 30);
            record.markRunning();
        }

        try {
            UnityMessage response = connection.sendToolRequest(projectId, request);

            if (record != null) {
                if (Boolean.TRUE.equals(response.getSuccess())) {
                    record.markSucceeded(response.getData());
                } else {
                    record.markFailed(response.getErrors() != null ? response.getErrors().toString() : "Failed");
                }
            }

            if (Boolean.TRUE.equals(response.getSuccess())) {
                log.info("Tool '{}' completed successfully (operationId={})", toolName, response.getOperationId());
            } else {
                log.warn("Tool '{}' failed (operationId={}): {}", toolName, response.getOperationId(), response.getErrors());
            }

            return response;
        } catch (TimeoutException te) {
            if (record != null) record.markTimedOut();
            throw te;
        } catch (Exception e) {
            if (record != null) record.markUnknown("Exception during tool dispatch: " + e.getMessage());
            throw e;
        }
    }
}
