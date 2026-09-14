package com.unityagent.phase14;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiProjectConnectionTest {

    private ObjectMapper objectMapper;
    private UnityConnection unityConnection;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        unityConnection = new UnityConnection(objectMapper);
    }

    @Test
    void testMultipleSimultaneousProjectConnectionsWithoutCrosstalk() throws Exception {
        WebSocketSession sessionA = mock(WebSocketSession.class);
        when(sessionA.getId()).thenReturn("sess-proj-A");
        when(sessionA.isOpen()).thenReturn(true);
        unityConnection.afterConnectionEstablished(sessionA);

        UnityMessage hsA = UnityMessage.handshake("6000.0.0f1");
        hsA.setProjectId("proj_AAA");
        hsA.setData(Map.of("projectId", "proj_AAA", "protocolVersion", "1.0", "unityVersion", "6000.0.0f1"));
        unityConnection.handleMessage(sessionA, new TextMessage(objectMapper.writeValueAsString(hsA)));

        WebSocketSession sessionB = mock(WebSocketSession.class);
        when(sessionB.getId()).thenReturn("sess-proj-B");
        when(sessionB.isOpen()).thenReturn(true);
        unityConnection.afterConnectionEstablished(sessionB);

        UnityMessage hsB = UnityMessage.handshake("6000.0.0f1");
        hsB.setProjectId("proj_BBB");
        hsB.setData(Map.of("projectId", "proj_BBB", "protocolVersion", "1.0", "unityVersion", "6000.0.0f1"));
        unityConnection.handleMessage(sessionB, new TextMessage(objectMapper.writeValueAsString(hsB)));

        assertEquals(2, unityConnection.getConnectedProjects().size());

        var connA = unityConnection.getProjectConnection("proj_AAA");
        var connB = unityConnection.getProjectConnection("proj_BBB");

        assertNotNull(connA);
        assertNotNull(connB);
        assertEquals("sess-proj-A", connA.getSession().getId());
        assertEquals("sess-proj-B", connB.getSession().getId());

        // Disconnecting Project A leaves Project B intact
        unityConnection.afterConnectionClosed(sessionA, org.springframework.web.socket.CloseStatus.NORMAL);

        assertEquals(1, unityConnection.getConnectedProjects().size());
        assertNull(unityConnection.getProjectConnection("proj_AAA"));
        assertNotNull(unityConnection.getProjectConnection("proj_BBB"));
    }
}
