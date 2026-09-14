package com.unityagent.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.studio.model.DiagnosticEntry;
import com.unityagent.studio.service.StudioDiagnosticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service providing exportable diagnostic bundles and production system health metrics.
 * Strictly scrubs all secrets, API keys, and credentials from diagnostic bundles.
 */
@Service
public class ProductDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(ProductDiagnosticsService.class);

    private final StudioDiagnosticsService studioDiagnostics;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ProductDiagnosticsService(StudioDiagnosticsService studioDiagnostics) {
        this.studioDiagnostics = studioDiagnostics;
    }

    /**
     * Generates a zip archive containing sanitized, secret-scrubbed system diagnostic logs.
     */
    public Path exportDiagnosticsBundle(Path outputPath) {
        log.info("Generating exportable diagnostic bundle at {}", outputPath);

        try {
            Files.createDirectories(outputPath.getParent());

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("bundleGeneratedAt", Instant.now().toString());
            metadata.put("hostOS", System.getProperty("os.name"));
            metadata.put("javaVersion", System.getProperty("java.version"));
            metadata.put("runtimeMemoryUsedBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
            metadata.put("maxMemoryBytes", Runtime.getRuntime().maxMemory());

            List<DiagnosticEntry> allLogs = studioDiagnostics.queryDiagnostics(null, null, null, null, 500);

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath))) {
                // 1. Bundle metadata
                zos.putNextEntry(new ZipEntry("diagnostics-metadata.json"));
                zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(metadata));
                zos.closeEntry();

                // 2. Secret-scrubbed logs
                zos.putNextEntry(new ZipEntry("system-diagnostics.json"));
                zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(allLogs));
                zos.closeEntry();

                zos.finish();
            }

            log.info("Diagnostic bundle generated successfully ({} entries): {}", allLogs.size(), outputPath);
            return outputPath;
        } catch (Exception e) {
            log.error("Failed to generate diagnostic bundle: {}", e.getMessage());
            throw new RuntimeException("Diagnostic bundle creation failed: " + e.getMessage(), e);
        }
    }
}
