package com.unityagent.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * Service for safely quarantining uncertain studio-owned files before deletion,
 * preserving SHA-256 checksums and audit trails.
 */
@Service
public class CleanupQuarantineService {

    private static final Logger log = LoggerFactory.getLogger(CleanupQuarantineService.class);
    private final Path defaultQuarantineDir;
    private final ObjectMapper mapper;

    public CleanupQuarantineService() {
        this(Paths.get(System.getProperty("user.home"), ".unityagent", "quarantine"));
    }

    public CleanupQuarantineService(Path quarantineDir) {
        this.defaultQuarantineDir = quarantineDir;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static class QuarantineRecord {
        public String operationId;
        public String timestamp;
        public String originalPath;
        public String quarantinedPath;
        public String sha256;
        public String owner;
        public String reason;
        public long sizeBytes;

        public QuarantineRecord() {}

        public QuarantineRecord(String operationId, String originalPath, String quarantinedPath,
                                String sha256, String owner, String reason, long sizeBytes) {
            this.operationId = operationId;
            this.timestamp = Instant.now().toString();
            this.originalPath = originalPath;
            this.quarantinedPath = quarantinedPath;
            this.sha256 = sha256;
            this.owner = owner;
            this.reason = reason;
            this.sizeBytes = sizeBytes;
        }
    }

    /**
     * Safely moves a file to the quarantine directory and writes a manifest entry.
     */
    public QuarantineRecord quarantineFile(Path file, String owner, String reason, String operationId) throws IOException {
        if (file == null || !Files.exists(file)) {
            throw new IllegalArgumentException("File to quarantine does not exist: " + file);
        }

        String opId = (operationId == null || operationId.isBlank()) ? "op_" + UUID.randomUUID().toString().substring(0, 8) : operationId;
        String sha256 = computeSha256(file);
        long size = Files.size(file);

        String timestampFolder = String.valueOf(System.currentTimeMillis());
        Path targetDir = defaultQuarantineDir.resolve(timestampFolder);
        Files.createDirectories(targetDir);

        Path quarantinedFile = targetDir.resolve(file.getFileName().toString());
        Files.move(file, quarantinedFile, StandardCopyOption.REPLACE_EXISTING);

        QuarantineRecord record = new QuarantineRecord(
                opId,
                file.toAbsolutePath().normalize().toString().replace('\\', '/'),
                quarantinedFile.toAbsolutePath().normalize().toString().replace('\\', '/'),
                sha256,
                owner != null ? owner : "STUDIO",
                reason != null ? reason : "Uncertain file quarantine prior to cleanup",
                size
        );

        Path manifestPath = targetDir.resolve("quarantine-manifest.json");
        mapper.writeValue(manifestPath.toFile(), record);

        log.info("Quarantined file {} to {} (hash: {}, reason: {})", file, quarantinedFile, sha256, reason);
        return record;
    }

    public List<QuarantineRecord> listQuarantinedRecords() {
        List<QuarantineRecord> records = new ArrayList<>();
        if (!Files.exists(defaultQuarantineDir)) {
            return records;
        }

        try (var stream = Files.walk(defaultQuarantineDir)) {
            stream.filter(p -> p.getFileName().toString().equals("quarantine-manifest.json"))
                    .forEach(p -> {
                        try {
                            QuarantineRecord rec = mapper.readValue(p.toFile(), QuarantineRecord.class);
                            records.add(rec);
                        } catch (Exception e) {
                            log.warn("Failed reading quarantine manifest {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("Failed walking quarantine directory: {}", e.getMessage());
        }
        return records;
    }

    public Path getQuarantineDir() {
        return defaultQuarantineDir;
    }

    private String computeSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
