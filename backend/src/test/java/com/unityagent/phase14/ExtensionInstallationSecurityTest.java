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

class ExtensionInstallationSecurityTest {

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
    void testSafeExtensionInstallation() throws Exception {
        Path projDir = tempDir.resolve("UserGame");
        Path packagesDir = projDir.resolve("Packages");
        Path assetsDir = projDir.resolve("Assets");
        Files.createDirectories(packagesDir);
        Files.createDirectories(assetsDir);

        Files.writeString(assetsDir.resolve("MyOriginalScript.cs"), "public class MyOriginalScript {}");
        Files.writeString(packagesDir.resolve("manifest.json"),
                "{\n  \"dependencies\": {\n    \"com.unity.textmeshpro\": \"3.0.6\"\n  }\n}");

        var result = lifecycleService.installExtension(projDir.toString(), "file:../../extension");
        assertTrue(result.isSuccess());
        assertNotNull(result.getBackupPath());
        assertTrue(Files.exists(Path.of(result.getBackupPath())));

        // Verify manifest updated safely
        JsonNode manifest = objectMapper.readTree(packagesDir.resolve("manifest.json").toFile());
        assertTrue(manifest.has("dependencies"));
        JsonNode deps = manifest.get("dependencies");
        assertTrue(deps.has("com.unity.textmeshpro")); // original preserved
        assertTrue(deps.has("com.unityagent.autonomous-ai-agent")); // added
        assertEquals("file:../../extension", deps.get("com.unityagent.autonomous-ai-agent").asText());

        // Verify Assets untouched
        assertTrue(Files.exists(assetsDir.resolve("MyOriginalScript.cs")));
        assertEquals("public class MyOriginalScript {}", Files.readString(assetsDir.resolve("MyOriginalScript.cs")));
    }
}
