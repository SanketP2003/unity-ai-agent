package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.service.ProjectPackageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProjectImportExportTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private ProjectPackageService packageService;
    private Path projectRoot;
    private Path packageZip;
    private Path importRoot;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("pkg_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        packageService = new ProjectPackageService(db);

        projectRoot = tempDir.resolve("export_source");
        importRoot = tempDir.resolve("import_target");
        packageZip = tempDir.resolve("packages/Game.autonomous-project");

        Files.createDirectories(projectRoot.resolve("Assets/Scripts"));
        Files.writeString(projectRoot.resolve("Assets/Scripts/GameManager.cs"), "public class GameManager {}");
        Files.writeString(projectRoot.resolve("Assets/Scripts/token.secret"), "SECRET_TOKEN_DO_NOT_EXPORT");

        // SSOT project
        repository.upsertProject(new ProjectMemory("proj-export-1", "Export Game", "6000.4.7f1", "WINDOWS", "URP", "1.0.0", "fp-export-1"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testExportAndImportCycle() {
        Path exported = packageService.exportProject("proj-export-1", "Export Game", projectRoot, packageZip);
        assertTrue(Files.exists(exported));

        Map<String, Object> result = packageService.importProject(exported, importRoot);
        assertNotNull(result);
        assertEquals("proj-export-1", result.get("projectId"));
        assertEquals("Export Game", result.get("projectName"));

        // Verify extracted files
        assertTrue(Files.exists(importRoot.resolve("Assets/Scripts/GameManager.cs")));
        // Verify secret file was NOT exported
        assertFalse(Files.exists(importRoot.resolve("Assets/Scripts/token.secret")));

        // Verify imported project registered in SSOT projects table
        assertTrue(repository.findProject("proj-export-1").isPresent());
    }

    @Test
    void testImportNonExistentPackageThrowsException() {
        Path nonExistent = tempDir.resolve("missing.autonomous-project");
        assertThrows(IllegalArgumentException.class, () -> {
            packageService.importProject(nonExistent, importRoot);
        });
    }
}
