package com.unityagent.studio;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.model.BuildRecord;
import com.unityagent.studio.service.StudioBuildService;
import com.unityagent.unity.UnityConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildManagementTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private UnityConnection unityConnection;
    private StudioBuildService buildService;

    @BeforeEach
    void setUp() throws Exception {
        memoryDb = new MemoryDatabase(tempDir.resolve("build_test.db").toString());
        memoryDb.initialize();

        // Seed test project for foreign key constraints
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO projects (project_id, project_name, created_at) VALUES (?, ?, datetime('now'))")) {
            stmt.setString(1, "proj_build_1");
            stmt.setString(2, "Build Test Project");
            stmt.executeUpdate();
        }

        unityConnection = Mockito.mock(UnityConnection.class);
        buildService = new StudioBuildService(memoryDb, unityConnection);
    }

    @AfterEach
    void tearDown() {
        if (memoryDb != null) {
            memoryDb.shutdown();
        }
    }

    @Test
    @DisplayName("Should prevent directory traversal attacks in build output path")
    void testOutputPathSecurity() {
        String projectRoot = tempDir.resolve("project_root").toString();

        // Traversal attempts
        assertThrows(SecurityException.class, () ->
            buildService.sanitizeAndValidateOutputPath(projectRoot, "../escaped_dir")
        );

        assertThrows(SecurityException.class, () ->
            buildService.sanitizeAndValidateOutputPath(projectRoot, "Builds/../../windows/system32")
        );

        // Valid relative paths inside project root
        String validPath = buildService.sanitizeAndValidateOutputPath(projectRoot, "Builds/Standalone/Game.exe");
        assertNotNull(validPath);
        assertTrue(validPath.startsWith(new File(projectRoot).getAbsolutePath()));
    }

    @Test
    @DisplayName("Should queue build and persist with QUEUED status")
    void testQueueBuildAndPersistence() {
        BuildRecord record = buildService.queueBuild("proj_build_1", "StandaloneWindows64", "Release", "Builds/Game.exe");
        assertNotNull(record);
        assertEquals(BuildRecord.BuildStatus.QUEUED, record.getStatus());
        assertEquals("StandaloneWindows64", record.getPlatform());
        assertEquals("Release", record.getConfiguration());

        BuildRecord loaded = buildService.getBuild(record.getBuildId());
        assertNotNull(loaded);
        assertEquals(record.getBuildId(), loaded.getBuildId());
        assertEquals(BuildRecord.BuildStatus.QUEUED, loaded.getStatus());
    }

    @Test
    @DisplayName("Should succeed and record evidence when physical artifact exists on disk")
    void testExecuteBuildSucceedsWithPhysicalArtifact() throws Exception {
        Path artifactFile = tempDir.resolve("Game.exe");
        Files.writeString(artifactFile, "MZ_MOCK_EXECUTABLE_BINARY_DATA_FOR_TEST");

        BuildRecord queued = buildService.queueBuild("proj_build_1", "StandaloneWindows64", "Release", artifactFile.toString());
        BuildRecord completed = buildService.executeBuild(queued.getBuildId());

        assertEquals(BuildRecord.BuildStatus.SUCCEEDED, completed.getStatus());
        assertTrue(completed.getArtifactSize() > 0);
        assertNotNull(completed.getValidationEvidence());
        assertTrue(completed.getValidationEvidence().contains("Physical artifact verified on disk"));
        assertNull(completed.getErrors());
    }

    @Test
    @DisplayName("Should fail when physical artifact is missing on disk - never falsely report success")
    void testExecuteBuildFailsWhenArtifactMissing() {
        Path missingPath = tempDir.resolve("NonExistentBuild.exe");

        BuildRecord queued = buildService.queueBuild("proj_build_1", "StandaloneWindows64", "Release", missingPath.toString());
        BuildRecord completed = buildService.executeBuild(queued.getBuildId());

        assertEquals(BuildRecord.BuildStatus.FAILED, completed.getStatus());
        assertEquals(0, completed.getArtifactSize());
        assertNotNull(completed.getErrors());
        assertTrue(completed.getErrors().contains("Physical artifact not found"));
    }

    @Test
    @DisplayName("Should fail when physical artifact file exists but is empty (0 bytes)")
    void testExecuteBuildFailsWhenArtifactEmpty() throws Exception {
        Path emptyFile = tempDir.resolve("EmptyBuild.exe");
        Files.createFile(emptyFile);

        BuildRecord queued = buildService.queueBuild("proj_build_1", "StandaloneWindows64", "Release", emptyFile.toString());
        BuildRecord completed = buildService.executeBuild(queued.getBuildId());

        assertEquals(BuildRecord.BuildStatus.FAILED, completed.getStatus());
        assertEquals(0, completed.getArtifactSize());
        assertNotNull(completed.getErrors());
        assertTrue(completed.getErrors().contains("empty (0 bytes)"));
    }

    @Test
    @DisplayName("Should cancel queued build")
    void testCancelBuild() {
        BuildRecord queued = buildService.queueBuild("proj_build_1", "WebGL", "Debug", "Builds/WebGL");
        BuildRecord cancelled = buildService.cancelBuild(queued.getBuildId());

        assertEquals(BuildRecord.BuildStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getCompletedAt());
    }

    @Test
    @DisplayName("Should query all builds for a project ordered by creation time")
    void testQueryBuildsForProject() {
        buildService.queueBuild("proj_build_1", "WebGL", "Debug", "Builds/1");
        buildService.queueBuild("proj_build_1", "StandaloneWindows64", "Release", "Builds/2");

        List<BuildRecord> list = buildService.getBuildsForProject("proj_build_1");
        assertEquals(2, list.size());
    }
}
