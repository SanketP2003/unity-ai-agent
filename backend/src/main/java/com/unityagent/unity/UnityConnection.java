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
    private int commandTimeoutSeconds;

    @Value("${unity.websocket.ping-interval-seconds:15}")
    private int pingIntervalSeconds;

    /** Current WebSocket session with Unity (null if disconnected). */
    private volatile WebSocketSession unitySession;

    /** Current connection state. */
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

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

    public UnityConnection(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // --- Connection state ---

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        HANDSHAKING,
        READY,
        ERROR
    }

    public ConnectionState getState() {
        return state;
    }

    public boolean isReady() {
        return state == ConnectionState.READY;
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
        this.unitySession = null;
        this.state = ConnectionState.DISCONNECTED;
        stopPingTask();
        failAllPending("Unity disconnected");
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Unity WebSocket transport error: {}", exception.getMessage());
        this.state = ConnectionState.ERROR;
        stopPingTask();
        failAllPending("Transport error: " + exception.getMessage());
    }

    // --- Protocol handlers ---

    private void handleHandshake(WebSocketSession session, UnityMessage msg) {
        log.info("Received HANDSHAKE from Unity: operationId={}, data={}", msg.getOperationId(), msg.getData());
        this.state = ConnectionState.HANDSHAKING;

        UnityMessage ack = UnityMessage.handshakeAck(msg.getOperationId());
        if (sendMessage(session, ack)) {
            this.state = ConnectionState.READY;
            log.info("Unity bridge is READY");
            startPingTask();
        } else {
            this.state = ConnectionState.ERROR;
            log.error("Failed to send HANDSHAKE_ACK");
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

        if (!isReady()) {
            throw new IllegalStateException("Unity is not READY (current state: " + state + ")");
        }

        String opId = request.getOperationId();
        CompletableFuture<UnityMessage> future = new CompletableFuture<>();
        pendingRequests.put(opId, future);

        try {
            if (!sendMessage(unitySession, request)) {
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
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            if (isReady() && unitySession != null && unitySession.isOpen()) {
                sendMessage(unitySession, UnityMessage.ping());
            }
        }, pingIntervalSeconds, pingIntervalSeconds, TimeUnit.SECONDS);
        log.debug("Ping heartbeat started (interval={}s)", pingIntervalSeconds);
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
