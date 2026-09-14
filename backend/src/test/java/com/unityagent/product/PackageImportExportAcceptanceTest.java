package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.service.ProjectPackageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class PackageImportExportAcceptanceTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private SQLiteMemoryRepository repository;
    private ProjectPackageService packageService;
    private Path projectRoot;
    private Path packageFile;
    private Path importTarget;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("pkg_acceptance.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        packageService = new ProjectPackageService(db);

        projectRoot = tempDir.resolve("export_source");
        importTarget = tempDir.resolve("import_destination");
        packageFile = tempDir.resolve("bundles/GamePackage.autonomous-project");

        Files.createDirectories(projectRoot.resolve("Assets/Scripts"));
        Files.createDirectories(projectRoot.resolve("Assets/Scenes"));

        Files.writeString(projectRoot.resolve("Assets/Scripts/PlayerController.cs"), "public class PlayerController {}");
        Files.writeString(projectRoot.resolve("Assets/Scenes/Main.unity"), "%YAML 1.1\n--- !u!29 &1\n");
        Files.writeString(projectRoot.resolve("Assets/Scripts/apikey.secret"), "SECRET_API_KEY_NEVER_EXPORT");
        Files.writeString(projectRoot.resolve("Assets/Scripts/credentials.json"), "{\"secret\":\"top-secret\"}");

        repository.upsertProject(new ProjectMemory("proj-export-pkg", "Package Export Game", "2022.3.20f1", "WINDOWS", "URP", "1.0.0", "fp-pkg"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testEndToEndExportImportLifecycle() {
        Path exported = packageService.exportProject("proj-export-pkg", "Package Export Game", projectRoot, packageFile);
        assertTrue(Files.exists(exported));

        Map<String, Object> manifest = packageService.importProject(exported, importTarget);
        assertNotNull(manifest);
        assertEquals("proj-export-pkg", manifest.get("projectId"));
        assertEquals("Package Export Game", manifest.get("projectName"));

        // Verify valid files imported
        assertTrue(Files.exists(importTarget.resolve("Assets/Scripts/PlayerController.cs")));
        assertTrue(Files.exists(importTarget.resolve("Assets/Scenes/Main.unity")));

        // Verify secret files excluded
        assertFalse(Files.exists(importTarget.resolve("Assets/Scripts/apikey.secret")));
        assertFalse(Files.exists(importTarget.resolve("Assets/Scripts/credentials.json")));

        // Verify project record registered in SSOT
        assertTrue(repository.findProject("proj-export-pkg").isPresent());
    }

    @Test
    void testZipSlipAttackRejectionOnImport() throws IOException {
        Path maliciousPackage = tempDir.resolve("malicious.autonomous-project");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(maliciousPackage.toFile()))) {
            // Write valid manifest
            ZipEntry manifestEntry = new ZipEntry("package-manifest.json");
            zos.putNextEntry(manifestEntry);
            zos.write("{\"projectId\":\"proj-evil\",\"projectName\":\"Evil\"}".getBytes());
            zos.closeEntry();

            // Inject Zip Slip traversal
            ZipEntry slipEntry = new ZipEntry("content/../../evil_file.txt");
            zos.putNextEntry(slipEntry);
            zos.write("malicious payload".getBytes());
            zos.closeEntry();
        }

        Exception ex = assertThrows(RuntimeException.class, () -> {
            packageService.importProject(maliciousPackage, importTarget);
        });

        assertTrue(ex.getMessage().contains("Zip Slip") || ex.getMessage().contains("traversal"));
        assertFalse(Files.exists(tempDir.resolve("evil_file.txt")));
    }

    @Test
    void testMissingManifestPackageRejection() throws IOException {
        Path badPackage = tempDir.resolve("no_manifest.autonomous-project");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(badPackage.toFile()))) {
            ZipEntry fileEntry = new ZipEntry("content/Assets/test.cs");
            zos.putNextEntry(fileEntry);
            zos.write("public class Test {}".getBytes());
            zos.closeEntry();
        }

        Exception ex = assertThrows(RuntimeException.class, () -> {
            packageService.importProject(badPackage, importTarget);
        });

        assertTrue(ex.getMessage().contains("missing package-manifest.json"));
    }
}
