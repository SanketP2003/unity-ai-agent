package com.unityagent.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unity Package Manager (UPM) Distribution Tests")
class UnityPackageDistributionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // Locate unity-agent-extension relative to backend project root
    private Path getExtensionPath() {
        Path path = Paths.get("..", "unity-agent-extension", "com.unityagent.autonomous-ai-agent").toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            // Fallback for execution from root workspace
            path = Paths.get("unity-agent-extension", "com.unityagent.autonomous-ai-agent").toAbsolutePath().normalize();
        }
        return path;
    }

    @Test
    @DisplayName("Verify package.json validity, semver, and required UPM metadata")
    void testPackageJsonValidity() throws Exception {
        Path packageJson = getExtensionPath().resolve("package.json");
        assertTrue(Files.exists(packageJson), "package.json must exist at " + packageJson);

        JsonNode root = mapper.readTree(packageJson.toFile());
        assertEquals("com.unityagent.autonomous-ai-agent", root.get("name").asText());
        assertNotNull(root.get("version").asText());
        assertTrue(root.get("version").asText().matches("^\\d+\\.\\d+\\.\\d+.*$"), "Version must be SemVer compliant");
        assertNotNull(root.get("displayName"));
        assertNotNull(root.get("unity"));
    }

    @Test
    @DisplayName("Verify runtime and editor .asmdef files are valid JSON definitions")
    void testAsmdefFilesValidity() throws Exception {
        Path ext = getExtensionPath();
        Path runtimeAsm = ext.resolve("Runtime/com.unityagent.runtime.asmdef");
        Path editorAsm = ext.resolve("Editor/com.unityagent.editor.asmdef");

        assertTrue(Files.exists(runtimeAsm), "Runtime asmdef must exist");
        assertTrue(Files.exists(editorAsm), "Editor asmdef must exist");

        JsonNode runtimeNode = mapper.readTree(runtimeAsm.toFile());
        assertEquals("com.unityagent.runtime", runtimeNode.get("name").asText());

        JsonNode editorNode = mapper.readTree(editorAsm.toFile());
        assertEquals("com.unityagent.editor", editorNode.get("name").asText());
    }

    @Test
    @DisplayName("Verify no hardcoded machine-specific absolute paths exist in package code")
    void testNoHardcodedAbsolutePaths() throws IOException {
        Path ext = getExtensionPath();
        List<String> violations = new ArrayList<>();

        Files.walkFileTree(ext, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (name.endsWith(".cs") || name.endsWith(".json") || name.endsWith(".asmdef")) {
                    String content = Files.readString(file);
                    if (content.contains("C:\\Users\\") || content.contains("/home/") || content.contains("/Users/")) {
                        violations.add(file.toString());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertTrue(violations.isEmpty(), "Found hardcoded absolute user paths in package files: " + violations);
    }

    @Test
    @DisplayName("Verify BuildTools and platform capability registration")
    void testBuildToolsPresence() throws IOException {
        Path ext = getExtensionPath();
        Path buildTools = ext.resolve("Editor/Tools/BuildTools.cs");
        Path agentBridge = ext.resolve("Editor/AgentBridge.cs");

        assertTrue(Files.exists(buildTools), "BuildTools.cs must exist");
        assertTrue(Files.exists(agentBridge), "AgentBridge.cs must exist");

        String buildContent = Files.readString(buildTools);
        assertTrue(buildContent.contains("get_platform_capabilities"), "Must declare get_platform_capabilities tool");
        assertTrue(buildContent.contains("build_project"), "Must declare build_project tool");

        String bridgeContent = Files.readString(agentBridge);
        assertTrue(bridgeContent.contains("GetPlatformCapabilitiesTool"), "Must register GetPlatformCapabilitiesTool");
        assertTrue(bridgeContent.contains("BuildProjectTool"), "Must register BuildProjectTool");
    }
}
