package com.unityagent.product.service;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

/**
 * Classifies file ownership to ensure clean separation between:
 * 1. The user's game (Assets, Scenes, AI-generated game code)
 * 2. Autonomous Game Studio infrastructure (JARs, DBs, logs, credentials)
 */
@Service
public class FileOwnershipTracker {

    public enum Ownership {
        STUDIO_OWNED,             // Studio infrastructure files (must NEVER be in user Unity projects)
        LEGITIMATE_GAME_CONTENT,  // AI-generated game assets (scripts, scenes, prefabs)
        USER_OWNED                // User's own game assets, settings, manifest
    }

    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            ".db", ".sqlite", ".sqlite3", ".jar", ".war", ".class"
    );

    private static final Set<String> FORBIDDEN_DIR_NAMES = Set.of(
            ".unityagent", "llm_cache", "target", "backend", "credentials"
    );

    /**
     * Classifies a file path relative to the project root.
     */
    public Ownership classifyFile(String relativeOrAbsolutePath, String projectRootPath) {
        if (relativeOrAbsolutePath == null) return Ownership.USER_OWNED;

        String pathStr = relativeOrAbsolutePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        String fileName = Paths.get(pathStr).getFileName().toString();

        // Check for forbidden Studio files
        for (String ext : FORBIDDEN_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return Ownership.STUDIO_OWNED;
            }
        }

        if (fileName.equals("credentials.json") || fileName.equals(".env") ||
            fileName.startsWith("studio_") && fileName.endsWith(".log") ||
            fileName.equals("memory.db") || fileName.equals("memory.db-wal") ||
            fileName.equals("memory.db-shm")) {
            return Ownership.STUDIO_OWNED;
        }

        for (String forbiddenDir : FORBIDDEN_DIR_NAMES) {
            if (pathStr.contains("/" + forbiddenDir + "/") || pathStr.startsWith(forbiddenDir + "/") || pathStr.endsWith("/" + forbiddenDir)) {
                return Ownership.STUDIO_OWNED;
            }
        }

        // Legitimate game content created in Assets/
        if (pathStr.contains("assets/") || pathStr.startsWith("assets/")) {
            if (pathStr.endsWith(".cs") || pathStr.endsWith(".unity") ||
                pathStr.endsWith(".prefab") || pathStr.endsWith(".mat") ||
                pathStr.endsWith(".anim") || pathStr.endsWith(".asset") ||
                pathStr.endsWith(".meta") || pathStr.endsWith(".png") ||
                pathStr.endsWith(".wav") || pathStr.endsWith(".mp3")) {
                return Ownership.LEGITIMATE_GAME_CONTENT;
            }
        }

        return Ownership.USER_OWNED;
    }

    public boolean isForbiddenInUnityProject(String filePath) {
        return classifyFile(filePath, "") == Ownership.STUDIO_OWNED;
    }
}
