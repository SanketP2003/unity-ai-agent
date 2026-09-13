package com.unityagent.unity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * WebSocket handler managing the Unity bridge connection.
 * Implements Protocol v1.0 with handshake, tool request/response correlation,
 * and ping/pong heartbeat.
 *
 * Connection state machine:
 * DISCONNECTED → CONNECTING → CONNECTED → HANDSHAKING → READY
 *                                                         ↕
 *                                                       ERROR
 */
@Component
public class UnityConnection extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(UnityConnection.class);

    private final ObjectMapper objectMapper;

    @Value("${unity.websocket.command-timeout-seconds:30}")
    private int commandTimeoutSeconds = 30;

    @Value("${unity.websocket.ping-interval-seconds:15}")
    private int pingIntervalSeconds = 15;

    /** Current WebSocket session with Unity (null if disconnected). */
    private volatile WebSocketSession unitySession;

    /** Current connection state. */
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    /** Information about connected projects. */
    public static class ProjectConnectionInfo {
        private final String projectId;
        private final String connectionId;
        private final String protocolVersion;
        private final String extensionVersion;
        private final String unityVersion;
        private final java.util.Map<String, Object> capabilities;
        private final long connectedAt;
        private final WebSocketSession session;

        public ProjectConnectionInfo(String projectId, String connectionId, String protocolVersion,
                                     String extensionVersion, String unityVersion,
                                     java.util.Map<String, Object> capabilities, WebSocketSession session) {
            this.projectId = projectId;
            this.connectionId = connectionId;
            this.protocolVersion = protocolVersion;
            this.extensionVersion = extensionVersion;
            this.unityVersion = unityVersion;
            this.capabilities = capabilities != null ? capabilities : java.util.Map.of();
            this.connectedAt = System.currentTimeMillis();
            this.session = session;
        }

        public String getProjectId() { return projectId; }
        public String getConnectionId() { return connectionId; }
        public String getProtocolVersion() { return protocolVersion; }
        public String getExtensionVersion() { return extensionVersion; }
        public String getUnityVersion() { return unityVersion; }
        public java.util.Map<String, Object> getCapabilities() { return capabilities; }
        public long getConnectedAt() { return connectedAt; }
        @com.fasterxml.jackson.annotation.JsonIgnore
        public WebSocketSession getSession() { return session; }
    }

    private final ConcurrentHashMap<String, ProjectConnectionInfo> projectConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToProject = new ConcurrentHashMap<>();

    /** Pending tool requests awaiting Unity responses, keyed by operationId. */
    private final ConcurrentHashMap<String, CompletableFuture<UnityMessage>> pendingRequests =
            new ConcurrentHashMap<>();

    /** Scheduled executor for ping heartbeats and timeouts. */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "unity-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /** Handle to the current ping task (cancelled on disconnect). */
    private volatile ScheduledFuture<?> pingTask;

    private final com.unityagent.memory.service.ProjectMemoryService projectMemoryService;
    private final com.unityagent.agent.reliability.ToolExecutionTracker toolExecutionTracker;

    @org.springframework.beans.factory.annotation.Autowired
    public UnityConnection(ObjectMapper objectMapper,
                           @org.springframework.beans.factory.annotation.Autowired(required = false)
                           com.unityagent.memory.service.ProjectMemoryService projectMemoryService,
                           @org.springframework.beans.factory.annotation.Autowired(required = false)
                           com.unityagent.agent.reliability.ToolExecutionTracker toolExecutionTracker) {
        this.objectMapper = objectMapper;
        this.projectMemoryService = projectMemoryService;
        this.toolExecutionTracker = toolExecutionTracker;
    }

    public UnityConnection(ObjectMapper objectMapper,
                           com.unityagent.memory.service.ProjectMemoryService projectMemoryService) {
        this(objectMapper, projectMemoryService, null);
    }

    public UnityConnection(ObjectMapper objectMapper) {
        this(objectMapper, null, null);
    }

    // --- Connection state ---

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        HANDSHAKING,
        READY,
        DEGRADED,
        RECONNECTING,
        FAILED,
        ERROR
    }

    public ConnectionState getState() {
        if (!projectConnections.isEmpty() && unitySession != null && unitySession.isOpen()) {
            return ConnectionState.READY;
        }
        return state;
    }

    public boolean isReady() {
        return (state == ConnectionState.READY || !projectConnections.isEmpty())
                && unitySession != null && unitySession.isOpen();
    }

    // --- WebSocket lifecycle ---

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Unity WebSocket connected: sessionId={}", session.getId());
        this.unitySession = session;
        this.state = ConnectionState.CONNECTED;
        // Wait for HANDSHAKE from Unity before transitioning to READY
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("Received from Unity: {}", payload);

        UnityMessage msg;
        try {
            msg = objectMapper.readValue(payload, UnityMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Unity message: {}", e.getMessage());
            sendError(session, null, "INVALID_JSON", "Could not parse message: " + e.getMessage());
            return;
        }

        if (msg.getType() == null) {
            log.error("Received message with no type");
            sendError(session, msg.getOperationId(), "MISSING_TYPE", "Message type is required");
            return;
        }

        switch (msg.getType()) {
            case HANDSHAKE -> handleHandshake(session, msg);
            case TOOL_RESPONSE -> handleToolResponse(msg);
            case PONG -> handlePong(msg);
            case EVENT -> handleEvent(msg);
            case ERROR -> handleError(msg);
            default -> log.warn("Unexpected message type from Unity: {}", msg.getType());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Unity WebSocket disconnected: status={}", status);
        String projectId = sessionToProject.remove(session.getId());
        if (projectId != null) {
            projectConnections.remove(projectId);
            log.info("Unity project connection closed: projectId={}", projectId);
        }
        if (this.unitySession == session) {
            if (projectConnections.isEmpty()) {
                this.unitySession = null;
                this.state = ConnectionState.DISCONNECTED;
                stopPingTask();
            } else {
                this.unitySession = projectConnections.values().iterator().next().getSession();
                this.state = ConnectionState.READY;
            }
        } else if (!projectConnections.isEmpty()) {
            this.state = ConnectionState.READY;
        }
        if (toolExecutionTracker != null) {
            toolExecutionTracker.markInFlightAsUnknown(projectId, "Unity WebSocket disconnected: " + status);
        }
        failAllPending("Unity disconnected");
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Unity WebSocket transport error: {}", exception.getMessage());
        String projId = sessionToProject.remove(session.getId());
        if (projId != null) {
            projectConnections.remove(projId);
        }
        if (this.unitySession == session) {
            if (projectConnections.isEmpty()) {
                this.unitySession = null;
                this.state = ConnectionState.ERROR;
                stopPingTask();
            } else {
                this.unitySession = projectConnections.values().iterator().next().getSession();
                this.state = ConnectionState.READY;
            }
        } else if (!projectConnections.isEmpty()) {
            this.state = ConnectionState.READY;
        }
        failAllPending("Transport error: " + exception.getMessage());
    }

    // --- Protocol handlers ---

    private void handleHandshake(WebSocketSession session, UnityMessage msg) {
        log.info("Received HANDSHAKE from Unity: operationId={}, data={}", msg.getOperationId(), msg.getData());
        this.state = ConnectionState.HANDSHAKING;

        // Protocol negotiation check
        String clientProtocol = msg.getProtocolVersion();
        if (msg.getData() != null && msg.getData().containsKey("protocolVersion")) {
            clientProtocol = String.valueOf(msg.getData().get("protocolVersion"));
        }
        if (clientProtocol == null || !clientProtocol.startsWith("1.")) {
            String mismatchMsg = "Protocol version mismatch: expected 1.x, received: " + clientProtocol;
            log.error(mismatchMsg);
            sendError(session, msg.getOperationId(), "EXTENSION_PROTOCOL_MISMATCH", mismatchMsg);
            this.state = ConnectionState.ERROR;
            return;
        }

        // Project identification
        String projectId = msg.getProjectId();
        if (projectId == null && msg.getData() != null && msg.getData().containsKey("projectId")) {
            projectId = String.valueOf(msg.getData().get("projectId"));
        }
        if (projectId == null || projectId.isBlank()) {
            projectId = "project_" + session.getId().substring(0, Math.min(8, session.getId().length()));
        }

        String unityVersion = "unknown";
        String extVersion = "1.0.0";
        java.util.Map<String, Object> capabilities = java.util.Map.of();
        if (msg.getData() != null) {
            if (msg.getData().containsKey("unityVersion")) {
                unityVersion = String.valueOf(msg.getData().get("unityVersion"));
            }
            if (msg.getData().containsKey("extensionVersion")) {
                extVersion = String.valueOf(msg.getData().get("extensionVersion"));
            } else if (msg.getData().containsKey("bridgeVersion")) {
                extVersion = String.valueOf(msg.getData().get("bridgeVersion"));
            }
            if (msg.getData().get("capabilities") instanceof java.util.Map<?, ?> capMap) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> typedCapMap = (java.util.Map<String, Object>) capMap;
                capabilities = typedCapMap;
            }
        }

        ProjectConnectionInfo projInfo = new ProjectConnectionInfo(
                projectId, session.getId(), clientProtocol, extVersion, unityVersion, capabilities, session);
        projectConnections.put(projectId, projInfo);
        sessionToProject.put(session.getId(), projectId);

        this.unitySession = session;

        if (projectMemoryService != null) {
            String projectName = null;
            if (msg.getData() != null) {
                if (msg.getData().containsKey("projectName")) {
                    projectName = String.valueOf(msg.getData().get("projectName"));
                } else if (msg.getData().containsKey("projectPath")) {
                    String p = String.valueOf(msg.getData().get("projectPath"));
                    projectName = p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) :
                            (p.contains("\\") ? p.substring(p.lastIndexOf('\\') + 1) : p);
                }
            }
            try {
                projectMemoryService.registerProject(projectId, unityVersion, projectName, extVersion, capabilities);
            } catch (Exception e) {
                log.warn("Failed to register project in memory service (non-fatal): {}", e.getMessage());
            }
        }

        UnityMessage ack = UnityMessage.handshakeAck(msg.getOperationId(), projectId);
        if (sendMessage(session, ack)) {
            this.state = ConnectionState.READY;
            log.info("Unity bridge is READY for projectId={} (session={})", projectId, session.getId());
            startPingTask();
        } else {
            this.state = ConnectionState.ERROR;
            log.error("Failed to send HANDSHAKE_ACK to projectId={}", projectId);
        }
    }

    private void handleToolResponse(UnityMessage msg) {
        String opId = msg.getOperationId();
        if (opId == null) {
            log.warn("Received TOOL_RESPONSE with no operationId");
            return;
        }

        CompletableFuture<UnityMessage> future = pendingRequests.remove(opId);
        if (future != null) {
            log.debug("Completing tool response for operationId={}", opId);
            future.complete(msg);
        } else {
            log.warn("No pending request for operationId={}", opId);
        }
    }

    private void handlePong(UnityMessage msg) {
        log.trace("Received PONG: operationId={}", msg.getOperationId());
    }

    private void handleEvent(UnityMessage msg) {
        log.info("Received EVENT from Unity: {}", msg.getData());
    }

    private void handleError(UnityMessage msg) {
        log.error("Received ERROR from Unity: {}", msg.getErrors());
        String opId = msg.getOperationId();
        if (opId != null) {
            CompletableFuture<UnityMessage> future = pendingRequests.remove(opId);
            if (future != null) {
                future.complete(msg);
            }
        }
    }

    // --- Send operations ---

    /**
     * Send a tool request to Unity and wait for the correlated response.
     *
     * @param request the TOOL_REQUEST message (must have operationId set)
     * @return the correlated TOOL_RESPONSE from Unity
     * @throws IllegalStateException if Unity is not READY
     * @throws TimeoutException if Unity does not respond within the configured timeout
     * @throws ExecutionException if the future completes exceptionally
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public UnityMessage sendToolRequest(UnityMessage request)
            throws TimeoutException, ExecutionException, InterruptedException {
        return sendToolRequest(request.getProjectId(), request);
    }

    /**
     * Send a tool request to a specific connected Unity project.
     */
    public UnityMessage sendToolRequest(String targetProjectId, UnityMessage request)
            throws TimeoutException, ExecutionException, InterruptedException {

        // Grace period for domain reload / play mode transition reconnect (up to 4 seconds)
        long deadline = System.currentTimeMillis() + 4000;
        while (!isReady() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        if (!isReady()) {
            throw new IllegalStateException("Unity is not READY (current state: " + state + ")");
        }

        WebSocketSession targetSession = null;
        while (System.currentTimeMillis() < deadline) {
            if (targetProjectId != null && projectConnections.containsKey(targetProjectId)) {
                targetSession = projectConnections.get(targetProjectId).getSession();
            }
            if (targetSession == null || !targetSession.isOpen()) {
                targetSession = this.unitySession;
            }
            if (targetSession == null || !targetSession.isOpen()) {
                for (ProjectConnectionInfo info : projectConnections.values()) {
                    if (info.getSession() != null && info.getSession().isOpen()) {
                        targetSession = info.getSession();
                        this.unitySession = targetSession;
                        this.state = ConnectionState.READY;
                        break;
                    }
                }
            }
            if (targetSession != null && targetSession.isOpen()) {
                break;
            }
            Thread.sleep(100);
        }

        if (targetSession == null || !targetSession.isOpen()) {
            throw new IllegalStateException("No open Unity WebSocket session available for execution");
        }

        String opId = request.getOperationId();
        CompletableFuture<UnityMessage> future = new CompletableFuture<>();
        pendingRequests.put(opId, future);

        try {
            if (!sendMessage(targetSession, request)) {
                pendingRequests.remove(opId);
                throw new IOException("Failed to send message to Unity");
            }
            return future.get(commandTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(opId);
            log.error("Tool request timed out: operationId={}, tool={}", opId, request.getTool());
            throw e;
        } catch (IOException e) {
            pendingRequests.remove(opId);
            throw new ExecutionException("Send failed", e);
        }
    }

    public java.util.List<ProjectConnectionInfo> getConnectedProjects() {
        return new java.util.ArrayList<>(projectConnections.values());
    }

    public ProjectConnectionInfo getProjectConnection(String projectId) {
        return projectId != null ? projectConnections.get(projectId) : null;
    }

    /**
     * Send a message to the given WebSocket session.
     *
     * @return true if sent successfully
     */
    private boolean sendMessage(WebSocketSession session, UnityMessage msg) {
        if (session == null || !session.isOpen()) {
            log.error("Cannot send message: session is null or closed");
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(msg);
            log.debug("Sending to Unity: {}", json);
            session.sendMessage(new TextMessage(json));
            return true;
        } catch (Exception e) {
            log.error("Failed to send message to Unity: {}", e.getMessage());
            return false;
        }
    }

    private void sendError(WebSocketSession session, String operationId, String code, String message) {
        sendMessage(session, UnityMessage.error(operationId, code, message));
    }

    // --- Heartbeat ---

    private void startPingTask() {
        stopPingTask();
        int interval = pingIntervalSeconds > 0 ? pingIntervalSeconds : 15;
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            if (isReady() && unitySession != null && unitySession.isOpen()) {
                sendMessage(unitySession, UnityMessage.ping());
            }
        }, interval, interval, TimeUnit.SECONDS);
        log.debug("Ping heartbeat started (interval={}s)", interval);
    }

    private void stopPingTask() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
            log.debug("Ping heartbeat stopped");
        }
    }

    // --- Cleanup ---

    /**
     * Fail all pending requests (e.g., on disconnect or transport error).
     */
    private void failAllPending(String reason) {
        int count = pendingRequests.size();
        if (count > 0) {
            log.warn("Failing {} pending requests: {}", count, reason);
            pendingRequests.forEach((opId, future) -> {
                future.completeExceptionally(new IOException("Request cancelled: " + reason));
            });
            pendingRequests.clear();
        }
    }

    /**
     * Get the number of pending (in-flight) requests.
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }
}
