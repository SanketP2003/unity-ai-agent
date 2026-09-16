package com.unityagent.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Authoritative tribunal gate evaluating repository cleanliness, zero-secret policy,
 * absence of temporary files, and lack of hardcoded developer machine paths.
 */
@Service
public class RepositoryCleanlinessGate {

    private static final Logger log = LoggerFactory.getLogger(RepositoryCleanlinessGate.class);

    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(nvapi-[a-z0-9_-]{20,}|sk-[a-z0-9]{20,}|bearer\\s+[a-z0-9_\\-\\.]{20,})");
    private static final Pattern HARDCODED_PATH_PATTERN = Pattern.compile("(?i)(c:\\\\users\\\\|/users/|onedrive|desktop\\\\)");

    private static final Set<String> TEMPORARY_EXTS = Set.of(
            ".tmp", ".temp", ".bak", ".old", ".orig", ".swp", ".swo", "~"
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
     * Evaluates the entire repository for hygiene, secrets, temporary files, and machine paths.
     */
    public GateResult evaluate(Path repoRoot) {
        if (repoRoot == null || !Files.exists(repoRoot)) {
            return GateResult.failure(List.of("Repository root does not exist: " + repoRoot));
        }

        List<String> violations = new ArrayList<>();
        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();

        // 1. Check for rogue empty root directories
        Path rogueAssets = normalizedRoot.resolve("Assets");
        if (Files.exists(rogueAssets) && Files.isDirectory(rogueAssets)) {
            try (var stream = Files.list(rogueAssets)) {
                if (stream.findAny().isEmpty()) {
                    violations.add("Rogue empty 'Assets' directory found at repository root.");
                }
            } catch (IOException ignored) {}
        }

        // 2. Scan files
        try (Stream<Path> stream = Files.walk(normalizedRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String relPath = normalizedRoot.relativize(path).toString().replace('\\', '/');

                // Skip .git directory, build targets, and ignored cache/transient directories
                if (relPath.startsWith(".git/") || relPath.startsWith("backend/target/")
                        || relPath.contains("/Library/") || relPath.contains("/Temp/") || relPath.contains("/Logs/")
                        || relPath.contains("/UserSettings/") || relPath.contains("/Obj/") || relPath.startsWith(".idea/")
                        || relPath.startsWith(".unityagent/") || relPath.startsWith("quarantine/")) {
                    return;
                }

                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

                // A. Temporary files
                for (String ext : TEMPORARY_EXTS) {
                    if (fileName.endsWith(ext)) {
                        violations.add("Prohibited temporary file present: " + relPath);
                    }
                }
                if (fileName.equals(".ds_store") || fileName.equals("thumbs.db")) {
                    violations.add("Prohibited OS metadata file present: " + relPath);
                }

                // B. Runtime databases in git repository root or unity
                if (fileName.endsWith(".db") || fileName.endsWith(".sqlite") || fileName.endsWith(".db-wal")) {
                    if (!relPath.contains("/test/") && !relPath.contains("src/test/")) {
                        violations.add("Prohibited runtime database file found in repo: " + relPath);
                    }
                }

                // C. Scan production code for hardcoded developer paths
                if ((relPath.startsWith("backend/src/main/") || relPath.startsWith("unity-agent-extension/"))
                        && (fileName.endsWith(".java") || fileName.endsWith(".cs") || fileName.endsWith(".yml"))) {
                    if (relPath.endsWith("RepositoryCleanlinessGate.java") || relPath.endsWith("ProductInstallationService.java")) {
                        return;
                    }
                    try {
                        String content = Files.readString(path);
                        if (HARDCODED_PATH_PATTERN.matcher(content).find()) {
                            violations.add("Hardcoded developer machine path found in production file: " + relPath);
                        }
                    } catch (IOException e) {
                        log.warn("Failed reading {}: {}", relPath, e.getMessage());
                    }
                }

                // D. Scan for actual plaintext secrets in non-test source and config
                if (!relPath.contains("/test/") && !relPath.contains("src/test/") && !relPath.endsWith(".md")) {
                    if (fileName.endsWith(".java") || fileName.endsWith(".cs") || fileName.endsWith(".json")
                            || fileName.endsWith(".yml") || fileName.endsWith(".properties")) {
                        try {
                            String content = Files.readString(path);
                            var matcher = SECRET_PATTERN.matcher(content);
                            if (matcher.find()) {
                                String matched = matcher.group();
                                if (!matched.contains("SCRUBBED") && !matched.contains("fake") && !matched.contains("dummy")) {
                                    violations.add("Plaintext credential detected in: " + relPath);
                                }
                            }
                        } catch (IOException e) {
                            log.warn("Failed reading {}: {}", relPath, e.getMessage());
                        }
                    }
                }
            });
        } catch (IOException e) {
            violations.add("Failed to walk repository tree: " + e.getMessage());
        }

        if (violations.isEmpty()) {
            log.info("RepositoryCleanlinessGate: PASSED for {}", repoRoot);
            return GateResult.success();
        } else {
            log.warn("RepositoryCleanlinessGate: FAILED with {} violation(s)", violations.size());
            return GateResult.failure(violations);
        }
    }
}
