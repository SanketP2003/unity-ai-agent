package com.unityagent.phase14;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.BuildArtifact;
import com.unityagent.product.model.Release;
import com.unityagent.product.model.ReleaseChannel;
import com.unityagent.product.service.ArtifactManager;
import com.unityagent.product.service.ReleaseManager;
import com.unityagent.product.service.VersionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CrossProjectReleaseSecurityTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private ReleaseManager releaseManager;
    private ArtifactManager artifactManager;
    private VersionManager versionManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("phase14_rel_sec.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        com.unityagent.product.service.ReleaseValidator releaseValidator = org.mockito.Mockito.mock(com.unityagent.product.service.ReleaseValidator.class);
        artifactManager = new ArtifactManager(db);
        versionManager = new VersionManager(db);
        releaseManager = new ReleaseManager(db, releaseValidator, versionManager, artifactManager);

        com.unityagent.memory.SQLiteMemoryRepository repo = new com.unityagent.memory.SQLiteMemoryRepository(db);
        repo.upsertProject(new com.unityagent.memory.model.ProjectMemory("proj_A", "Proj A", "6000.0.0f1", "Windows", "URP", "1.0.0", "fp_a"));
        repo.upsertProject(new com.unityagent.memory.model.ProjectMemory("proj_B", "Proj B", "6000.0.0f1", "Windows", "URP", "1.0.0", "fp_b"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testCrossProjectReleaseOperationsRejected() {
        Release relA = releaseManager.createReleaseCandidate(
                "rel_A",
                "proj_A",
                "1.0.0",
                ReleaseChannel.STABLE,
                "build_001",
                "Initial release"
        );
        assertNotNull(relA);

        // Project B attempting to publish Project A's release
        BuildArtifact mockArtifact = new BuildArtifact();
        assertThrows(SecurityException.class, () -> {
            releaseManager.publishRelease(relA.getReleaseId(), "proj_B", mockArtifact, tempDir);
        });

        // Project B attempting to rollback Project A's release
        assertThrows(SecurityException.class, () -> {
            releaseManager.rollbackRelease(relA.getReleaseId(), "proj_B");
        });

        // Project B attempting to archive Project A's release
        assertThrows(SecurityException.class, () -> {
            releaseManager.archiveRelease(relA.getReleaseId(), "proj_B");
        });
    }
}
