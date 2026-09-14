package com.unityagent.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Service managing safe installation and uninstallation of the Unity AI Agent extension.
 * Operates strictly on Packages/manifest.json without modifying user game assets.
 */
@Service
public class ExtensionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ExtensionLifecycleService.class);
    private static final String PACKAGE_NAME = "com.unityagent.autonomous-ai-agent";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class LifecycleResult {
        private final boolean success;
        private final String message;
        private final String backupPath;

        public LifecycleResult(boolean success, String message, String backupPath) {
            this.success = success;
            this.message = message;
            this.backupPath = backupPath;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getBackupPath() {
            return backupPath;
        }
    }

    /**
     * Safely installs the Autonomous Agent extension package into the target Unity project.
     * Creates a backup of Packages/manifest.json before making changes.
     */
    public LifecycleResult installExtension(String projectPath, String packageSource) {
        if (projectPath == null || projectPath.isBlank()) {
            return new LifecycleResult(false, "Project path cannot be null or empty", null);
        }

        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        Path manifestPath = root.resolve("Packages").resolve("manifest.json");

        if (!Files.exists(manifestPath)) {
            return new LifecycleResult(false, "Packages/manifest.json not found in " + projectPath, null);
        }

        try {
            // 1. Create backup
            Path backupPath = root.resolve("Packages").resolve("manifest.json.bak." + System.currentTimeMillis());
            Files.copy(manifestPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Created manifest backup at {}", backupPath);

            // 2. Read and update manifest
            JsonNode rootNode = objectMapper.readTree(manifestPath.toFile());
            if (!rootNode.isObject()) {
                return new LifecycleResult(false, "Invalid manifest.json: root is not a JSON object", backupPath.toString());
            }

            ObjectNode objRoot = (ObjectNode) rootNode;
            ObjectNode depsNode;
            if (objRoot.has("dependencies") && objRoot.get("dependencies").isObject()) {
                depsNode = (ObjectNode) objRoot.get("dependencies");
            } else {
                depsNode = objRoot.putObject("dependencies");
            }

            String source = (packageSource != null && !packageSource.isBlank())
                    ? packageSource
                    : "1.0.0";

            depsNode.put(PACKAGE_NAME, source);

            // 3. Write back
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), objRoot);
            log.info("Installed extension {} with source '{}' into {}", PACKAGE_NAME, source, manifestPath);

            return new LifecycleResult(true, "Extension successfully installed into " + projectPath, backupPath.toString());
        } catch (Exception e) {
            log.error("Failed to install extension: {}", e.getMessage(), e);
            return new LifecycleResult(false, "Installation failed: " + e.getMessage(), null);
        }
    }

    /**
     * Safely uninstalls the Autonomous Agent extension package from the target Unity project.
     * Leaves user Assets, scenes, and custom packages completely untouched.
     */
    public LifecycleResult uninstallExtension(String projectPath, boolean removeIdentity) {
        if (projectPath == null || projectPath.isBlank()) {
            return new LifecycleResult(false, "Project path cannot be null or empty", null);
        }

        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        Path manifestPath = root.resolve("Packages").resolve("manifest.json");

        if (!Files.exists(manifestPath)) {
            return new LifecycleResult(false, "Packages/manifest.json not found in " + projectPath, null);
        }

        try {
            // 1. Create backup
            Path backupPath = root.resolve("Packages").resolve("manifest.json.bak." + System.currentTimeMillis());
            Files.copy(manifestPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

            // 2. Read and remove from manifest
            JsonNode rootNode = objectMapper.readTree(manifestPath.toFile());
            if (rootNode.isObject()) {
                ObjectNode objRoot = (ObjectNode) rootNode;
                if (objRoot.has("dependencies") && objRoot.get("dependencies").isObject()) {
                    ObjectNode depsNode = (ObjectNode) objRoot.get("dependencies");
                    depsNode.remove(PACKAGE_NAME);
                }
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), objRoot);
            }

            // 3. Optionally remove AutonomousAgentIdentity.json
            if (removeIdentity) {
                Path identityFile = root.resolve("ProjectSettings").resolve("AutonomousAgentIdentity.json");
                if (Files.exists(identityFile)) {
                    Files.delete(identityFile);
                    log.info("Removed AutonomousAgentIdentity.json from {}", identityFile);
                }
            }

            log.info("Safely uninstalled extension from {}", projectPath);
            return new LifecycleResult(true, "Extension successfully uninstalled", backupPath.toString());
        } catch (Exception e) {
            log.error("Failed to uninstall extension: {}", e.getMessage(), e);
            return new LifecycleResult(false, "Uninstallation failed: " + e.getMessage(), null);
        }
    }
}
