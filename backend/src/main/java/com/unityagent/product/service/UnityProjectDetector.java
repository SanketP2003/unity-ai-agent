package com.unityagent.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.product.model.UnityProjectScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service to detect, inspect, and validate Unity project directories on the filesystem.
 */
@Service
public class UnityProjectDetector {

    private static final Logger log = LoggerFactory.getLogger(UnityProjectDetector.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Inspects a directory path and determines whether it contains a valid Unity project,
     * its version, compatibility, extension status, and identity.
     */
    public UnityProjectScanResult detectProject(String directoryPath) {
        UnityProjectScanResult result = new UnityProjectScanResult();
        if (directoryPath == null || directoryPath.trim().isEmpty()) {
            result.setValid(false);
            result.getErrors().add("Project path cannot be null or empty");
            return result;
        }

        Path rootPath;
        try {
            rootPath = Paths.get(directoryPath.trim()).toAbsolutePath().normalize();
        } catch (Exception e) {
            result.setValid(false);
            result.getErrors().add("Invalid filesystem path: " + e.getMessage());
            return result;
        }

        result.setProjectPath(rootPath.toString().replace('\\', '/'));

        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            result.setValid(false);
            result.getErrors().add("Directory does not exist: " + rootPath);
            return result;
        }

        // 1. Check for Assets/
        Path assetsDir = rootPath.resolve("Assets");
        if (!Files.exists(assetsDir) || !Files.isDirectory(assetsDir)) {
            result.setValid(false);
            result.getErrors().add("Directory is not a Unity project (missing Assets folder)");
            return result;
        }

        result.setValid(true);
        result.setProjectName(rootPath.getFileName().toString());

        // 2. Read Unity Version from ProjectSettings/ProjectVersion.txt
        Path projectVersionFile = rootPath.resolve("ProjectSettings").resolve("ProjectVersion.txt");
        if (Files.exists(projectVersionFile)) {
            try {
                List<String> lines = Files.readAllLines(projectVersionFile);
                for (String line : lines) {
                    if (line.startsWith("m_EditorVersion:")) {
                        String version = line.substring("m_EditorVersion:".length()).trim();
                        result.setUnityVersion(version);
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read ProjectVersion.txt in {}: {}", rootPath, e.getMessage());
                result.getWarnings().add("Could not read ProjectVersion.txt: " + e.getMessage());
            }
        }

        // Check compatibility (Unity 2021+ or 6000+)
        if (result.getUnityVersion() != null) {
            String v = result.getUnityVersion();
            boolean comp = v.startsWith("2021.") || v.startsWith("2022.") || v.startsWith("2023.") || v.startsWith("6000.");
            result.setCompatible(comp);
            if (!comp) {
                result.getWarnings().add("Unity version " + v + " may not be officially supported (Recommended: 2021.3+, 2022.3+, or 6000.x)");
            }
        } else {
            result.setCompatible(true); // default optimistic if version file missing
        }

        // 3. Check for Extension Installation in Packages/manifest.json
        Path manifestFile = rootPath.resolve("Packages").resolve("manifest.json");
        if (Files.exists(manifestFile)) {
            try {
                JsonNode rootNode = objectMapper.readTree(manifestFile.toFile());
                JsonNode deps = rootNode.get("dependencies");
                if (deps != null && deps.has("com.unityagent.autonomous-ai-agent")) {
                    result.setExtensionInstalled(true);
                    result.setExtensionVersion(deps.get("com.unityagent.autonomous-ai-agent").asText());
                } else {
                    result.setExtensionInstalled(false);
                }
            } catch (Exception e) {
                log.warn("Failed to read Packages/manifest.json: {}", e.getMessage());
                result.getWarnings().add("Could not parse Packages/manifest.json: " + e.getMessage());
            }
        }

        // 4. Check for AutonomousAgentIdentity.json in ProjectSettings/
        Path identityFile = rootPath.resolve("ProjectSettings").resolve("AutonomousAgentIdentity.json");
        if (Files.exists(identityFile)) {
            try {
                JsonNode idNode = objectMapper.readTree(identityFile.toFile());
                if (idNode.has("projectId")) {
                    result.setExistingProjectId(idNode.get("projectId").asText());
                }
                if (idNode.has("projectName") && !idNode.get("projectName").asText().isEmpty()) {
                    result.setProjectName(idNode.get("projectName").asText());
                }
            } catch (Exception e) {
                log.warn("Failed to read AutonomousAgentIdentity.json: {}", e.getMessage());
                result.getWarnings().add("Could not parse AutonomousAgentIdentity.json: " + e.getMessage());
            }
        }

        // 5. Check if project is fresh / empty
        try (Stream<Path> stream = Files.walk(assetsDir)) {
            long assetFileCount = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().endsWith(".meta"))
                    .count();
            // 0 or 1 file (like SampleScene.unity) is considered a clean/fresh starting project
            result.setEmptyProject(assetFileCount <= 1);
        } catch (Exception e) {
            result.setEmptyProject(false);
        }

        return result;
    }
}
