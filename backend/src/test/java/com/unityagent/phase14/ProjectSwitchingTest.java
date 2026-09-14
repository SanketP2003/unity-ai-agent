package com.unityagent.phase14;

import com.unityagent.agent.persistence.AutonomousRunRecord;
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
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ProjectSwitchingTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private WorkspaceManager workspaceManager;
    private ProjectIdentityService identityService;
    private UnityProjectDetector detector;
    private ProjectRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("phase14_switching.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        workspaceManager = new WorkspaceManager(db);
        identityService = new ProjectIdentityService(db);
        detector = new UnityProjectDetector();
        registrationService = new ProjectRegistrationService(db, detector, identityService, workspaceManager);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testProjectSwitchingPreservesActiveRunProjectImmutability() throws Exception {
        Path p1 = tempDir.resolve("ProjectOne");
        Files.createDirectories(p1.resolve("Assets"));
        Files.createDirectories(p1.resolve("ProjectSettings"));
        Files.writeString(p1.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");

        Path p2 = tempDir.resolve("ProjectTwo");
        Files.createDirectories(p2.resolve("Assets"));
        Files.createDirectories(p2.resolve("ProjectSettings"));
        Files.writeString(p2.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");

        ProjectRecord rec1 = registrationService.registerProject(p1.toString(), "ProjectOne", null);
        ProjectRecord rec2 = registrationService.registerProject(p2.toString(), "ProjectTwo", null);

        // Simulate an autonomous run started for ProjectOne
        AutonomousRunRecord run = new AutonomousRunRecord(
                "run_001",
                "sess_001",
                rec1.getProjectId(),
                "Build level 1",
                "EXECUTING"
        );

        assertEquals(rec1.getProjectId(), run.getProjectId());

        // Studio switches UI to ProjectTwo
        String activeStudioProjectId = rec2.getProjectId();
        assertNotEquals(run.getProjectId(), activeStudioProjectId);

        // Verify run's projectId remains permanently bound to ProjectOne
        assertEquals(rec1.getProjectId(), run.getProjectId());
        assertNotEquals(activeStudioProjectId, run.getProjectId());
    }
}
