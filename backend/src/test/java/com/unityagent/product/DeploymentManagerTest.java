package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.*;
import com.unityagent.product.service.ArtifactManager;
import com.unityagent.product.service.DeploymentManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DeploymentManagerTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private ArtifactManager artifactManager;
    private DeploymentManager deploymentManager;
    private Path projectRoot;
    private Path deployTarget;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("deploy_test_" + UUID.randomUUID() + ".db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        artifactManager = Mockito.mock(ArtifactManager.class);
        deploymentManager = new DeploymentManager(db, artifactManager);

        projectRoot = tempDir.resolve("project_root");
        deployTarget = tempDir.resolve("deploy_staging");
        Files.createDirectories(projectRoot.resolve("Builds/Windows"));
        Files.writeString(projectRoot.resolve("Builds/Windows/Game.exe"), "BINARY_GAME_PAYLOAD");

        // SSOT project
        repository.upsertProject(new ProjectMemory("proj-dep-1", "Deploy Game", "6000.4.7f1", "WINDOWS", "URP", "1.0.0", "fp-dep-1"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testDeployReleaseSucceedsOnPublishedRelease() throws Exception {
        Release release = new Release("rel-dep-1", "proj-dep-1", "1.0.0", ReleaseChannel.STABLE, ReleaseStatus.PUBLISHED, "b1", "V1.0", Instant.now());
        try (var conn = db.getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO game_releases (release_id, project_id, version_string, channel, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, release.getReleaseId());
            ps.setString(2, release.getProjectId());
            ps.setString(3, release.getVersionString());
            ps.setString(4, release.getChannel().name());
            ps.setString(5, release.getStatus().name());
            ps.setString(6, release.getCreatedAt().toString());
            ps.executeUpdate();
        }

        BuildArtifact artifact = new BuildArtifact("art-1", "proj-dep-1", "rel-dep-1", "WINDOWS", "x64", "Builds/Windows/Game.exe", 100L, "sha", ArtifactStatus.VERIFIED, Instant.now());

        when(artifactManager.verifyArtifactIntegrity(eq("art-1"), eq(projectRoot))).thenReturn(true);

        DeploymentRecord record = deploymentManager.deployRelease(
                "dep-1", release, artifact, "Steam Staging", "LOCAL_FOLDER", projectRoot, deployTarget
        );

        assertNotNull(record);
        assertEquals(DeploymentStatus.SUCCEEDED, record.getStatus());
        assertTrue(Files.exists(deployTarget.resolve("Game.exe")));

        List<DeploymentRecord> list = deploymentManager.listDeployments("proj-dep-1");
        assertEquals(1, list.size());
        assertEquals("dep-1", list.get(0).getDeploymentId());
    }

    @Test
    void testDeployRejectedWhenReleaseNotPublished() {
        // DRAFT release
        Release release = new Release("rel-draft", "proj-dep-1", "1.0.0", ReleaseChannel.STABLE, ReleaseStatus.DRAFT, "b1", "V1.0", Instant.now());
        BuildArtifact artifact = new BuildArtifact("art-1", "proj-dep-1", "rel-draft", "WINDOWS", "x64", "Builds/Windows/Game.exe", 100L, "sha", ArtifactStatus.VERIFIED, Instant.now());

        assertThrows(IllegalStateException.class, () -> {
            deploymentManager.deployRelease("dep-2", release, artifact, "Local", "LOCAL_FOLDER", projectRoot, deployTarget);
        }, "Must reject deployment of unpublished release");
    }

    @Test
    void testDeployRejectedWhenArtifactCorrupted() {
        Release release = new Release("rel-dep-2", "proj-dep-1", "1.0.0", ReleaseChannel.STABLE, ReleaseStatus.PUBLISHED, "b1", "V1.0", Instant.now());
        BuildArtifact artifact = new BuildArtifact("art-1", "proj-dep-1", "rel-dep-2", "WINDOWS", "x64", "Builds/Windows/Game.exe", 100L, "sha", ArtifactStatus.VERIFIED, Instant.now());

        // Artifact verification fails
        when(artifactManager.verifyArtifactIntegrity(eq("art-1"), eq(projectRoot))).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> {
            deploymentManager.deployRelease("dep-3", release, artifact, "Local", "LOCAL_FOLDER", projectRoot, deployTarget);
        }, "Must reject deployment if artifact is corrupted");
    }
}
