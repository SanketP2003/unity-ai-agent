package com.unityagent.studio;

import com.unityagent.api.StudioController;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.studio.model.ProjectActivity;
import com.unityagent.studio.model.StudioProject;
import com.unityagent.studio.model.StudioProjectMetadata;
import com.unityagent.studio.service.StudioProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StudioDashboardApiTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private SQLiteMemoryRepository repository;
    private StudioProjectService projectService;
    private StudioController studioController;

    @BeforeEach
    void setUp() {
        memoryDb = new MemoryDatabase(tempDir.resolve("studio_api_test.db").toString());
        memoryDb.initialize();
        repository = new SQLiteMemoryRepository(memoryDb);

        // Register two test projects
        repository.upsertProject(new ProjectMemory("p_dash_1", "Platformer", "2022.3.10f1", "Windows", "URP", "1.0.0", null));
        repository.upsertProject(new ProjectMemory("p_dash_2", "RPG", "2023.1.0f1", "Windows", "URP", "1.0.0", null));

        projectService = new StudioProjectService(memoryDb, repository, null, null, null, null);

        studioController = new StudioController(
                projectService,
                null,
                null,
                null,
                null,
                null,
                new com.unityagent.studio.service.StudioBuildService(memoryDb, null),
                new com.unityagent.studio.service.StudioDiagnosticsService(),
                new com.unityagent.studio.service.StudioSecurityService(memoryDb),
                null,
                memoryDb
        );
    }

    @AfterEach
    void tearDown() {
        if (memoryDb != null) {
            memoryDb.shutdown();
        }
    }

    @Test
    @DisplayName("GET /api/studio/projects should return aggregated project projections")
    void testGetProjects() {
        List<StudioProject> projects = studioController.getProjects();
        assertNotNull(projects);
        assertEquals(2, projects.size());
        assertTrue(projects.stream().anyMatch(p -> p.getProjectId().equals("p_dash_1")));
        assertTrue(projects.stream().anyMatch(p -> p.getProjectId().equals("p_dash_2")));
    }

    @Test
    @DisplayName("GET /api/studio/projects/{projectId} should return project details or 404")
    void testGetProjectById() {
        ResponseEntity<StudioProject> resp = studioController.getProject("p_dash_1");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("p_dash_1", resp.getBody().getProjectId());
        assertEquals("Platformer", resp.getBody().getProjectName());

        ResponseEntity<StudioProject> missing = studioController.getProject("non_existent");
        assertEquals(404, missing.getStatusCode().value());
    }

    @Test
    @DisplayName("PUT /api/studio/projects/{projectId}/metadata should update and persist workspace metadata")
    void testUpdateMetadata() {
        StudioProjectMetadata meta = new StudioProjectMetadata();
        meta.setDescription("High-intensity retro platformer");
        meta.setTags("retro, 2d, physics");
        meta.setFavorite(true);
        meta.setTargetFps(120);
        meta.setActiveBuildProfile("Release_Steam");

        ResponseEntity<StudioProjectMetadata> resp = studioController.updateProjectMetadata("p_dash_1", meta);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("High-intensity retro platformer", resp.getBody().getDescription());

        // Verify loaded projection reflects the metadata
        StudioProject project = studioController.getProject("p_dash_1").getBody();
        assertNotNull(project);
        assertEquals("High-intensity retro platformer", project.getMetadata().getDescription());
        assertTrue(project.getMetadata().isFavorite());
        assertEquals(120, project.getMetadata().getTargetFps());
    }

    @Test
    @DisplayName("GET /api/studio/projects/{projectId}/activity should return activity entries")
    void testGetProjectActivity() {
        List<ProjectActivity> activity = studioController.getProjectActivity("p_dash_1", 20);
        assertNotNull(activity);
    }

    @Test
    @DisplayName("GET /api/studio/diagnostics and /audit should expose centralized diagnostics and immutable audit events")
    void testDiagnosticsAndAuditEndpoints() {
        var diags = studioController.getDiagnostics("p_dash_1", null, null, null, 50);
        assertNotNull(diags);

        var audit = studioController.getAuditTrail("p_dash_1", 50);
        assertNotNull(audit);
    }

    @Test
    @DisplayName("Build controller endpoints: Queue and cancel build")
    void testBuildControllerWorkflow() {
        ResponseEntity<?> queueResp = studioController.triggerBuild("p_dash_1", Map.of(
                "platform", "StandaloneWindows64",
                "configuration", "Debug",
                "outputPath", "Builds/Dev/Game.exe"
        ));
        assertEquals(200, queueResp.getStatusCode().value());
        com.unityagent.studio.model.BuildRecord queued = (com.unityagent.studio.model.BuildRecord) queueResp.getBody();
        assertNotNull(queued);
        assertEquals(com.unityagent.studio.model.BuildRecord.BuildStatus.QUEUED, queued.getStatus());

        ResponseEntity<com.unityagent.studio.model.BuildRecord> cancelResp = studioController.cancelBuild(queued.getBuildId());
        assertEquals(200, cancelResp.getStatusCode().value());
        assertEquals(com.unityagent.studio.model.BuildRecord.BuildStatus.CANCELLED, cancelResp.getBody().getStatus());
    }
}
