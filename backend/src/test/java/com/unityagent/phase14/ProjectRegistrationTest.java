package com.unityagent.phase14;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ProjectRecord;
import com.unityagent.product.service.ProjectIdentityService;
import com.unityagent.product.service.ProjectRegistrationService;
import com.unityagent.product.service.UnityProjectDetector;
import com.unityagent.product.service.WorkspaceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRegistrationTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private WorkspaceManager workspaceManager;
    private ProjectIdentityService identityService;
    private UnityProjectDetector projectDetector;
    private ProjectRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("phase14_reg.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        workspaceManager = new WorkspaceManager(db);
        identityService = new ProjectIdentityService(db);
        projectDetector = new UnityProjectDetector();
        registrationService = new ProjectRegistrationService(db, projectDetector, identityService, workspaceManager);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testRegisterNewProjectPersistsInSQLiteSSOT() throws Exception {
        Path pDir = tempDir.resolve("RPGGame");
        Files.createDirectories(pDir.resolve("Assets"));
        Files.createDirectories(pDir.resolve("ProjectSettings"));
        Files.writeString(pDir.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.4.7f1\n");

        ProjectRecord rec = registrationService.registerProject(pDir.toString(), "My Epic RPG", null);

        assertNotNull(rec);
        assertTrue(rec.getProjectId().startsWith("proj_"));
        assertEquals("My Epic RPG", rec.getProjectName());
        assertEquals("6000.4.7f1", rec.getUnityVersion());
        assertNotNull(rec.getProjectFingerprint());
        assertNotNull(rec.getCreatedAt());

        // Verify stored in SQLite
        Optional<ProjectRecord> readBack = registrationService.getProject(rec.getProjectId());
        assertTrue(readBack.isPresent());
        assertEquals(rec.getProjectId(), readBack.get().getProjectId());
        assertEquals("My Epic RPG", readBack.get().getProjectName());

        // Verify identity file written in ProjectSettings/
        Path idFile = pDir.resolve("ProjectSettings").resolve("AutonomousAgentIdentity.json");
        assertTrue(Files.exists(idFile));
        String idJson = Files.readString(idFile);
        assertTrue(idJson.contains(rec.getProjectId()));
    }

    @Test
    void testRegisterWithWorkspaceLinkage() throws Exception {
        Path pDir = tempDir.resolve("StrategyGame");
        Files.createDirectories(pDir.resolve("Assets"));
        Files.createDirectories(pDir.resolve("ProjectSettings"));
        Files.writeString(pDir.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 2022.3.10f1\n");

        workspaceManager.createWorkspace("ws_strat", "Strategy Workspace", tempDir.toString());
        ProjectRecord rec = registrationService.registerProject(pDir.toString(), "StrategyGame", "ws_strat");

        var wsProjects = workspaceManager.listWorkspaceProjects("ws_strat");
        assertEquals(1, wsProjects.size());
        assertEquals(rec.getProjectId(), wsProjects.get(0).getProjectId());
    }

    @Test
    void testListRegisteredProjects() throws Exception {
        Path p1 = tempDir.resolve("P1");
        Files.createDirectories(p1.resolve("Assets"));
        Files.createDirectories(p1.resolve("ProjectSettings"));
        Files.writeString(p1.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");

        Path p2 = tempDir.resolve("P2");
        Files.createDirectories(p2.resolve("Assets"));
        Files.createDirectories(p2.resolve("ProjectSettings"));
        Files.writeString(p2.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");

        registrationService.registerProject(p1.toString(), "P1", null);
        registrationService.registerProject(p2.toString(), "P2", null);

        List<ProjectRecord> list = registrationService.listProjects();
        assertEquals(2, list.size());
    }

    @Test
    void testRegisterInvalidPathThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            registrationService.registerProject(tempDir.resolve("NonExistent").toString(), "Bad", null);
        });
    }
}
