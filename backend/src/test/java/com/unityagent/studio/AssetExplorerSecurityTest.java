package com.unityagent.studio;

import com.unityagent.api.StudioController;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.service.StudioDiagnosticsService;
import com.unityagent.studio.service.StudioProjectService;
import com.unityagent.studio.service.StudioSecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AssetExplorerSecurityTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private StudioController studioController;

    @BeforeEach
    void setUp() throws Exception {
        memoryDb = new MemoryDatabase(tempDir.resolve("asset_test.db").toString());
        memoryDb.initialize();

        // Seed project and assets
        try (Connection conn = memoryDb.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO projects (project_id, project_name, created_at) VALUES (?, ?, datetime('now'))")) {
                ps.setString(1, "proj_assets");
                ps.setString(2, "Asset Proj");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO assets (project_id, asset_path, asset_type, asset_guid, metadata, last_modified_at) VALUES (?, ?, ?, ?, ?, datetime('now'))")) {
                ps.setString(1, "proj_assets");
                ps.setString(2, "Assets/Prefabs/Player.prefab");
                ps.setString(3, "Prefab");
                ps.setString(4, "guid_101");
                ps.setString(5, "{\"size\": 1024}");
                ps.executeUpdate();

                ps.setString(1, "proj_assets");
                ps.setString(2, "Assets/Materials/Ground.mat");
                ps.setString(3, "Material");
                ps.setString(4, "guid_102");
                ps.setString(5, "{\"size\": 512}");
                ps.executeUpdate();
            }
        }

        StudioProjectService projectService = Mockito.mock(StudioProjectService.class);

        studioController = new StudioController(
                projectService,
                null,
                null,
                null,
                null,
                null,
                null,
                new StudioDiagnosticsService(),
                new StudioSecurityService(memoryDb),
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
    @DisplayName("GET /api/studio/projects/{projectId}/assets should list assets and support category filtering")
    void testGetAssetsWithFiltering() {
        ResponseEntity<List<Map<String, Object>>> allResp = studioController.getAssets("proj_assets", null);
        assertEquals(200, allResp.getStatusCode().value());
        assertNotNull(allResp.getBody());
        assertEquals(2, allResp.getBody().size());

        ResponseEntity<List<Map<String, Object>>> prefabResp = studioController.getAssets("proj_assets", "Prefab");
        assertEquals(200, prefabResp.getStatusCode().value());
        assertNotNull(prefabResp.getBody());
        assertEquals(1, prefabResp.getBody().size());
        assertEquals("Assets/Prefabs/Player.prefab", prefabResp.getBody().get(0).get("path"));
    }
}
