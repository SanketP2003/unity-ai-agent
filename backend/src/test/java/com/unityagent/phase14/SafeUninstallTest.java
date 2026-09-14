package com.unityagent.phase14;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.product.service.ExtensionLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SafeUninstallTest {

    @TempDir
    Path tempDir;

    private ExtensionLifecycleService lifecycleService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        lifecycleService = new ExtensionLifecycleService();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testUninstallRemovesExtensionOnlyAndPreservesAllGameAssets() throws Exception {
        Path projDir = tempDir.resolve("GameToUninstall");
        Path packagesDir = projDir.resolve("Packages");
        Path assetsDir = projDir.resolve("Assets");
        Path settingsDir = projDir.resolve("ProjectSettings");

        Files.createDirectories(packagesDir);
        Files.createDirectories(assetsDir);
        Files.createDirectories(settingsDir);

        // Assets
        Files.writeString(assetsDir.resolve("Player.cs"), "public class Player {}");
        Files.writeString(assetsDir.resolve("Level1.unity"), "unity scene data");
        Files.writeString(settingsDir.resolve("AutonomousAgentIdentity.json"), "{\"projectId\":\"proj_test\"}");

        // Manifest with multiple dependencies
        String initialManifest = """
                {
                  "dependencies": {
                    "com.unity.modules.ui": "1.0.0",
                    "com.unityagent.autonomous-ai-agent": "file:../../ext",
                    "com.unity.textmeshpro": "3.0.6"
                  }
                }
                """;
        Files.writeString(packagesDir.resolve("manifest.json"), initialManifest);

        // Perform safe uninstall with removeIdentity = true
        var result = lifecycleService.uninstallExtension(projDir.toString(), true);
        assertTrue(result.isSuccess());

        // Check manifest
        JsonNode manifest = objectMapper.readTree(packagesDir.resolve("manifest.json").toFile());
        JsonNode deps = manifest.get("dependencies");
        assertNotNull(deps);
        assertTrue(deps.has("com.unity.modules.ui"));
        assertTrue(deps.has("com.unity.textmeshpro"));
        assertFalse(deps.has("com.unityagent.autonomous-ai-agent")); // Extension successfully removed

        // Verify all game assets remain intact
        assertTrue(Files.exists(assetsDir.resolve("Player.cs")));
        assertEquals("public class Player {}", Files.readString(assetsDir.resolve("Player.cs")));
        assertTrue(Files.exists(assetsDir.resolve("Level1.unity")));

        // Verify identity file removed as requested
        assertFalse(Files.exists(settingsDir.resolve("AutonomousAgentIdentity.json")));
    }
}
