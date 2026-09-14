package com.unityagent.phase14;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.BuildArtifact;
import com.unityagent.product.service.ArtifactManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CrossProjectArtifactSecurityTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private ArtifactManager artifactManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("phase14_art_sec.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        artifactManager = new ArtifactManager(db);
        com.unityagent.memory.SQLiteMemoryRepository repo = new com.unityagent.memory.SQLiteMemoryRepository(db);
        repo.upsertProject(new com.unityagent.memory.model.ProjectMemory("proj_A", "Proj A", "6000.0.0f1", "Windows", "URP", "1.0.0", "fp_a"));
        repo.upsertProject(new com.unityagent.memory.model.ProjectMemory("proj_B", "Proj B", "6000.0.0f1", "Windows", "URP", "1.0.0", "fp_b"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testCrossProjectArtifactAccessIsDenied() throws Exception {
        Path pDirA = tempDir.resolve("ProjA");
        Files.createDirectories(pDirA.resolve("Builds"));
        Path artifactFile = pDirA.resolve("Builds").resolve("game.exe");
        Files.writeString(artifactFile, "FAKE_EXE_BINARY_DATA");

        BuildArtifact artA = artifactManager.registerArtifact(
                "art_A",
                "proj_A",
                "rel_A",
                "StandaloneWindows64",
                "x86_64",
                pDirA,
                "Builds/game.exe"
        );

        assertNotNull(artA);

        // Project A can access its own artifact
        BuildArtifact fetchedA = artifactManager.getArtifactWithTenantCheck(artA.getArtifactId(), "proj_A");
        assertEquals(artA.getArtifactId(), fetchedA.getArtifactId());

        // Project B attempting to access Project A's artifact throws SecurityException
        SecurityException ex = assertThrows(SecurityException.class, () -> {
            artifactManager.getArtifactWithTenantCheck(artA.getArtifactId(), "proj_B");
        });
        assertTrue(ex.getMessage().contains("ACCESS_DENIED"));
    }
}
