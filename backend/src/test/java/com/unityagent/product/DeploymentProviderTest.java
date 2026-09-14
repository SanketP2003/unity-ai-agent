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
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("Deployment Provider Adapter Tests")
class DeploymentProviderTest {

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
        File dbFile = tempDir.resolve("deploy_prov_test_" + UUID.randomUUID() + ".db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        artifactManager = Mockito.mock(ArtifactManager.class);
        deploymentManager = new DeploymentManager(db, artifactManager);

        projectRoot = tempDir.resolve("project_root");
        deployTarget = tempDir.resolve("deploy_target");
        Files.createDirectories(projectRoot.resolve("Builds/WebGL"));
        Files.writeString(projectRoot.resolve("Builds/WebGL/index.html"), "<html><body>WebGL Game</body></html>");

        repository.upsertProject(new ProjectMemory("proj-prov-1", "Provider Game", "6000.4.7f1", "WEBGL", "URP", "1.0.0", "fp-prov-1"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    @DisplayName("Verify WebGL staging deployment adapter execution")
    void testWebGLStagingDeployment() throws Exception {
        Release release = new Release("rel-webgl-1", "proj-prov-1", "1.0.0", ReleaseChannel.STABLE, ReleaseStatus.PUBLISHED, "b1", "V1.0 WebGL", Instant.now());
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

        BuildArtifact artifact = new BuildArtifact("art-webgl-1", "proj-prov-1", "rel-webgl-1", "WEBGL", "Wasm", "Builds/WebGL/index.html", 45L, "sha256", ArtifactStatus.VERIFIED, Instant.now());
        when(artifactManager.verifyArtifactIntegrity(eq("art-webgl-1"), eq(projectRoot))).thenReturn(true);

        DeploymentRecord record = deploymentManager.deployRelease(
                "dep-webgl-1", release, artifact, "WebGL Staging Server", "WEBGL_STAGING", projectRoot, deployTarget
        );

        assertNotNull(record);
        assertEquals(DeploymentStatus.SUCCEEDED, record.getStatus());
        assertEquals("WebGL Staging Server", record.getTargetName());
        assertTrue(Files.exists(deployTarget.resolve("index.html")));

        List<DeploymentRecord> records = deploymentManager.listDeployments("proj-prov-1");
        assertFalse(records.isEmpty());
        assertEquals("dep-webgl-1", records.get(0).getDeploymentId());
    }
}
