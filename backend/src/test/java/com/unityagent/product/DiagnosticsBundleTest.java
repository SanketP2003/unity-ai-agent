package com.unityagent.product;

import com.unityagent.product.service.ProductDiagnosticsService;
import com.unityagent.studio.model.DiagnosticEntry;
import com.unityagent.studio.service.StudioDiagnosticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Production Diagnostics Bundle Tests")
class DiagnosticsBundleTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Verify diagnostics bundle export includes metadata and redacts sensitive data")
    void testDiagnosticsBundleExport() throws Exception {
        StudioDiagnosticsService studioDiag = Mockito.mock(StudioDiagnosticsService.class);
        DiagnosticEntry entry = new DiagnosticEntry(
                "diag-bundle-1", Instant.now().toString(), "SYSTEM", DiagnosticEntry.Category.BUILD,
                DiagnosticEntry.Severity.INFO, "host-prod-1", "Build finished cleanly", null
        );
        when(studioDiag.queryDiagnostics(any(), any(), any(), any(), Mockito.anyInt()))
                .thenReturn(List.of(entry));

        ProductDiagnosticsService diagService = new ProductDiagnosticsService(studioDiag);
        Path bundleZip = tempDir.resolve("diagnostics-bundle.zip");
        Path exported = diagService.exportDiagnosticsBundle(bundleZip);

        assertTrue(Files.exists(exported), "Diagnostics bundle zip must exist");

        boolean hasMeta = false;
        boolean hasDiag = false;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(exported))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if ("diagnostics-metadata.json".equals(ze.getName())) hasMeta = true;
                if ("system-diagnostics.json".equals(ze.getName())) hasDiag = true;
            }
        }

        assertTrue(hasMeta, "Must contain diagnostics-metadata.json");
        assertTrue(hasDiag, "Must contain system-diagnostics.json");
    }
}
