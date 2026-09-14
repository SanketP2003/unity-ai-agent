package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ProjectTemplate;
import com.unityagent.product.service.ProjectTemplateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Template Management & Instantiation Tests")
class TemplateManagementTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private ProjectTemplateManager templateManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("template_mgmt_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        templateManager = new ProjectTemplateManager(db);
        templateManager.registerBuiltinTemplates();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    @DisplayName("Verify all 5 built-in templates are correctly registered")
    void testBuiltinTemplatesRegistration() {
        List<ProjectTemplate> templates = templateManager.listTemplates();
        assertNotNull(templates);
        assertTrue(templates.size() >= 5, "Should have at least 5 standard templates registered");

        assertTrue(templates.stream().anyMatch(t -> "tpl-blank-3d".equals(t.getTemplateId())));
        assertTrue(templates.stream().anyMatch(t -> "tpl-3d-platformer".equals(t.getTemplateId())));
        assertTrue(templates.stream().anyMatch(t -> "tpl-top-down".equals(t.getTemplateId())));
        assertTrue(templates.stream().anyMatch(t -> "tpl-third-person".equals(t.getTemplateId())));
        assertTrue(templates.stream().anyMatch(t -> "tpl-2d-game".equals(t.getTemplateId())));
    }

    @Test
    @DisplayName("Verify template instantiation generates Unity structure and registers SSOT record")
    void testInstantiateTemplate() throws Exception {
        Path targetDir = tempDir.resolve("my_platformer");
        templateManager.instantiateProject("tpl-3d-platformer", "proj_tpl_01", "Neon Platformer", targetDir);

        assertTrue(Files.exists(targetDir.resolve("Assets")));
        assertTrue(Files.exists(targetDir.resolve("Assets").resolve("Scripts")));
        assertTrue(Files.exists(targetDir.resolve("Assets").resolve("Scenes")));
        assertTrue(Files.exists(targetDir.resolve("ProjectSettings")));

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT project_name, unity_version FROM projects WHERE project_id = ?")) {
            ps.setString(1, "proj_tpl_01");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Project record must be in database");
                assertEquals("Neon Platformer", rs.getString("project_name"));
            }
        }
    }

    @Test
    @DisplayName("Verify unknown template ID throws IllegalArgumentException")
    void testInstantiateUnknownTemplateThrows() {
        Path targetDir = tempDir.resolve("invalid");
        assertThrows(IllegalArgumentException.class, () ->
                templateManager.instantiateProject("non_existent_id", "p_fail", "Fail", targetDir));
    }
}
