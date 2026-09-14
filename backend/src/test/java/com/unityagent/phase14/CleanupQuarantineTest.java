package com.unityagent.phase14;

import com.unityagent.product.service.CleanupQuarantineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CleanupQuarantineTest {

    @TempDir
    Path tempDir;

    private Path quarantineDir;
    private CleanupQuarantineService quarantineService;

    @BeforeEach
    void setUp() throws IOException {
        quarantineDir = tempDir.resolve("quarantine_vault");
        Files.createDirectories(quarantineDir);
        quarantineService = new CleanupQuarantineService(quarantineDir);
    }

    @Test
    void testQuarantineFilePreservesHashAndRecordsManifest() throws IOException {
        Path suspiciousFile = tempDir.resolve("uncertain_artifact.dat");
        Files.writeString(suspiciousFile, "arbitrary-content-for-quarantine-test");

        CleanupQuarantineService.QuarantineRecord record = quarantineService.quarantineFile(
                suspiciousFile,
                "STUDIO",
                "Uncertain build dump",
                "op_test_123"
        );

        // Original file must no longer exist in original spot
        assertFalse(Files.exists(suspiciousFile));

        // Quarantine record must hold valid sha256 and metadata
        assertNotNull(record.sha256);
        assertEquals(64, record.sha256.length()); // SHA-256 hex string
        assertEquals("op_test_123", record.operationId);
        assertEquals("STUDIO", record.owner);

        // File must exist in quarantine location
        Path quarantinedPath = Path.of(record.quarantinedPath);
        assertTrue(Files.exists(quarantinedPath));

        // Listing must return the record
        List<CleanupQuarantineService.QuarantineRecord> records = quarantineService.listQuarantinedRecords();
        assertEquals(1, records.size());
        assertEquals("op_test_123", records.get(0).operationId);
    }
}
