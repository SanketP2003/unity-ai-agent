package com.unityagent.phase14;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.product.service.ProjectIdentityService;
import com.unityagent.product.service.ProjectRegistrationService;
import com.unityagent.product.service.UnityProjectDetector;
import com.unityagent.product.service.WorkspaceManager;
import com.unityagent.unity.ProjectRouter;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UniversalProjectIsolationTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private WorkspaceManager workspaceManager;
    private ProjectIdentityService identityService;
    private UnityProjectDetector projectDetector;
    private ProjectRegistrationService registrationService;
    private UnityConnection unityConnection;
    private ProjectRouter projectRouter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("phase14_iso.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        workspaceManager = new WorkspaceManager(db);
        identityService = new ProjectIdentityService(db);
        projectDetector = new UnityProjectDetector();
        registrationService = new ProjectRegistrationService(db, projectDetector, identityService, workspaceManager);

        objectMapper = new ObjectMapper();
        unityConnection = new UnityConnection(objectMapper);
        projectRouter = new ProjectRouter(unityConnection, registrationService, workspaceManager);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testTwoProjectsAreCompletelyIsolatedInRoutingAndIdentity() throws Exception {
        // Setup mock Unity Project A
        Path projDirA = tempDir.resolve("ProjectA");
        Files.createDirectories(projDirA.resolve("Assets"));
        Files.createDirectories(projDirA.resolve("ProjectSettings"));
        Files.writeString(projDirA.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");

        // Setup mock Unity Project B
        Path projDirB = tempDir.resolve("ProjectB");
        Files.createDirectories(projDirB.resolve("Assets"));
        Files.createDirectories(projDirB.resolve("ProjectSettings"));
        Files.writeString(projDirB.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");

        var recA = registrationService.registerProject(projDirA.toString(), "ProjectA", null);
        var recB = registrationService.registerProject(projDirB.toString(), "ProjectB", null);

        assertNotEquals(recA.getProjectId(), recB.getProjectId());

        // Connect Session A
        WebSocketSession sessionA = mock(WebSocketSession.class);
        when(sessionA.getId()).thenReturn("sess-a-123");
        when(sessionA.isOpen()).thenReturn(true);
        unityConnection.afterConnectionEstablished(sessionA);

        UnityMessage hsA = UnityMessage.handshake("6000.0.0f1");
        hsA.setProjectId(recA.getProjectId());
        hsA.setData(Map.of("projectId", recA.getProjectId(), "protocolVersion", "1.0", "unityVersion", "6000.0.0f1"));
        unityConnection.handleMessage(sessionA, new TextMessage(objectMapper.writeValueAsString(hsA)));

        // Connect Session B
        WebSocketSession sessionB = mock(WebSocketSession.class);
        when(sessionB.getId()).thenReturn("sess-b-456");
        when(sessionB.isOpen()).thenReturn(true);
        unityConnection.afterConnectionEstablished(sessionB);

        UnityMessage hsB = UnityMessage.handshake("6000.0.0f1");
        hsB.setProjectId(recB.getProjectId());
        hsB.setData(Map.of("projectId", recB.getProjectId(), "protocolVersion", "1.0", "unityVersion", "6000.0.0f1"));
        unityConnection.handleMessage(sessionB, new TextMessage(objectMapper.writeValueAsString(hsB)));

        assertTrue(projectRouter.isProjectConnected(recA.getProjectId()));
        assertTrue(projectRouter.isProjectConnected(recB.getProjectId()));

        var connA = projectRouter.getConnection(recA.getProjectId());
        var connB = projectRouter.getConnection(recB.getProjectId());

        assertTrue(connA.isPresent());
        assertTrue(connB.isPresent());
        assertEquals("sess-a-123", connA.get().getSession().getId());
        assertEquals("sess-b-456", connB.get().getSession().getId());
        assertNotEquals(connA.get().getSession().getId(), connB.get().getSession().getId());
    }

    @Test
    void testCommandToUnknownProjectIsRejected() {
        UnityMessage req = UnityMessage.toolRequest("create_primitive", Map.of("type", "Cube"));
        assertThrows(IllegalArgumentException.class, () -> {
            projectRouter.routeCommand("proj_nonexistent_999", req);
        });
    }

    @Test
    void testCommandWithoutProjectContextIsRejected() {
        UnityMessage req = UnityMessage.toolRequest("create_primitive", Map.of("type", "Cube"));
        assertThrows(IllegalStateException.class, () -> {
            projectRouter.routeCommand(null, req);
        });
    }
}
