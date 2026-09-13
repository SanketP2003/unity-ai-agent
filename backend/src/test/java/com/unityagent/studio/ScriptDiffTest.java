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

class ScriptDiffTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private StudioController studioController;

    @BeforeEach
    void setUp() throws Exception {
        memoryDb = new MemoryDatabase(tempDir.resolve("script_test.db").toString());
        memoryDb.initialize();

        // Seed project and script
        try (Connection conn = memoryDb.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO projects (project_id, project_name, created_at) VALUES (?, ?, datetime('now'))")) {
                ps.setString(1, "proj_scripts");
                ps.setString(2, "Script Proj");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO scripts (project_id, script_path, class_name, last_modified_at, source) VALUES (?, ?, ?, datetime('now'), ?)")) {
                ps.setString(1, "proj_scripts");
                ps.setString(2, "Assets/Scripts/PlayerController.cs");
                ps.setString(3, "PlayerController");
                ps.setString(4, "MonoBehaviour with Move() and Jump()");
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
    @DisplayName("POST /api/studio/projects/{projectId}/scripts/diff should calculate clean line diffs")
    void testCalculateScriptDiff() {
        String beforeCode = "using UnityEngine;\npublic class Player {\n    int speed = 5;\n}";
        String afterCode = "using UnityEngine;\npublic class Player {\n    int speed = 10;\n    void Jump() {}\n}";

        ResponseEntity<Map<String, Object>> resp = studioController.calculateScriptDiff("proj_scripts", Map.of(
                "beforeContent", beforeCode,
                "afterContent", afterCode
        ));

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());

        @SuppressWarnings("unchecked")
        List<String> diffLines = (List<String>) resp.getBody().get("diffLines");
        assertNotNull(diffLines);
        assertTrue(diffLines.stream().anyMatch(l -> l.startsWith("-    int speed = 5;")));
        assertTrue(diffLines.stream().anyMatch(l -> l.startsWith("+    int speed = 10;")));
        assertTrue(diffLines.stream().anyMatch(l -> l.startsWith("+    void Jump() {}")));
    }

    @Test
    @DisplayName("GET /api/studio/projects/{projectId}/scripts should list indexed scripts")
    void testGetScripts() {
        List<Map<String, Object>> scripts = studioController.getScripts("proj_scripts");
        assertNotNull(scripts);
        assertEquals(1, scripts.size());
        assertEquals("Assets/Scripts/PlayerController.cs", scripts.get(0).get("path"));
        assertEquals("PlayerController", scripts.get(0).get("className"));
    }
}
