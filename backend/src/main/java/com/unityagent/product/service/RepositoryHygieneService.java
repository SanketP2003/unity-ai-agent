package com.unityagent.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service for repository hygiene scanning, dry-run cleanup planning, and execution.
 * Classifies files and produces zero-secret cleanup-report.json.
 */
@Service
public class RepositoryHygieneService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryHygieneService.class);

    private final PathSafetyValidator pathSafetyValidator;
    private final CleanupQuarantineService quarantineService;
    private final ObjectMapper mapper;

    private static final Set<String> TEMPORARY_EXTENSIONS = Set.of(
            ".tmp", ".temp", ".bak", ".old", ".orig", ".swp", ".swo", "~"
    );

    private static final Set<String> TEMPORARY_FILENAMES = Set.of(
            ".ds_store", "thumbs.db"
    );

    private static final Set<String> FORBIDDEN_IN_UNITY_EXTENSIONS = Set.of(
            ".jar", ".db", ".sqlite", ".sqlite3", ".db-wal", ".db-shm"
    );

    public RepositoryHygieneService(PathSafetyValidator pathSafetyValidator,
                                   CleanupQuarantineService quarantineService) {
        this.pathSafetyValidator = pathSafetyValidator;
        this.quarantineService = quarantineService;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public enum FileAction {
        WOULD_DELETE,
        WOULD_QUARANTINE,
        WOULD_PRESERVE,
        PROTECTED,
        UNKNOWN
    }

    public static class FileItem {
        public String path;
        public FileAction action;
        public String reason;
        public long sizeBytes;

        public FileItem() {}

        public FileItem(String path, FileAction action, String reason, long sizeBytes) {
            this.path = path;
            this.action = action;
            this.reason = reason;
            this.sizeBytes = sizeBytes;
        }
    }

    public static class CleanupReport {
        public String operationId;
        public String timestamp;
        public String scannedRoot;
        public boolean dryRun;
        public int totalScanned;
        public int preservedCount;
        public int deletedCount;
        public int quarantinedCount;
        public int protectedCount;
        public int unknownCount;
        public List<FileItem> items = new ArrayList<>();
        public List<String> emptyDirectoriesRemoved = new ArrayList<>();
        public List<String> securityFindings = new ArrayList<>();

        public CleanupReport() {}
    }

    /**
     * Performs a dry-run cleanup scan without deleting or modifying any file.
     */
    public CleanupReport performDryRun(Path rootDir) {
        return scanAndProcess(rootDir, true);
    }

    /**
     * Executes safe cleanup with quarantine on uncertain studio-owned files.
     */
    public CleanupReport executeCleanup(Path rootDir) {
        return scanAndProcess(rootDir, false);
    }

    private CleanupReport scanAndProcess(Path rootDir, boolean dryRun) {
        if (rootDir == null || !Files.exists(rootDir)) {
            throw new IllegalArgumentException("Root directory does not exist: " + rootDir);
        }

        Path normalizedRoot = rootDir.toAbsolutePath().normalize();
        CleanupReport report = new CleanupReport();
        report.operationId = "clean_" + UUID.randomUUID().toString().substring(0, 8);
        report.timestamp = Instant.now().toString();
        report.scannedRoot = normalizedRoot.toString().replace('\\', '/');
        report.dryRun = dryRun;

        try (Stream<Path> stream = Files.walk(normalizedRoot)) {
            List<Path> allPaths = stream.toList();

            for (Path path : allPaths) {
                if (Files.isRegularFile(path)) {
                    report.totalScanned++;
                    FileItem item = classifyFile(normalizedRoot, path);
                    report.items.add(item);

                    switch (item.action) {
                        case PROTECTED -> report.protectedCount++;
                        case WOULD_PRESERVE -> report.preservedCount++;
                        case WOULD_DELETE -> {
                            report.deletedCount++;
                            if (!dryRun) {
                                try {
                                    Files.deleteIfExists(path);
                                    log.info("Deleted temporary file: {}", path);
                                } catch (IOException e) {
                                    log.error("Failed to delete {}: {}", path, e.getMessage());
                                }
                            }
                        }
                        case WOULD_QUARANTINE -> {
                            report.quarantinedCount++;
                            if (!dryRun) {
                                try {
                                    quarantineService.quarantineFile(path, "STUDIO", item.reason, report.operationId);
                                } catch (IOException e) {
                                    log.error("Failed to quarantine {}: {}", path, e.getMessage());
                                }
                            }
                        }
                        case UNKNOWN -> report.unknownCount++;
                    }
                }
            }

            // Empty directory cleanup (bottom up)
            if (!dryRun) {
                List<Path> dirs = allPaths.stream()
                        .filter(Files::isDirectory)
                        .filter(p -> !p.equals(normalizedRoot))
                        .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                        .toList();

                for (Path dir : dirs) {
                    if (isRogueEmptyStudioDirectory(normalizedRoot, dir)) {
                        try {
                            Files.delete(dir);
                            report.emptyDirectoriesRemoved.add(normalizedRoot.relativize(dir).toString().replace('\\', '/'));
                            log.info("Removed rogue empty directory: {}", dir);
                        } catch (Exception e) {
                            log.warn("Could not delete empty dir {}: {}", dir, e.getMessage());
                        }
                    }
                }
            }

        } catch (IOException e) {
            log.error("Error during hygiene scanning: {}", e.getMessage());
            report.securityFindings.add("Scan error: " + e.getMessage());
        }

        return report;
    }

    private FileItem classifyFile(Path rootDir, Path file) {
        String relativePath = rootDir.relativize(file).toString().replace('\\', '/');
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        long size = 0;
        try {
            size = Files.size(file);
        } catch (Exception ignored) {}

        // 1. Protected root metadata and intentional files
        if (isProtectedFile(relativePath, fileName)) {
            return new FileItem(relativePath, FileAction.PROTECTED, "Critical core product/project file", size);
        }

        // 2. Confirmed temporary development files
        for (String ext : TEMPORARY_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return new FileItem(relativePath, FileAction.WOULD_DELETE, "Temporary file pattern (" + ext + ")", size);
            }
        }
        if (TEMPORARY_FILENAMES.contains(fileName)) {
            return new FileItem(relativePath, FileAction.WOULD_DELETE, "OS metadata artifact", size);
        }

        // 3. Unity project inspection
        if (relativePath.startsWith("unity/") || relativePath.contains("/Assets/")) {
            for (String forbExt : FORBIDDEN_IN_UNITY_EXTENSIONS) {
                if (fileName.endsWith(forbExt)) {
                    return new FileItem(relativePath, FileAction.WOULD_QUARANTINE, "Forbidden studio infrastructure in Unity project (" + forbExt + ")", size);
                }
            }
            if (fileName.equals(".env") || fileName.startsWith(".env.")) {
                return new FileItem(relativePath, FileAction.WOULD_QUARANTINE, "Forbidden environment credential file in Unity project", size);
            }
        }

        // 4. Source & test files
        if (relativePath.startsWith("backend/src/") || relativePath.startsWith("docs/")
                || relativePath.startsWith("unity-agent-extension/")
                || relativePath.startsWith("distribution/")
                || relativePath.startsWith("acceptance/")) {
            return new FileItem(relativePath, FileAction.WOULD_PRESERVE, "Legitimate source/test/documentation", size);
        }

        // 5. Build outputs in target/ (if not ignored)
        if (relativePath.startsWith("backend/target/")) {
            return new FileItem(relativePath, FileAction.WOULD_DELETE, "Temporary Maven build output", size);
        }

        // 6. Root temporary test scripts
        if (relativePath.equals("install_clean_test.ps1") || relativePath.equals("run_request.json")) {
            return new FileItem(relativePath, FileAction.WOULD_DELETE, "Temporary root test script", size);
        }

        return new FileItem(relativePath, FileAction.WOULD_PRESERVE, "Preserved project asset", size);
    }

    private boolean isProtectedFile(String relativePath, String fileName) {
        return relativePath.equals("pom.xml") ||
                relativePath.equals(".gitignore") ||
                relativePath.equals("README.md") ||
                relativePath.endsWith("/package.json") ||
                relativePath.endsWith("/manifest.json") ||
                relativePath.endsWith("/ProjectVersion.txt");
    }

    private boolean isRogueEmptyStudioDirectory(Path rootDir, Path dir) {
        try (var stream = Files.list(dir)) {
            if (stream.findAny().isPresent()) {
                return false; // not empty
            }
        } catch (IOException e) {
            return false;
        }

        String rel = rootDir.relativize(dir).toString().replace('\\', '/');
        // If it's an empty "Assets" at the repo root (not inside unity/Assets)
        if (rel.equals("Assets") || rel.startsWith("Assets/Temp") || rel.startsWith("Assets/Generated")) {
            return true;
        }
        return false;
    }
}
