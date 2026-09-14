package com.unityagent.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Authoritative tribunal gate evaluating connected Unity projects for zero pollution,
 * valid Unity structure, and preservation of user assets.
 */
@Service
public class ProjectHygieneGate {

    private static final Logger log = LoggerFactory.getLogger(ProjectHygieneGate.class);

    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            ".jar", ".db", ".sqlite", ".sqlite3", ".db-wal", ".db-shm", ".log"
    );

    public record GateResult(boolean passed, List<String> violations) {
        public static GateResult success() {
            return new GateResult(true, List.of());
        }

        public static GateResult failure(List<String> violations) {
            return new GateResult(false, violations);
        }
    }

    /**
     * Evaluates the hygiene of a connected Unity project.
     */
    public GateResult evaluate(Path projectRoot) {
        if (projectRoot == null || !Files.exists(projectRoot)) {
            return GateResult.failure(List.of("Project root does not exist: " + projectRoot));
        }

        List<String> violations = new ArrayList<>();
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();

        // 1. Structure check: Assets and Packages must exist
        Path assetsDir = normalizedRoot.resolve("Assets");
        Path packagesDir = normalizedRoot.resolve("Packages");
        if (!Files.exists(assetsDir) || !Files.isDirectory(assetsDir)) {
            violations.add("Missing required Unity Assets directory: " + assetsDir);
        }
        if (!Files.exists(packagesDir) || !Files.isDirectory(packagesDir)) {
            violations.add("Missing required Unity Packages directory: " + packagesDir);
        }

        // 2. Scan for forbidden studio pollution
        try (Stream<Path> stream = Files.walk(normalizedRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String relPath = normalizedRoot.relativize(path).toString().replace('\\', '/');
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

                // Ignore standard Unity internal logs in unity/Logs/ directory if any
                if (relPath.startsWith("Logs/") || relPath.startsWith("unity/Logs/")) {
                    return;
                }

                for (String ext : FORBIDDEN_EXTENSIONS) {
                    if (fileName.endsWith(ext)) {
                        violations.add("Forbidden studio pollution file found in Unity project: " + relPath);
                    }
                }

                if (fileName.equals(".env") || fileName.startsWith(".env.")) {
                    violations.add("Forbidden credential file found in Unity project: " + relPath);
                }
            });
        } catch (IOException e) {
            violations.add("Error scanning project root: " + e.getMessage());
        }

        // 3. Verify manifest exists and is readable
        Path manifest = packagesDir.resolve("manifest.json");
        if (!Files.exists(manifest)) {
            violations.add("Missing Packages/manifest.json in Unity project.");
        }

        if (violations.isEmpty()) {
            log.info("ProjectHygieneGate: PASSED for {}", projectRoot);
            return GateResult.success();
        } else {
            log.warn("ProjectHygieneGate: FAILED with {} violation(s)", violations.size());
            return GateResult.failure(violations);
        }
    }
}
