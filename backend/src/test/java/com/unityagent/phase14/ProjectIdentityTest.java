package com.unityagent.phase14;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.memory.MemoryDatabase;
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

import static org.junit.jupiter.api.Assertions.*;

class ProjectIdentityTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private ProjectIdentityService identityService;
    private UnityProjectDetector detector;
    private WorkspaceManager workspaceManager;
    private ProjectRegistrationService registrationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("phase14_identity.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        identityService = new ProjectIdentityService(db);
        detector = new UnityProjectDetector();
        workspaceManager = new WorkspaceManager(db);
        registrationService = new ProjectRegistrationService(db, detector, identityService, workspaceManager);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testProjectIdFormatValidation() {
        assertTrue(identityService.isValidProjectId("proj_a1b2c3d4e5f6"));
        assertTrue(identityService.isValidProjectId("proj_custom_1234"));
        assertTrue(identityService.isValidProjectId("project_legacy123"));

        assertFalse(identityService.isValidProjectId(null));
        assertFalse(identityService.isValidProjectId(""));
        assertFalse(identityService.isValidProjectId("invalid-prefix_123"));
        assertFalse(identityService.isValidProjectId("proj_!"));
    }

    @Test
    void testProjectIdGenerationIsUniqueAndStable() {
        String id1 = identityService.generateProjectId();
        String id2 = identityService.generateProjectId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("proj_"));
        assertTrue(identityService.isValidProjectId(id1));
    }

    @Test
    void testFingerprintComputation() {
        String fp1 = identityService.computeFingerprint("proj_1", "C:/Projects/Game", "2026-09-14T00:00:00Z");
        String fp2 = identityService.computeFingerprint("proj_1", "C:/Projects/Game", "2026-09-14T00:00:00Z");
        String fpDiff = identityService.computeFingerprint("proj_1", "C:/Projects/Game2", "2026-09-14T00:00:00Z");

        assertEquals(fp1, fp2);
        assertNotEquals(fp1, fpDiff);
    }

    @Test
    void testCloneDetectionWhenProjectMovedOrCopied() throws Exception {
        Path origDir = tempDir.resolve("OriginalGame");
        Files.createDirectories(origDir.resolve("Assets"));
        Files.createDirectories(origDir.resolve("ProjectSettings"));
        Files.writeString(origDir.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");

        var rec = registrationService.registerProject(origDir.toString(), "OriginalGame", null);
        String assignedId = rec.getProjectId();

        // Check same path -> no clone detected
        var checkSame = identityService.detectCloneOrMove(assignedId, origDir.toString());
        assertFalse(checkSame.isCloneDetected());

        // Clone/copy to new directory
        Path cloneDir = tempDir.resolve("ClonedGame");
        Files.createDirectories(cloneDir.resolve("Assets"));
        Files.createDirectories(cloneDir.resolve("ProjectSettings"));
        Files.writeString(cloneDir.resolve("ProjectSettings").resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.0.0f1\n");

        // Copy identity file to simulated clone
        Path idFileOrig = origDir.resolve("ProjectSettings").resolve("AutonomousAgentIdentity.json");
        Path idFileClone = cloneDir.resolve("ProjectSettings").resolve("AutonomousAgentIdentity.json");
        Files.copy(idFileOrig, idFileClone);

        var checkClone = identityService.detectCloneOrMove(assignedId, cloneDir.toString());
        assertTrue(checkClone.isCloneDetected());

        // Registering cloned directory assigns a new project ID for safety
        var recClone = registrationService.registerProject(cloneDir.toString(), "ClonedGame", null);
        assertNotEquals(assignedId, recClone.getProjectId());
    }
}
