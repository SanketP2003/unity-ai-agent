package com.unityagent.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service evaluating host system requirements: Java runtime, memory, disk, filesystem permissions, and SQLite.
 */
@Service
public class SystemRequirementsService {

    private static final Logger log = LoggerFactory.getLogger(SystemRequirementsService.class);
    private static final long MIN_REQUIRED_DISK_BYTES = 1024L * 1024L * 1024L; // 1 GB minimum working disk

    public record RequirementsReport(
            boolean satisfied,
            int javaVersion,
            long maxMemoryBytes,
            long freeDiskBytes,
            boolean filesystemWritable,
            boolean sqliteSupported,
            String details
    ) {}

    public RequirementsReport checkRequirements(Path workingDirectory) {
        // 1. Java version check (Java 21+ required)
        int javaVersion = Runtime.version().feature();
        boolean javaOk = javaVersion >= 21;

        // 2. JVM available memory
        long maxMemory = Runtime.getRuntime().maxMemory();
        boolean memoryOk = maxMemory >= 512L * 1024L * 1024L; // At least 512MB heap

        // 3. Disk space and filesystem write permissions
        Path checkDir = workingDirectory != null ? workingDirectory : Path.of(".");
        File dirFile = checkDir.toFile();
        long freeDisk = dirFile.getUsableSpace();
        boolean diskOk = freeDisk >= MIN_REQUIRED_DISK_BYTES;

        boolean writable = false;
        try {
            Path tempProbe = checkDir.resolve(".probe_" + System.currentTimeMillis() + ".tmp");
            Files.writeString(tempProbe, "probe");
            Files.deleteIfExists(tempProbe);
            writable = true;
        } catch (Exception e) {
            log.warn("Filesystem write check failed on {}: {}", checkDir, e.getMessage());
        }

        // 4. SQLite JDBC availability check
        boolean sqliteOk = false;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            sqliteOk = conn != null && !conn.isClosed();
        } catch (Exception e) {
            log.warn("SQLite in-memory test check failed: {}", e.getMessage());
        }

        boolean allSatisfied = javaOk && memoryOk && diskOk && writable && sqliteOk;
        String details = allSatisfied
                ? "System requirements fully satisfied"
                : String.format("Check failures: javaOk=%b, memoryOk=%b, diskOk=%b, writable=%b, sqliteOk=%b",
                javaOk, memoryOk, diskOk, writable, sqliteOk);

        return new RequirementsReport(allSatisfied, javaVersion, maxMemory, freeDisk, writable, sqliteOk, details);
    }

    public Map<String, Object> toStatusMap(RequirementsReport report) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("satisfied", report.satisfied());
        map.put("javaVersion", report.javaVersion());
        map.put("maxMemoryMb", report.maxMemoryBytes() / (1024 * 1024));
        map.put("freeDiskMb", report.freeDiskBytes() / (1024 * 1024));
        map.put("filesystemWritable", report.filesystemWritable());
        map.put("sqliteSupported", report.sqliteSupported());
        map.put("details", report.details());
        return map;
    }
}
