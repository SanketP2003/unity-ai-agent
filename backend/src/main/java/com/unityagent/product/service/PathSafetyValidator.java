package com.unityagent.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for canonicalizing file paths and validating that target paths remain
 * strictly confined within the approved project or repository boundary.
 */
@Service
public class PathSafetyValidator {

    private static final Logger log = LoggerFactory.getLogger(PathSafetyValidator.class);

    /**
     * Validates that the given targetPath is safe and strictly contained within the baseDir.
     *
     * @param baseDir the root directory boundary (e.g. project root or repository root)
     * @param targetPath the path to test or resolve
     * @return the normalized, validated Path
     * @throws SecurityException if the path attempts traversal, escapes baseDir, or uses illegal constructs
     */
    public Path validateSafePath(Path baseDir, String targetPath) {
        if (baseDir == null) {
            throw new IllegalArgumentException("Base directory cannot be null");
        }
        if (targetPath == null || targetPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Target path cannot be null or blank");
        }

        // Check for raw traversal patterns
        if (targetPath.contains("..") || targetPath.contains("/../") || targetPath.contains("\\..\\")) {
            throw new SecurityException("Path traversal pattern '..' detected in: " + targetPath);
        }

        Path normalizedBase;
        try {
            normalizedBase = baseDir.toAbsolutePath().normalize();
            if (Files.exists(normalizedBase)) {
                normalizedBase = normalizedBase.toRealPath();
            }
        } catch (IOException e) {
            normalizedBase = baseDir.toAbsolutePath().normalize();
        }

        Path candidate;
        Path rawTarget = Paths.get(targetPath);
        if (rawTarget.isAbsolute()) {
            candidate = rawTarget.normalize();
        } else {
            candidate = normalizedBase.resolve(rawTarget).normalize();
        }

        // If file exists, resolve real path to defeat symlink / junction escapes
        if (Files.exists(candidate)) {
            try {
                Path realCandidate = candidate.toRealPath();
                if (!realCandidate.startsWith(normalizedBase)) {
                    throw new SecurityException("Symlink / junction escape detected: " + realCandidate + " is outside " + normalizedBase);
                }
                return realCandidate;
            } catch (IOException e) {
                log.warn("Could not determine real path for {}: {}", candidate, e.getMessage());
            }
        }

        if (!candidate.startsWith(normalizedBase)) {
            throw new SecurityException("Target path " + candidate + " escapes base boundary " + normalizedBase);
        }

        return candidate;
    }

    /**
     * Helper to check if a path is safe without throwing.
     */
    public boolean isSafe(Path baseDir, String targetPath) {
        try {
            validateSafePath(baseDir, targetPath);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
