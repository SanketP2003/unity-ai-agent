package com.unityagent.product;

import com.unityagent.product.service.ProductInstallationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ProductInstallationTest {

    private ProductInstallationService installationService;

    @BeforeEach
    void setUp() {
        installationService = new ProductInstallationService();
    }

    @Test
    void testScaffoldInstallationAndRepair(@TempDir Path tempDir) throws IOException {
        Path installRoot = tempDir.resolve("AutonomousGameStudio");

        ProductInstallationService.InstallResult result = installationService.installOrRepair(installRoot);
        assertTrue(result.success());
        assertEquals(ProductInstallationService.CURRENT_PRODUCT_VERSION, result.version());
        assertTrue(result.installedComponents().contains("launcher"));
        assertTrue(result.installedComponents().contains("backend"));
        assertTrue(result.installedComponents().contains("unity-agent-extension"));

        // Verify files exist
        assertTrue(Files.exists(installRoot.resolve("launcher/start.bat")));
        assertTrue(Files.exists(installRoot.resolve("launcher/start.sh")));
        assertTrue(Files.exists(installRoot.resolve("runtime/version.properties")));

        // Verify cleanliness
        assertTrue(installationService.verifyPathCleanliness(installRoot));

        // Test repair: delete a directory and file
        Files.deleteIfExists(installRoot.resolve("launcher/start.bat"));
        ProductInstallationService.InstallResult repairResult = installationService.installOrRepair(installRoot);
        assertTrue(repairResult.success());
        assertTrue(Files.exists(installRoot.resolve("launcher/start.bat")));
    }

    @Test
    void testUpgrade(@TempDir Path tempDir) throws IOException {
        Path installRoot = tempDir.resolve("AutonomousGameStudio");
        installationService.installOrRepair(installRoot);

        ProductInstallationService.InstallResult upgradeResult = installationService.upgrade(installRoot, "1.1.0");
        assertTrue(upgradeResult.success());
        assertEquals("1.1.0", upgradeResult.version());

        String versionProps = Files.readString(installRoot.resolve("runtime/version.properties"));
        assertTrue(versionProps.contains("product.version=1.1.0"));
        assertTrue(versionProps.contains("upgraded.at="));
    }

    @Test
    void testPathCleanlinessDetection(@TempDir Path tempDir) throws IOException {
        Path installRoot = tempDir.resolve("AutonomousGameStudio");
        installationService.installOrRepair(installRoot);

        assertTrue(installationService.verifyPathCleanliness(installRoot));

        // Inject hardcoded absolute developer path
        Path dirtyConfig = installRoot.resolve("config/app.properties");
        Files.writeString(dirtyConfig, "user.dir=C:\\Users\\dev\\project\n");

        assertFalse(installationService.verifyPathCleanliness(installRoot));
    }
}
