package com.unityagent.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Service enforcing project hygiene by detecting and preventing Studio infrastructure
 * pollution (databases, JARs, logs, credentials) from contaminating user Unity projects.
 */
@Service
public class ProjectHygieneService {

    private static final Logger log = LoggerFactory.getLogger(ProjectHygieneService.class);
    private final FileOwnershipTracker ownershipTracker;

    public ProjectHygieneService(FileOwnershipTracker ownershipTracker) {
        this.ownershipTracker = ownershipTracker;
    }

    public static class HygieneReport {
        private final boolean clean;
        private final List<String> violations;
        private final List<String> offendingPaths;
        private final int totalFilesScanned;

        public HygieneReport(boolean clean, List<String> violations, List<String> offendingPaths, int totalFilesScanned) {
            this.clean = clean;
            this.violations = violations;
            this.offendingPaths = offendingPaths;
            this.totalFilesScanned = totalFilesScanned;
        }

        public boolean isClean() {
            return clean;
        }

        public List<String> getViolations() {
            return violations;
        }

        public List<String> getOffendingPaths() {
            return offendingPaths;
        }

        public int getTotalFilesScanned() {
            return totalFilesScanned;
        }
    }

    /**
     * Performs a comprehensive hygiene scan of the Unity project directory.
     */
    public HygieneReport performHygieneCheck(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return new HygieneReport(true, List.of(), List.of(), 0);
        }

        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return new HygieneReport(true, List.of(), List.of(), 0);
        }

        List<String> violations = new ArrayList<>();
        List<String> offendingPaths = new ArrayList<>();
        int[] count = new int[]{0};

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String rel = root.relativize(dir).toString().replace('\\', '/');
                    // Skip hidden git directories or Library
                    if (rel.startsWith(".git") || rel.startsWith("Library") || rel.startsWith("Temp") || rel.startsWith("obj")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (ownershipTracker.isForbiddenInUnityProject(rel)) {
                        violations.add("Forbidden Studio infrastructure directory found: " + rel);
                        offendingPaths.add(dir.toString().replace('\\', '/'));
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    count[0]++;
                    String rel = root.relativize(file).toString().replace('\\', '/');
                    if (ownershipTracker.isForbiddenInUnityProject(rel)) {
                        violations.add("Forbidden Studio infrastructure file found: " + rel);
                        offendingPaths.add(file.toString().replace('\\', '/'));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Error scanning project hygiene for {}: {}", projectPath, e.getMessage());
            violations.add("Scan error: " + e.getMessage());
        }

        boolean clean = violations.isEmpty();
        if (!clean) {
            log.warn("Hygiene violations detected in {}: {} offending items", projectPath, violations.size());
        }
        return new HygieneReport(clean, violations, offendingPaths, count[0]);
    }

    /**
     * Cleans detected pollution files from the Unity project without touching user game assets.
     */
    public int cleanPollution(String projectPath, boolean dryRun) {
        HygieneReport report = performHygieneCheck(projectPath);
        if (report.isClean()) return 0;

        int cleaned = 0;
        for (String pathStr : report.getOffendingPaths()) {
            Path p = Paths.get(pathStr);
            if (Files.exists(p)) {
                if (!dryRun) {
                    try {
                        if (Files.isDirectory(p)) {
                            deleteDirectoryRecursive(p);
                        } else {
                            Files.delete(p);
                        }
                        log.info("Cleaned forbidden Studio pollution: {}", p);
                    } catch (Exception e) {
                        log.error("Failed to delete pollution file {}: {}", p, e.getMessage());
                    }
                }
                cleaned++;
            }
        }
        return cleaned;
    }

    public void enforcePreRunHygiene(String projectPath) {
        HygieneReport report = performHygieneCheck(projectPath);
        if (!report.isClean()) {
            throw new IllegalStateException("PROJECT_HYGIENE_VIOLATION: Studio infrastructure files found in project: "
                    + String.join(", ", report.getViolations()));
        }
    }

    public void enforcePostRunHygiene(String projectPath) {
        HygieneReport report = performHygieneCheck(projectPath);
        if (!report.isClean()) {
            throw new IllegalStateException("PROJECT_HYGIENE_POST_RUN_VIOLATION: Autonomous run left Studio pollution in project: "
                    + String.join(", ", report.getViolations()));
        }
    }

    private void deleteDirectoryRecursive(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
