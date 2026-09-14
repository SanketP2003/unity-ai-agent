package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ProjectTemplate;
import com.unityagent.product.service.ProjectTemplateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTemplateTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private ProjectTemplateManager templateManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("template_test.db").toFile();
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
    void testBuiltinTemplatesRegistration() {
        List<ProjectTemplate> templates = templateManager.listTemplates();
        assertFalse(templates.isEmpty(), "Templates list must not be empty");
        assertTrue(templates.stream().anyMatch(t -> t.getTemplateId().equals("tpl-blank-3d")));
        assertTrue(templates.stream().anyMatch(t -> t.getTemplateId().equals("tpl-3d-platformer")));
        assertTrue(templates.stream().anyMatch(t -> t.getTemplateId().equals("tpl-top-down")));
        assertTrue(templates.stream().anyMatch(t -> t.getTemplateId().equals("tpl-third-person")));
        assertTrue(templates.stream().anyMatch(t -> t.getTemplateId().equals("tpl-2d-game")));
    }

    @Test
    void testTemplateInstantiationScaffoldAndSSOT() throws Exception {
        Path targetDir = tempDir.resolve("new_game_project");
        templateManager.instantiateProject("tpl-3d-platformer", "proj-plat-1", "My Platformer", targetDir);

        // Verify directory scaffold
        assertTrue(Files.exists(targetDir.resolve("Assets").resolve("Scenes")));
        assertTrue(Files.exists(targetDir.resolve("Assets").resolve("Scripts")));
        assertTrue(Files.exists(targetDir.resolve("ProjectSettings")));
        assertTrue(Files.exists(targetDir.resolve("Assets/Scenes/PlatformerLevel1.unity")));

        // Verify SSOT projects table record was created
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT project_name, unity_version FROM projects WHERE project_id = ?")) {
            ps.setString(1, "proj-plat-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Project must be registered in SSOT projects table");
                assertEquals("My Platformer", rs.getString("project_name"));
                assertEquals("6000.4.7f1", rs.getString("unity_version"));
            }
        }
    }

    @Test
    void testUnknownTemplateThrowsException() {
        Path targetDir = tempDir.resolve("invalid_game");
        assertThrows(IllegalArgumentException.class, () -> {
            templateManager.instantiateProject("non-existent-tpl", "p1", "Test", targetDir);
        });
    }
}
