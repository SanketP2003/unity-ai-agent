package com.unityagent.unity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Project Isolation and Handshake Protocol Tests")
class ProjectIsolationTest {

    private ObjectMapper mapper;
    private UnityConnection connection;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        connection = new UnityConnection(mapper);
    }

    @Test
    @DisplayName("Should successfully negotiate handshake with protocol 1.0 and send HANDSHAKE_ACK with capabilities")
    void testHandshakeSuccess() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-project-a");
        when(session.isOpen()).thenReturn(true);

        connection.afterConnectionEstablished(session);
        assertEquals(UnityConnection.ConnectionState.CONNECTED, connection.getState());

        Map<String, Object> handshakeData = Map.of(
                "projectId", "my-unity-game-project",
                "projectName", "SpaceExplorationGame",
                "unityVersion", "6000.4.7f1",
                "extensionVersion", "1.0.0",
                "protocolVersion", "1.0"
        );
        UnityMessage handshakeMsg = UnityMessage.handshake("op-handshake-1", "my-unity-game-project", handshakeData);
        String payload = mapper.writeValueAsString(handshakeMsg);

        connection.handleTextMessage(session, new TextMessage(payload));

        assertEquals(UnityConnection.ConnectionState.READY, connection.getState());
        assertTrue(connection.isReady());

        // Verify that HANDSHAKE_ACK was sent with capabilities
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());

        TextMessage sentMessage = captor.getValue();
        UnityMessage ack = mapper.readValue(sentMessage.getPayload(), UnityMessage.class);

        assertEquals(UnityMessage.Type.HANDSHAKE_ACK, ack.getType());
        assertEquals("op-handshake-1", ack.getOperationId());
        assertEquals("my-unity-game-project", ack.getProjectId());
        assertNotNull(ack.getData());
        assertTrue(ack.getData().containsKey("capabilities"));

        // Verify project is tracked
        UnityConnection.ProjectConnectionInfo projInfo = connection.getProjectConnection("my-unity-game-project");
        assertNotNull(projInfo);
        assertEquals("my-unity-game-project", projInfo.getProjectId());
        assertEquals("6000.4.7f1", projInfo.getUnityVersion());
        assertEquals("1.0.0", projInfo.getExtensionVersion());
    }

    @Test
    @DisplayName("Should reject handshake with EXTENSION_PROTOCOL_MISMATCH if protocol version is incompatible")
    void testHandshakeProtocolMismatch() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-mismatch");
        when(session.isOpen()).thenReturn(true);

        connection.afterConnectionEstablished(session);

        Map<String, Object> handshakeData = Map.of(
                "projectId", "incompatible-project",
                "protocolVersion", "2.0" // Incompatible major version
        );
        UnityMessage handshakeMsg = UnityMessage.handshake("op-bad-version", "incompatible-project", handshakeData);
        String payload = mapper.writeValueAsString(handshakeMsg);

        connection.handleTextMessage(session, new TextMessage(payload));

        assertEquals(UnityConnection.ConnectionState.ERROR, connection.getState());

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());

        UnityMessage errorReply = mapper.readValue(captor.getValue().getPayload(), UnityMessage.class);
        assertEquals(UnityMessage.Type.ERROR, errorReply.getType());
        assertNotNull(errorReply.getErrors());
        assertTrue(errorReply.getErrors().stream().anyMatch(e -> "EXTENSION_PROTOCOL_MISMATCH".equals(e.getCode())));
    }

    @Test
    @DisplayName("Should track multiple project connections and clean up on disconnect")
    void testMultipleProjectConnectionsAndDisconnection() throws Exception {
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("sess-1");
        when(session1.isOpen()).thenReturn(true);

        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("sess-2");
        when(session2.isOpen()).thenReturn(true);

        // Connect Project 1
        connection.afterConnectionEstablished(session1);
        UnityMessage msg1 = UnityMessage.handshake("op-1", "project-alpha", Map.of("protocolVersion", "1.0"));
        connection.handleTextMessage(session1, new TextMessage(mapper.writeValueAsString(msg1)));

        // Connect Project 2
        connection.afterConnectionEstablished(session2);
        UnityMessage msg2 = UnityMessage.handshake("op-2", "project-beta", Map.of("protocolVersion", "1.0"));
        connection.handleTextMessage(session2, new TextMessage(mapper.writeValueAsString(msg2)));

        assertEquals(2, connection.getConnectedProjects().size());
        assertNotNull(connection.getProjectConnection("project-alpha"));
        assertNotNull(connection.getProjectConnection("project-beta"));

        // Disconnect Project 1
        connection.afterConnectionClosed(session1, CloseStatus.NORMAL);
        assertEquals(1, connection.getConnectedProjects().size());
        assertNull(connection.getProjectConnection("project-alpha"));
        assertNotNull(connection.getProjectConnection("project-beta"));
        assertTrue(connection.isReady());

        // Disconnect Project 2
        connection.afterConnectionClosed(session2, CloseStatus.NORMAL);
        assertEquals(0, connection.getConnectedProjects().size());
        assertFalse(connection.isReady());
        assertEquals(UnityConnection.ConnectionState.DISCONNECTED, connection.getState());
    }
}
