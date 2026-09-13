package com.unityagent.studio;

import com.unityagent.api.StudioController;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.service.StudioDiagnosticsService;
import com.unityagent.studio.service.StudioProjectService;
import com.unityagent.studio.service.StudioSecurityService;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SceneExplorerTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private UnityConnection unityConnection;
    private StudioController studioController;

    @BeforeEach
    void setUp() {
        memoryDb = new MemoryDatabase(tempDir.resolve("scene_test.db").toString());
        memoryDb.initialize();

        unityConnection = Mockito.mock(UnityConnection.class);
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
                unityConnection,
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
    @DisplayName("GET /api/studio/projects/{projectId}/scene should return live Unity perception when connected")
    void testLiveSceneHierarchy() throws Exception {
        when(unityConnection.isReady()).thenReturn(true);

        UnityMessage mockResp = new UnityMessage();
        mockResp.setData(Map.of(
                "sceneName", "MainScene",
                "objects", List.of(
                        Map.of("name", "Player", "tag", "Player", "active", true),
                        Map.of("name", "Main Camera", "tag", "MainCamera", "active", true)
                )
        ));
        when(unityConnection.sendToolRequest(eq("proj_scene_1"), any(UnityMessage.class))).thenReturn(mockResp);

        ResponseEntity<Map<String, Object>> resp = studioController.getSceneHierarchy("proj_scene_1");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("MainScene", resp.getBody().get("sceneName"));

        List<?> objects = (List<?>) resp.getBody().get("objects");
        assertEquals(2, objects.size());
    }

    @Test
    @DisplayName("GET /api/studio/projects/{projectId}/scene should fall back to cached perception when Unity is disconnected")
    void testCachedSceneFallback() {
        when(unityConnection.isReady()).thenReturn(false);

        ResponseEntity<Map<String, Object>> resp = studioController.getSceneHierarchy("proj_scene_1");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("cached_studio_perception", resp.getBody().get("source"));
        assertNotNull(resp.getBody().get("objects"));
    }
}
