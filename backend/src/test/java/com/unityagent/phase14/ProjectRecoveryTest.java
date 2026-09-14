package com.unityagent.phase14;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectRecoveryTest {

    private ObjectMapper objectMapper;
    private UnityConnection unityConnection;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        unityConnection = new UnityConnection(objectMapper);
    }

    @Test
    void testProjectDisconnectAndReconnectRecovery() throws Exception {
        String projectId = "proj_recover_123";

        // Initial connection
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("sess-first");
        when(session1.isOpen()).thenReturn(true);
        unityConnection.afterConnectionEstablished(session1);

        UnityMessage hs1 = UnityMessage.handshake("6000.0.0f1");
        hs1.setProjectId(projectId);
        hs1.setData(Map.of("projectId", projectId, "protocolVersion", "1.0", "unityVersion", "6000.0.0f1"));
        unityConnection.handleMessage(session1, new TextMessage(objectMapper.writeValueAsString(hs1)));

        assertTrue(unityConnection.isReady());
        assertNotNull(unityConnection.getProjectConnection(projectId));
        assertEquals("sess-first", unityConnection.getProjectConnection(projectId).getSession().getId());

        // Domain reload disconnect
        unityConnection.afterConnectionClosed(session1, CloseStatus.NORMAL);
        assertNull(unityConnection.getProjectConnection(projectId));

        // Reconnect with same stable projectId
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("sess-reconnected");
        when(session2.isOpen()).thenReturn(true);
        unityConnection.afterConnectionEstablished(session2);

        UnityMessage hs2 = UnityMessage.handshake("6000.0.0f1");
        hs2.setProjectId(projectId);
        hs2.setData(Map.of("projectId", projectId, "protocolVersion", "1.0", "unityVersion", "6000.0.0f1"));
        unityConnection.handleMessage(session2, new TextMessage(objectMapper.writeValueAsString(hs2)));

        assertTrue(unityConnection.isReady());
        assertNotNull(unityConnection.getProjectConnection(projectId));
        assertEquals("sess-reconnected", unityConnection.getProjectConnection(projectId).getSession().getId());
    }
}
