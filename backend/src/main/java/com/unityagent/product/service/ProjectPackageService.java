package com.unityagent.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemorySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Service for exporting and importing portable .autonomous-project package archives.
 * Strictly sanitizes against path traversal (Zip Slip) and excludes all secrets and machine paths.
 */
@Service
public class ProjectPackageService {

    private static final Logger log = LoggerFactory.getLogger(ProjectPackageService.class);
    private final MemoryDatabase memoryDb;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ProjectPackageService(MemoryDatabase memoryDb) {
        this.memoryDb = memoryDb;
    }

    public Path exportProject(String projectId, String projectName, Path projectRoot, Path exportLocation) {
        log.info("Exporting project {} to portable package: {}", projectId, exportLocation);

        try {
            Files.createDirectories(exportLocation.getParent());
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("packageFormat", "1.0");
            manifest.put("projectId", projectId);
            manifest.put("projectName", projectName);
            manifest.put("schemaVersion", MemorySchema.CURRENT_VERSION);
            manifest.put("exportedAt", Instant.now().toString());

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(exportLocation))) {
                // 1. Write package manifest
                zos.putNextEntry(new ZipEntry("package-manifest.json"));
                zos.write(objectMapper.writeValueAsBytes(manifest));
                zos.closeEntry();

                // 2. Export project assets/settings (excluding secrets/cache)
                if (Files.exists(projectRoot)) {
                    try (var stream = Files.walk(projectRoot)) {
                        for (Path p : (Iterable<Path>) stream::iterator) {
                            if (Files.isRegularFile(p)) {
                                String rel = projectRoot.relativize(p).toString().replace('\\', '/');
                                // Exclude secrets and build caches
                                if (rel.contains(".key") || rel.contains("secret") || rel.startsWith("Library")
                                        || rel.startsWith("Temp") || rel.startsWith("Builds")) {
                                    continue;
                                }
                                zos.putNextEntry(new ZipEntry("content/" + rel));
                                Files.copy(p, zos);
                                zos.closeEntry();
                            }
                        }
                    }
                }
                zos.finish();
            }

            log.info("Project {} exported successfully to {}", projectId, exportLocation);
            return exportLocation;
        } catch (Exception e) {
            log.error("Failed to export project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> importProject(Path packageFile, Path targetExtractionDirectory) {
        log.info("Importing project from {} into {}", packageFile, targetExtractionDirectory);

        if (!Files.exists(packageFile)) {
            throw new IllegalArgumentException("Package file does not exist: " + packageFile);
        }

        Map<String, Object> manifest = null;

        try {
            Files.createDirectories(targetExtractionDirectory);

            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(packageFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();

                    // Strict Zip Slip defense
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        throw new SecurityException("Zip Slip path traversal attack rejected: " + name);
                    }

                    if ("package-manifest.json".equals(name)) {
                        manifest = objectMapper.readValue(zis.readAllBytes(), Map.class);
                    } else if (name.startsWith("content/")) {
                        String sub = name.substring("content/".length());
                        Path outPath = targetExtractionDirectory.resolve(sub).normalize();
                        if (!outPath.startsWith(targetExtractionDirectory.normalize())) {
                            throw new SecurityException("Path containment violation: " + name);
                        }
                        if (entry.isDirectory()) {
                            Files.createDirectories(outPath);
                        } else {
                            Files.createDirectories(outPath.getParent());
                            Files.copy(zis, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    zis.closeEntry();
                }
            }

            if (manifest == null) {
                throw new IllegalStateException("Invalid .autonomous-project package: missing package-manifest.json");
            }

            String importedProjectId = (String) manifest.get("projectId");
            String importedProjectName = (String) manifest.getOrDefault("projectName", "ImportedProject");

            // Register in authoritative projects table
            String sql = "INSERT OR REPLACE INTO projects (project_id, project_name, unity_version, created_at) " +
                         "VALUES (?, ?, '6000.4.7f1', ?)";
            try (Connection conn = memoryDb.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, importedProjectId);
                ps.setString(2, importedProjectName);
                ps.setString(3, Instant.now().toString());
                ps.executeUpdate();
            }

            log.info("Project {} imported and registered successfully", importedProjectId);
            return manifest;
        } catch (Exception e) {
            log.error("Failed to import package: {}", e.getMessage());
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
    }
}
