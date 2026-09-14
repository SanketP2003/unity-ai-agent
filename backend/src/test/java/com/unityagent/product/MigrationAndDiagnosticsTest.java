package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.service.MigrationManager;
import com.unityagent.product.service.ProductDiagnosticsService;
import com.unityagent.studio.model.DiagnosticEntry;
import com.unityagent.studio.service.StudioDiagnosticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MigrationAndDiagnosticsTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MigrationManager migrationManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("mig_diag_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        migrationManager = new MigrationManager(db);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testMigrationManagerCurrentVersion() {
        int ver = migrationManager.getCurrentVersion();
        assertEquals(4, ver, "Memory database initialized in setUp should be at schema version 4");
        assertTrue(migrationManager.migrateToVersion(4), "Migrating to current version should be a no-op success");
    }

    @Test
    void testDiagnosticsBundleExport() throws Exception {
        StudioDiagnosticsService studioDiag = Mockito.mock(StudioDiagnosticsService.class);
        DiagnosticEntry entry = new DiagnosticEntry(
                "diag-1", Instant.now().toString(), "SYSTEM", DiagnosticEntry.Category.BUILD,
                DiagnosticEntry.Severity.INFO, "host-1", "Subsystem operational", null
        );
        when(studioDiag.queryDiagnostics(any(), any(), any(), any(), Mockito.anyInt()))
                .thenReturn(List.of(entry));

        ProductDiagnosticsService diagService = new ProductDiagnosticsService(studioDiag);
        Path bundleZip = tempDir.resolve("diagnostics.zip");
        Path exported = diagService.exportDiagnosticsBundle(bundleZip);

        assertTrue(Files.exists(exported));
        boolean foundMeta = false;
        boolean foundDiag = false;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(exported))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().equals("diagnostics-metadata.json")) foundMeta = true;
                if (ze.getName().equals("system-diagnostics.json")) foundDiag = true;
            }
        }

        assertTrue(foundMeta, "Must include diagnostics-metadata.json");
        assertTrue(foundDiag, "Must include system-diagnostics.json");
    }
}
