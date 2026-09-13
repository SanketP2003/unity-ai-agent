package com.unityagent.api;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.memory.service.MemoryRetriever;
import com.unityagent.memory.service.ProjectMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryControllerTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private SQLiteMemoryRepository repository;
    private ProjectMemoryService projectService;
    private MemoryRetriever retriever;
    private MemoryController controller;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("controller_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        projectService = new ProjectMemoryService(repository);
        retriever = new MemoryRetriever(repository);
        controller = new MemoryController(projectService, repository, retriever);

        // Seed project
        projectService.registerProject("proj_ctrl", "6000.4.7f1", "CtrlGame", "1.0.0", Map.of());
    }

    @AfterEach
    void tearDown() {
        if (db != null) db.shutdown();
    }

    @Test
    void testListProjects() {
        ResponseEntity<List<ProjectMemory>> resp = controller.listProjects();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().size());
        assertEquals("CtrlGame", resp.getBody().get(0).getProjectName());
    }

    @Test
    void testGetProject() {
        ResponseEntity<?> resp = controller.getProject("proj_ctrl");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody() instanceof ProjectMemory);

        ResponseEntity<?> notFound = controller.getProject("unknown_proj");
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
    }

    @Test
    void testGetProjectSummary() {
        repository.upsertMemoryEntry("proj_ctrl", "ARCHITECTURE", "object:Hero", "Hero Cube", "AGENT", 1.0);
        ResponseEntity<Map<String, Object>> resp = controller.getProjectSummary("proj_ctrl");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("proj_ctrl", resp.getBody().get("projectId"));
        assertEquals(1, resp.getBody().get("totalEntries"));
    }

    @Test
    void testPreviewContext() {
        ResponseEntity<Map<String, Object>> resp = controller.previewContext("proj_ctrl", "Build player");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("proj_ctrl", resp.getBody().get("projectId"));
        assertTrue((Boolean) resp.getBody().get("hasContent"));
        assertTrue(((String) resp.getBody().get("formattedContext")).contains("CtrlGame"));
    }

    @Test
    void testPreferencesEndpoints() {
        ResponseEntity<?> setResp = controller.setPreference("proj_ctrl", Map.of("key", "theme", "value", "cyberpunk"));
        assertEquals(HttpStatus.OK, setResp.getStatusCode());

        ResponseEntity<?> listResp = controller.getPreferences("proj_ctrl");
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        assertNotNull(listResp.getBody());

        // Missing key validation
        ResponseEntity<?> badResp = controller.setPreference("proj_ctrl", Map.of("value", "dark"));
        assertEquals(HttpStatus.BAD_REQUEST, badResp.getStatusCode());
    }

    @Test
    void testDeleteProjectMemory() {
        ResponseEntity<Map<String, Object>> resp = controller.deleteProjectMemory("proj_ctrl");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("DELETED", resp.getBody().get("status"));

        assertTrue(projectService.getProject("proj_ctrl").isEmpty());
    }
}
