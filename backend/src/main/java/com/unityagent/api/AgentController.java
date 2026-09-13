package com.unityagent.api;

import com.unityagent.agent.AgentLoop;
import com.unityagent.agent.CancellationToken;
import com.unityagent.agent.model.AgentRunResult;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * REST API for the Autonomous Unity Agent.
 * Provides health, status, tool execution, and autonomous agent run endpoints.
 */
@RestController
@RequestMapping("/api")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final UnityConnection unityConnection;
    private final UnityCommandExecutor commandExecutor;
    private final ToolRegistry toolRegistry;
    private final com.unityagent.agent.service.AgentService agentService;
    private final com.unityagent.agent.provider.AIProvider aiProvider;
    private final com.unityagent.agent.provider.AIProviderFactory aiProviderFactory;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentController(UnityConnection unityConnection,
                           UnityCommandExecutor commandExecutor,
                           ToolRegistry toolRegistry,
                           com.unityagent.agent.service.AgentService agentService,
                           com.unityagent.agent.provider.AIProvider aiProvider,
                           com.unityagent.agent.provider.AIProviderFactory aiProviderFactory) {
        this.unityConnection = unityConnection;
        this.commandExecutor = commandExecutor;
        this.toolRegistry = toolRegistry;
        this.agentService = agentService;
        this.aiProvider = aiProvider;
        this.aiProviderFactory = aiProviderFactory;
    }

    public AgentController(UnityConnection unityConnection,
                           UnityCommandExecutor commandExecutor,
                           ToolRegistry toolRegistry,
                           com.unityagent.agent.service.AgentService agentService,
                           com.unityagent.agent.provider.AIProvider aiProvider) {
        this(unityConnection, commandExecutor, toolRegistry, agentService, aiProvider, null);
    }

    /**
     * POST /api/agent/run — start an autonomous agent run.
     * Defaults to asynchronous non-blocking execution (returns immediately with status=RUNNING).
     * Set ?async=false or "async": false in body for synchronous execution.
     */
    @PostMapping("/agent/run")
    public ResponseEntity<?> runAgent(
            @RequestBody Map<String, Object> body,
            @RequestParam(name = "async", required = false) Boolean asyncParam) {

        String message = (String) body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MISSING_MESSAGE",
                    "message", "The 'message' field is required and cannot be blank"
            ));
        }

        String sessionId = (String) body.getOrDefault("sessionId", "session_" + UUID.randomUUID().toString().substring(0, 8));
        String agentRunId = (String) body.getOrDefault("agentRunId", "run_" + UUID.randomUUID().toString().substring(0, 8));

        com.unityagent.tools.ToolMode mode = com.unityagent.tools.ToolMode.BOTH;
        if (body.containsKey("mode")) {
            try {
                mode = com.unityagent.tools.ToolMode.valueOf(((String) body.get("mode")).toUpperCase());
            } catch (Exception ignored) {}
        }

        boolean isAsync = true;
        if (asyncParam != null) {
            isAsync = asyncParam;
        } else if (body.containsKey("async")) {
            Object a = body.get("async");
            if (a instanceof Boolean b) isAsync = b;
            else if (a instanceof String s) isAsync = Boolean.parseBoolean(s);
        }

        String projectId = (String) body.get("projectId");

        try {
            if (isAsync) {
                log.info("REST request to start async run: sessionId={}, agentRunId={}, projectId={}, message='{}'",
                        sessionId, agentRunId, projectId, message);
                com.unityagent.agent.service.AgentRunState state = agentService.startRunAsync(sessionId, agentRunId, message, mode, projectId);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("sessionId", sessionId);
                resp.put("agentRunId", agentRunId);
                if (projectId != null) {
                    resp.put("projectId", projectId);
                }
                resp.put("status", state.getStatus().name());
                return ResponseEntity.ok(resp);
            } else {
                log.info("REST request to execute synchronous run: sessionId={}, agentRunId={}, projectId={}", sessionId, agentRunId, projectId);
                AgentRunResult result = agentService.runSync(sessionId, agentRunId, message, mode, projectId);
                return ResponseEntity.ok(result);
            }
        } catch (com.unityagent.agent.service.SessionConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "CONFLICT",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/agent/run/{agentRunId} — get status and events for an agent run.
     */
    @GetMapping("/agent/run/{agentRunId}")
    public ResponseEntity<?> getRunStatus(@PathVariable String agentRunId) {
        return agentService.getRunState(agentRunId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "NOT_FOUND",
                        "message", "Agent run '" + agentRunId + "' not found"
                )));
    }

    /**
     * POST /api/agent/run/{agentRunId}/cancel — cancel an active agent run.
     */
    @PostMapping("/agent/run/{agentRunId}/cancel")
    public ResponseEntity<?> cancelRun(@PathVariable String agentRunId) {
        boolean cancelled = agentService.cancelRun(agentRunId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("agentRunId", agentRunId);
        resp.put("status", "CANCELLED");
        resp.put("cancelled", cancelled);
        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/agent/session/{sessionId} — get conversation history and session status.
     */
    @GetMapping("/agent/session/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(agentService.getSessionData(sessionId));
    }

    /**
     * GET /api/agent/events/{sessionId} — subscribe to real-time Server-Sent Events (SSE).
     */
    @GetMapping(value = "/agent/events/{sessionId}", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribeEvents(@PathVariable String sessionId) {
        return agentService.subscribeToEvents(sessionId);
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
     * GET /api/status — detailed agent, bridge, and LLM connection status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "autonomous-unity-agent");
        result.put("version", "0.1.0");

        // Backend status
        result.put("backend", Map.of("available", true));

        // Unity connection status
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("state", unityConnection.getState().name());
        connection.put("ready", unityConnection.isReady());
        connection.put("pendingRequests", unityConnection.getPendingRequestCount());
        result.put("unityConnection", connection);
        result.put("unity", Map.of("connected", unityConnection.isReady()));

        // LLM provider status
        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("provider", aiProvider.getProviderName());
        if (aiProvider instanceof com.unityagent.agent.provider.openai.OpenAICompatibleProvider oacp) {
            llm.put("model", oacp.getModel());
            llm.put("baseUrl", oacp.getBaseUrl());
        } else if (aiProvider instanceof com.unityagent.agent.provider.openai.OpenAIProvider op) {
            llm.put("model", op.getModel());
            llm.put("baseUrl", op.getBaseUrl());
        }
        llm.put("configured", aiProvider.isConfigured());
        llm.put("available", aiProvider.isConfigured());
        llm.put("capabilities", aiProvider.getCapabilities());
        result.put("llm", llm);

        result.put("projects", unityConnection.getConnectedProjects());

        result.put("registeredTools", toolRegistry.getAllTools().stream()
                .map(Tool::name)
                .toList());

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/config/provider — get currently active provider configuration and capabilities.
     * Never echoes the raw secret API key.
     */
    @GetMapping("/config/provider")
    public ResponseEntity<Map<String, Object>> getProviderConfig() {
        if (aiProviderFactory != null) {
            return ResponseEntity.ok(aiProviderFactory.getCurrentProviderInfo());
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("provider", aiProvider.getProviderName());
        resp.put("configured", aiProvider.isConfigured());
        resp.put("capabilities", aiProvider.getCapabilities());
        if (aiProvider instanceof com.unityagent.agent.provider.openai.OpenAICompatibleProvider oacp) {
            resp.put("model", oacp.getModel());
            resp.put("baseUrl", oacp.getBaseUrl());
        } else if (aiProvider instanceof com.unityagent.agent.provider.openai.OpenAIProvider op) {
            resp.put("model", op.getModel());
            resp.put("baseUrl", op.getBaseUrl());
        }
        resp.put("hasApiKey", aiProvider.isConfigured());
        resp.put("apiKeyConfigured", aiProvider.isConfigured());
        resp.put("maskedApiKey", aiProvider.isConfigured() ? "••••••••" : "");
        return ResponseEntity.ok(resp);
    }

    /**
     * POST /api/config/provider — dynamically configure provider credentials and endpoint.
     * Does not write secrets to Git or Unity assets.
     */
    @PostMapping("/config/provider")
    public ResponseEntity<Map<String, Object>> updateProviderConfig(@RequestBody Map<String, Object> body) {
        String provider = (String) body.getOrDefault("provider", aiProvider.getProviderName());
        String baseUrl = (String) body.get("baseUrl");
        String apiKey = (String) body.get("apiKey");
        String model = (String) body.get("model");

        com.unityagent.agent.provider.ProviderCapabilities capabilities = null;
        if (body.containsKey("capabilities") && body.get("capabilities") instanceof Map<?, ?> capMap) {
            boolean toolCalling = capMap.containsKey("toolCalling") ? Boolean.TRUE.equals(capMap.get("toolCalling")) : true;
            boolean structured = capMap.containsKey("structuredOutput") ? Boolean.TRUE.equals(capMap.get("structuredOutput")) : false;
            boolean streaming = capMap.containsKey("streaming") ? Boolean.TRUE.equals(capMap.get("streaming")) : true;
            boolean vision = capMap.containsKey("vision") ? Boolean.TRUE.equals(capMap.get("vision")) : false;
            capabilities = new com.unityagent.agent.provider.ProviderCapabilities(toolCalling, structured, streaming, vision);
        }

        if (aiProviderFactory != null) {
            aiProviderFactory.configureProvider(provider, baseUrl, apiKey, model, capabilities);
            return ResponseEntity.ok(aiProviderFactory.getCurrentProviderInfo());
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "SUCCESS");
        resp.put("provider", aiProvider.getProviderName());
        resp.put("configured", aiProvider.isConfigured());
        resp.put("capabilities", aiProvider.getCapabilities());
        if (aiProvider instanceof com.unityagent.agent.provider.openai.OpenAICompatibleProvider oacp) {
            resp.put("model", oacp.getModel());
            resp.put("baseUrl", oacp.getBaseUrl());
        } else if (aiProvider instanceof com.unityagent.agent.provider.openai.OpenAIProvider op) {
            resp.put("model", op.getModel());
            resp.put("baseUrl", op.getBaseUrl());
        }
        resp.put("hasApiKey", aiProvider.isConfigured());
        resp.put("apiKeyConfigured", aiProvider.isConfigured());
        resp.put("maskedApiKey", aiProvider.isConfigured() ? "••••••••" : "");
        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/projects — list all connected Unity projects.
     */
    @GetMapping("/projects")
    public ResponseEntity<List<UnityConnection.ProjectConnectionInfo>> getConnectedProjects() {
        return ResponseEntity.ok(unityConnection.getConnectedProjects());
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

        String projectId = (String) body.get("projectId");

        try {
            UnityMessage response = commandExecutor.execute(projectId, null, toolName, parameters);
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
