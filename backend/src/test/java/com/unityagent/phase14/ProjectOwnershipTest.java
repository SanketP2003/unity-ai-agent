package com.unityagent.phase14;

import com.unityagent.product.service.FileOwnershipTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectOwnershipTest {

    private FileOwnershipTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new FileOwnershipTracker();
    }

    @Test
    void testFileClassifications() {
        // Forbidden Studio Infrastructure files
        assertEquals(FileOwnershipTracker.Ownership.STUDIO_OWNED,
                tracker.classifyFile("memory.db", "C:/Projects/Game"));
        assertEquals(FileOwnershipTracker.Ownership.STUDIO_OWNED,
                tracker.classifyFile("backend.sqlite", "C:/Projects/Game"));
        assertEquals(FileOwnershipTracker.Ownership.STUDIO_OWNED,
                tracker.classifyFile("autonomous-unity-agent-0.1.0.jar", "C:/Projects/Game"));
        assertEquals(FileOwnershipTracker.Ownership.STUDIO_OWNED,
                tracker.classifyFile("credentials.json", "C:/Projects/Game"));
        assertEquals(FileOwnershipTracker.Ownership.STUDIO_OWNED,
                tracker.classifyFile(".env", "C:/Projects/Game"));
        assertEquals(FileOwnershipTracker.Ownership.STUDIO_OWNED,
                tracker.classifyFile("llm_cache/response_123.json", "C:/Projects/Game"));
        assertEquals(FileOwnershipTracker.Ownership.STUDIO_OWNED,
                tracker.classifyFile(".unityagent/db.sqlite", "C:/Projects/Game"));

        // Legitimate AI-Generated / Game Assets
        assertEquals(FileOwnershipTracker.Ownership.LEGITIMATE_GAME_CONTENT,
                tracker.classifyFile("Assets/Scripts/Player.cs", "C:/Projects/Game"));
        assertEquals(FileOwnershipTracker.Ownership.LEGITIMATE_GAME_CONTENT,
                tracker.classifyFile("Assets/Scenes/Main.unity", "C:/Projects/Game"));
        assertEquals(FileOwnershipTracker.Ownership.LEGITIMATE_GAME_CONTENT,
                tracker.classifyFile("Assets/Prefabs/Enemy.prefab", "C:/Projects/Game"));
        assertEquals(FileOwnershipTracker.Ownership.LEGITIMATE_GAME_CONTENT,
                tracker.classifyFile("Assets/Materials/Floor.mat", "C:/Projects/Game"));

        // User / System Owned files
        assertEquals(FileOwnershipTracker.Ownership.USER_OWNED,
                tracker.classifyFile("Packages/manifest.json", "C:/Projects/Game"));
        assertEquals(FileOwnershipTracker.Ownership.USER_OWNED,
                tracker.classifyFile("ProjectSettings/ProjectVersion.txt", "C:/Projects/Game"));
    }
}
