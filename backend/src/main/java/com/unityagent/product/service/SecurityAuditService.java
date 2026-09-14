package com.unityagent.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service performing deep security auditing across the application workspace, database,
 * diagnostics, and exports to enforce the Zero-Secret Invariant.
 */
@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)sk-[A-Za-z0-9_-]{20,}"),
            Pattern.compile("(?i)nvapi-[A-Za-z0-9_-]{20,}"),
            Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9_\\-\\.]+"),
            Pattern.compile("-----BEGIN (?:RSA )?PRIVATE KEY-----"),
            Pattern.compile("(?i)(?:api[_-]?key|secret[_-]?key|auth[_-]?token)[\"']?\\s*[:=]\\s*[\"']([A-Za-z0-9_\\-]{16,})[\"']")
    );

    public record AuditReport(
            boolean clean,
            int scannedFiles,
            List<String> violations,
            Map<String, Integer> categoryStats,
            long durationMs
    ) {}

    /**
     * Performs security scan across specified paths (workspace, config, db, logs).
     */
    public AuditReport auditPaths(List<Path> targetPaths) {
        long startTime = System.currentTimeMillis();
        int[] fileCount = new int[]{0};
        List<String> violations = new ArrayList<>();
        Map<String, Integer> categoryStats = new LinkedHashMap<>();

        categoryStats.put("SCANNED_FILES", 0);
        categoryStats.put("CONFIG_FILES", 0);
        categoryStats.put("CODE_FILES", 0);
        categoryStats.put("DATABASE_FILES", 0);
        categoryStats.put("LOG_FILES", 0);

        for (Path root : targetPaths) {
            if (root == null || !Files.exists(root)) continue;

            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        fileCount[0]++;
                        String name = file.getFileName().toString().toLowerCase();

                        // Categorize
                        if (name.endsWith(".json") || name.endsWith(".properties") || name.endsWith(".yaml") || name.endsWith(".yml")) {
                            categoryStats.put("CONFIG_FILES", categoryStats.get("CONFIG_FILES") + 1);
                            scanTextFile(file, violations);
                        } else if (name.endsWith(".cs") || name.endsWith(".java") || name.endsWith(".js")) {
                            categoryStats.put("CODE_FILES", categoryStats.get("CODE_FILES") + 1);
                            scanTextFile(file, violations);
                        } else if (name.endsWith(".log") || name.endsWith(".txt")) {
                            categoryStats.put("LOG_FILES", categoryStats.get("LOG_FILES") + 1);
                            scanTextFile(file, violations);
                        } else if (name.endsWith(".db") || name.endsWith(".sqlite")) {
                            categoryStats.put("DATABASE_FILES", categoryStats.get("DATABASE_FILES") + 1);
                            scanBinaryFile(file, violations);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        log.warn("Failed to audit file {}: {}", file, exc.getMessage());
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (Exception e) {
                log.error("Failed during audit walk on {}: {}", root, e.getMessage());
            }
        }

        categoryStats.put("SCANNED_FILES", fileCount[0]);
        long duration = System.currentTimeMillis() - startTime;
        boolean clean = violations.isEmpty();

        return new AuditReport(clean, fileCount[0], violations, categoryStats, duration);
    }

    private void scanTextFile(Path file, List<String> violations) {
        try {
            // Limit text scan to 10MB to avoid OOM
            if (Files.size(file) > 10 * 1024 * 1024) return;
            String content = Files.readString(file);
            for (Pattern pattern : SECRET_PATTERNS) {
                var matcher = pattern.matcher(content);
                if (matcher.find()) {
                    violations.add("Plaintext secret detected in " + file.getFileName() + " [Rule: " + pattern.pattern() + "]");
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Could not read text file {}: {}", file, e.getMessage());
        }
    }

    private void scanBinaryFile(Path file, List<String> violations) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String raw = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            for (Pattern pattern : SECRET_PATTERNS) {
                var matcher = pattern.matcher(raw);
                if (matcher.find()) {
                    violations.add("Plaintext secret detected in binary/sqlite file " + file.getFileName());
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Could not read binary file {}: {}", file, e.getMessage());
        }
    }
}
