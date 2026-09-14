package com.unityagent.phase14;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ProjectLifecycleState;
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

class ProjectRouterTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private WorkspaceManager workspaceManager;
    private ProjectIdentityService identityService;
    private UnityProjectDetector projectDetector;
    private ProjectRegistrationService registrationService;
    private UnityConnection unityConnection;
    private ProjectRouter projectRouter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("phase14_router.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
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
    void testMissingProjectContextThrowsRequiredError() {
        UnityMessage msg = UnityMessage.toolRequest("create_primitive", Map.of("type", "Cube"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            projectRouter.routeCommand("", msg);
        });
        assertTrue(ex.getMessage().contains("PROJECT_CONTEXT_REQUIRED"));
    }

    @Test
    void testUnknownProjectThrowsNotFoundError() {
        UnityMessage msg = UnityMessage.toolRequest("create_primitive", Map.of("type", "Cube"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            projectRouter.routeCommand("proj_random_unknown", msg);
        });
        assertTrue(ex.getMessage().contains("PROJECT_NOT_FOUND"));
    }

    @Test
    void testArchivedProjectRejectsCommands() throws Exception {
        Path pDir = tempDir.resolve("ArchivedGame");
        Files.createDirectories(pDir.resolve("Assets"));
        Files.createDirectories(pDir.resolve("ProjectSettings"));
        Files.writeString(pDir.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");

        workspaceManager.createWorkspace("ws_arch", "Test WS", tempDir.toString());
        var rec = registrationService.registerProject(pDir.toString(), "ArchivedGame", "ws_arch");

        workspaceManager.archiveProject(rec.getProjectId());

        UnityMessage msg = UnityMessage.toolRequest("create_primitive", Map.of("type", "Cube"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            projectRouter.routeCommand(rec.getProjectId(), msg);
        });
        assertTrue(ex.getMessage().contains("PROJECT_ACCESS_DENIED"));
    }

    @Test
    void testDisconnectedProjectThrowsDisconnectedAndDoesNotFallback() throws Exception {
        // Project 1: connected
        Path pDir1 = tempDir.resolve("ActiveGame");
        Files.createDirectories(pDir1.resolve("Assets"));
        Files.createDirectories(pDir1.resolve("ProjectSettings"));
        Files.writeString(pDir1.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");
        var rec1 = registrationService.registerProject(pDir1.toString(), "ActiveGame", null);

        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("sess-1");
        when(session1.isOpen()).thenReturn(true);
        unityConnection.afterConnectionEstablished(session1);
        UnityMessage hs1 = UnityMessage.handshake("6000.0.0f1");
        hs1.setProjectId(rec1.getProjectId());
        unityConnection.handleMessage(session1, new TextMessage(objectMapper.writeValueAsString(hs1)));

        // Project 2: registered but disconnected
        Path pDir2 = tempDir.resolve("OfflineGame");
        Files.createDirectories(pDir2.resolve("Assets"));
        Files.createDirectories(pDir2.resolve("ProjectSettings"));
        Files.writeString(pDir2.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");
        var rec2 = registrationService.registerProject(pDir2.toString(), "OfflineGame", null);

        UnityMessage msg = UnityMessage.toolRequest("create_primitive", Map.of("type", "Cube"));

        // Routing to Project 2 must NOT silently route to Project 1!
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            projectRouter.routeCommand(rec2.getProjectId(), msg);
        });
        assertTrue(ex.getMessage().contains("PROJECT_DISCONNECTED"));
    }
}
