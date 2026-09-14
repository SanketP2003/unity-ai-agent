package com.unityagent.phase14;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ProjectRecord;
import com.unityagent.product.model.UnityProjectScanResult;
import com.unityagent.product.service.ProjectAssetResolutionService;
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

class ExistingProjectIntegrationTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private UnityProjectDetector detector;
    private ProjectIdentityService identityService;
    private WorkspaceManager workspaceManager;
    private ProjectRegistrationService registrationService;
    private ProjectAssetResolutionService assetResolutionService;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("phase14_existing.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        detector = new UnityProjectDetector();
        identityService = new ProjectIdentityService(db);
        workspaceManager = new WorkspaceManager(db);
        registrationService = new ProjectRegistrationService(db, detector, identityService, workspaceManager);
        assetResolutionService = new ProjectAssetResolutionService();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testExistingNonEmptyProjectIntegrationPreservesUserData() throws Exception {
        Path projDir = tempDir.resolve("ExistingGameProject");
        Path assetsDir = projDir.resolve("Assets");
        Path scriptsDir = assetsDir.resolve("Scripts");
        Path scenesDir = assetsDir.resolve("Scenes");
        Path packagesDir = projDir.resolve("Packages");
        Path settingsDir = projDir.resolve("ProjectSettings");

        Files.createDirectories(scriptsDir);
        Files.createDirectories(scenesDir);
        Files.createDirectories(packagesDir);
        Files.createDirectories(settingsDir);

        // Populate existing user content
        Files.writeString(settingsDir.resolve("ProjectVersion.txt"), "m_EditorVersion: 6000.4.7f1\n");
        Files.writeString(packagesDir.resolve("manifest.json"), "{\n  \"dependencies\": {\n    \"com.unity.modules.ui\": \"1.0.0\"\n  }\n}");
        Files.writeString(scriptsDir.resolve("PlayerController.cs"), "public class PlayerController : UnityEngine.MonoBehaviour {}");
        Files.writeString(scenesDir.resolve("MainLevel.unity"), "--- !u!29 &1\nOcclusionCullingSettings:\n");

        // 1. Detection
        UnityProjectScanResult scan = detector.detectProject(projDir.toString());
        assertTrue(scan.isValid());
        assertEquals("6000.4.7f1", scan.getUnityVersion());
        assertTrue(scan.isCompatible());
        assertFalse(scan.isExtensionInstalled());
        assertFalse(scan.isEmptyProject()); // Existing non-empty project

        // 2. Registration
        ProjectRecord rec = registrationService.registerProject(projDir.toString(), "ExistingGameProject", null);
        assertNotNull(rec.getProjectId());

        // 3. Verify user content untouched
        assertTrue(Files.exists(scriptsDir.resolve("PlayerController.cs")));
        assertEquals("public class PlayerController : UnityEngine.MonoBehaviour {}",
                Files.readString(scriptsDir.resolve("PlayerController.cs")));
        assertTrue(Files.exists(scenesDir.resolve("MainLevel.unity")));

        // 4. Entity resolution recognizes existing assets
        assertTrue(assetResolutionService.shouldReuse(projDir.toString(), ".cs", "PlayerController"));
        var foundScript = assetResolutionService.findExistingAsset(projDir.toString(), ".cs", "PlayerController");
        assertTrue(foundScript.isPresent());
        assertTrue(foundScript.get().contains("PlayerController.cs"));
    }
}
