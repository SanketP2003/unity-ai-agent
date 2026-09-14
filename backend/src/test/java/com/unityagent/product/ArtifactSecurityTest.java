package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.ArtifactStatus;
import com.unityagent.product.model.BuildArtifact;
import com.unityagent.product.service.ArtifactManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactSecurityTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private ArtifactManager artifactManager;
    private Path projectARoot;
    private Path projectBRoot;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("artifact_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        artifactManager = new ArtifactManager(db);

        projectARoot = tempDir.resolve("project_a");
        projectBRoot = tempDir.resolve("project_b");
        Files.createDirectories(projectARoot);
        Files.createDirectories(projectBRoot);

        // SSOT projects
        repository.upsertProject(new ProjectMemory("proj-A", "Project A", "6000.4.7f1", "WINDOWS", "URP", "1.0.0", "fp-A"));
        repository.upsertProject(new ProjectMemory("proj-B", "Project B", "6000.4.7f1", "WINDOWS", "URP", "1.0.0", "fp-B"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testPathTraversalRejection() {
        // Reject attempts to escape project root
        assertThrows(SecurityException.class, () -> {
            artifactManager.validateAndCanonicalizePath(projectARoot, "../project_b/Build/Game.exe");
        }, "Path with '..' escaping project root must be rejected");

        assertThrows(SecurityException.class, () -> {
            artifactManager.validateAndCanonicalizePath(projectARoot, "../../Windows/System32/cmd.exe");
        }, "Path with traversal must be rejected");

        assertThrows(SecurityException.class, () -> {
            artifactManager.validateAndCanonicalizePath(projectARoot, null);
        }, "Null path must be rejected");

        assertThrows(SecurityException.class, () -> {
            artifactManager.validateAndCanonicalizePath(projectARoot, "Build/Game\0.exe");
        }, "Path with null byte must be rejected");
    }

    @Test
    void testRegisterArtifactWithSha256Checksum() throws Exception {
        Path buildDir = projectARoot.resolve("Builds/Windows");
        Files.createDirectories(buildDir);
        Path gameExe = buildDir.resolve("Game.exe");
        byte[] content = "UNITY_BINARY_PAYLOAD_v1.0.0".getBytes();
        Files.write(gameExe, content);

        BuildArtifact artifact = artifactManager.registerArtifact(
                "art-1", "proj-A", null, "WINDOWS", "x86_64",
                projectARoot, "Builds/Windows/Game.exe"
        );

        assertNotNull(artifact);
        assertEquals("art-1", artifact.getArtifactId());
        assertEquals("proj-A", artifact.getProjectId());
        assertEquals(content.length, artifact.getSizeBytes());
        assertNotNull(artifact.getSha256Checksum());
        assertEquals(ArtifactStatus.VERIFIED, artifact.getStatus());

        // Verify integrity passes
        boolean intact = artifactManager.verifyArtifactIntegrity("art-1", projectARoot);
        assertTrue(intact, "Freshly registered artifact must be verified intact");
    }

    @Test
    void testTamperDetectionOnCorruptedOrModifiedFile() throws Exception {
        Path buildDir = projectARoot.resolve("Builds/Windows");
        Files.createDirectories(buildDir);
        Path gameExe = buildDir.resolve("Game.exe");
        Files.write(gameExe, "AUTHENTIC_CONTENT".getBytes());

        BuildArtifact artifact = artifactManager.registerArtifact(
                "art-tamper", "proj-A", null, "WINDOWS", "x86_64",
                projectARoot, "Builds/Windows/Game.exe"
        );

        // Tamper with the artifact on disk
        Files.write(gameExe, "TAMPERED_INJECTED_MALICIOUS_PAYLOAD".getBytes());

        // Integrity verification must detect the modification
        boolean intact = artifactManager.verifyArtifactIntegrity("art-tamper", projectARoot);
        assertFalse(intact, "Tampered artifact file must fail integrity verification");

        // Status should be set to CORRUPTED
        Optional<BuildArtifact> updated = artifactManager.getArtifact("proj-A", "art-tamper");
        assertTrue(updated.isPresent());
        assertEquals(ArtifactStatus.CORRUPTED, updated.get().getStatus());
    }

    @Test
    void testCrossProjectTenantIsolation() throws Exception {
        Path buildDirA = projectARoot.resolve("Builds");
        Files.createDirectories(buildDirA);
        Path gameExeA = buildDirA.resolve("GameA.exe");
        Files.write(gameExeA, "PAYLOAD_A".getBytes());

        artifactManager.registerArtifact(
                "art-tenant-a", "proj-A", null, "WINDOWS", "x86_64",
                projectARoot, "Builds/GameA.exe"
        );

        // Project A can access its own artifact
        assertTrue(artifactManager.getArtifact("proj-A", "art-tenant-a").isPresent());

        // Project B CANNOT access Project A's artifact
        assertFalse(artifactManager.getArtifact("proj-B", "art-tenant-a").isPresent(),
                "Tenant isolation: Project B must not access Project A's artifact");
    }

    @Test
    void testZeroByteOrMissingFileRejectedAtRegistration() {
        // Missing file
        assertThrows(IllegalStateException.class, () -> {
            artifactManager.registerArtifact("art-missing", "proj-A", null, "WINDOWS", "x86_64",
                    projectARoot, "Builds/DoesNotExist.exe");
        });

        // 0-byte file
        assertThrows(IllegalStateException.class, () -> {
            Path empty = projectARoot.resolve("empty.exe");
            Files.createFile(empty);
            artifactManager.registerArtifact("art-empty", "proj-A", null, "WINDOWS", "x86_64",
                    projectARoot, "empty.exe");
        });
    }
}
