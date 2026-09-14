package com.unityagent.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.reliability.ToolExecutionTracker;
import com.unityagent.memory.service.ProjectMemoryService;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UnityReliabilityTest {

    private ObjectMapper objectMapper;
    private ProjectMemoryService memoryService;
    private ToolExecutionTracker tracker;
    private UnityConnection connection;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        memoryService = mock(ProjectMemoryService.class);
        tracker = mock(ToolExecutionTracker.class);
        connection = new UnityConnection(objectMapper, memoryService, tracker);
    }

    @Test
    void testInitialStateIsDisconnected() {
        assertEquals(UnityConnection.ConnectionState.DISCONNECTED, connection.getState());
        assertFalse(connection.isReady());
        assertEquals(0, connection.getConnectedProjects().size());
    }

    @Test
    void testCompleteHandshakeTransitionsToReady() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-001");
        when(session.isOpen()).thenReturn(true);

        connection.afterConnectionEstablished(session);
        assertEquals(UnityConnection.ConnectionState.CONNECTED, connection.getState());

        // Send valid handshake
        UnityMessage handshake = UnityMessage.handshake("handshake-op-1", "proj-alpha", Map.of(
                "protocolVersion", "1.0",
                "unityVersion", "2022.3.20f1",
                "extensionVersion", "1.0.0"
        ));

        TextMessage textMsg = new TextMessage(objectMapper.writeValueAsString(handshake));
        connection.handleMessage(session, textMsg);

        assertEquals(UnityConnection.ConnectionState.READY, connection.getState());
        assertTrue(connection.isReady());
        assertEquals(1, connection.getConnectedProjects().size());
        assertEquals("proj-alpha", connection.getConnectedProjects().get(0).getProjectId());

        // Disconnect
        connection.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertEquals(UnityConnection.ConnectionState.DISCONNECTED, connection.getState());
        assertFalse(connection.isReady());
        assertEquals(0, connection.getConnectedProjects().size());
    }

    @Test
    void testProtocolMismatchRejection() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-bad-proto");
        when(session.isOpen()).thenReturn(true);

        connection.afterConnectionEstablished(session);

        UnityMessage handshake = UnityMessage.handshake("handshake-op-bad", "proj-bad", Map.of("protocolVersion", "2.5")); // incompatible major version

        TextMessage textMsg = new TextMessage(objectMapper.writeValueAsString(handshake));
        connection.handleMessage(session, textMsg);

        assertEquals(UnityConnection.ConnectionState.ERROR, connection.getState());
        assertFalse(connection.isReady());
        verify(session).sendMessage(argThat(msg -> {
            String payload = ((TextMessage) msg).getPayload();
            return payload.contains("EXTENSION_PROTOCOL_MISMATCH");
        }));
    }

    @Test
    void testInFlightRequestCancellationOnAbruptDisconnect() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-inflight");
        when(session.isOpen()).thenReturn(true);

        connection.afterConnectionEstablished(session);
        UnityMessage handshake = UnityMessage.handshake("hs-1", "proj-flight", Map.of("protocolVersion", "1.0"));
        connection.handleMessage(session, new TextMessage(objectMapper.writeValueAsString(handshake)));

        assertTrue(connection.isReady());

        // Launch an asynchronous tool request
        UnityMessage toolReq = UnityMessage.toolRequest("op-flight-1", "test_tool", Map.of());
        toolReq.setProjectId("proj-flight");

        var future = Executors.newSingleThreadExecutor().submit(() -> {
            try {
                return connection.sendToolRequest("proj-flight", toolReq);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait brief moment for pending request registration
        Thread.sleep(100);
        assertEquals(1, connection.getPendingRequestCount());

        // Abrupt disconnect while waiting
        connection.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        // Verify request failed cleanly
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
        assertTrue(ex.getCause().getMessage().contains("Unity disconnected"));
        assertEquals(0, connection.getPendingRequestCount());
        verify(tracker).markInFlightAsUnknown(eq("proj-flight"), anyString());
    }

    @Test
    void testDomainReloadReconnectionSequence() throws Exception {
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("sess-reload-1");
        when(session1.isOpen()).thenReturn(true);

        connection.afterConnectionEstablished(session1);
        connection.handleMessage(session1, new TextMessage(objectMapper.writeValueAsString(
                UnityMessage.handshake("hs-r1", "proj-reloaded", Map.of("protocolVersion", "1.0"))
        )));
        assertTrue(connection.isReady());

        // Domain reload triggers disconnect
        connection.afterConnectionClosed(session1, CloseStatus.NORMAL);
        assertFalse(connection.isReady());

        // Domain reload finishes and reconnects with new session
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("sess-reload-2");
        when(session2.isOpen()).thenReturn(true);

        connection.afterConnectionEstablished(session2);
        connection.handleMessage(session2, new TextMessage(objectMapper.writeValueAsString(
                UnityMessage.handshake("hs-r2", "proj-reloaded", Map.of("protocolVersion", "1.0"))
        )));

        assertTrue(connection.isReady());
        assertEquals(1, connection.getConnectedProjects().size());
        assertEquals("sess-reload-2", connection.getConnectedProjects().get(0).getConnectionId());
    }
}
