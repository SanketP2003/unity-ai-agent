package com.unityagent.api;

import com.unityagent.tools.Tool;
import com.unityagent.tools.ToolRegistry;
import com.unityagent.unity.UnityCommandExecutor;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * REST API for the Autonomous Unity Agent.
 * Provides health, status, and tool execution endpoints.
 */
@RestController
@RequestMapping("/api")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final UnityConnection unityConnection;
    private final UnityCommandExecutor commandExecutor;
    private final ToolRegistry toolRegistry;

    public AgentController(UnityConnection unityConnection,
                           UnityCommandExecutor commandExecutor,
                           ToolRegistry toolRegistry) {
        this.unityConnection = unityConnection;
        this.commandExecutor = commandExecutor;
        this.toolRegistry = toolRegistry;
    }

    /**
     * GET /api/health — basic health check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("service", "autonomous-unity-agent");
        result.put("version", "0.1.0");
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/status — detailed agent and connection status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "autonomous-unity-agent");
        result.put("version", "0.1.0");

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("state", unityConnection.getState().name());
        connection.put("ready", unityConnection.isReady());
        connection.put("pendingRequests", unityConnection.getPendingRequestCount());
        result.put("unityConnection", connection);

        result.put("registeredTools", toolRegistry.getAllTools().stream()
                .map(Tool::name)
                .toList());

        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/tools/execute — execute a Unity tool.
     *
     * Request body:
     * {
     *   "tool": "create_test_cube",
     *   "parameters": {}
     * }
     */
    @PostMapping("/tools/execute")
    public ResponseEntity<Object> executeTool(@RequestBody Map<String, Object> body) {
        String toolName = (String) body.get("tool");
        if (toolName == null || toolName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MISSING_TOOL",
                    "message", "The 'tool' field is required"
            ));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) body.getOrDefault("parameters", Map.of());

        // Check Unity readiness before attempting execution
        if (!unityConnection.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "UNITY_NOT_READY",
                    "message", "Unity bridge is not connected or handshake incomplete (state: " +
                               unityConnection.getState() + ")"
            ));
        }

        try {
            UnityMessage response = commandExecutor.execute(toolName, parameters);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", e.getMessage()
            ));
        } catch (TimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                    "error", "TIMEOUT",
                    "message", "Unity did not respond within the configured timeout"
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "UNITY_NOT_READY",
                    "message", e.getMessage()
            ));
        } catch (ExecutionException | InterruptedException e) {
            log.error("Tool execution failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "EXECUTION_FAILED",
                    "message", e.getMessage()
            ));
        }
    }
}
