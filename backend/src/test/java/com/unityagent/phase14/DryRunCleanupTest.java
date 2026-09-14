package com.unityagent.phase14;

import com.unityagent.product.service.CleanupQuarantineService;
import com.unityagent.product.service.PathSafetyValidator;
import com.unityagent.product.service.RepositoryHygieneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DryRunCleanupTest {

    @TempDir
    Path tempDir;

    private RepositoryHygieneService hygieneService;

    @BeforeEach
    void setUp() throws IOException {
        Path quarantineDir = tempDir.resolve(".quarantine");
        Files.createDirectories(quarantineDir);
        PathSafetyValidator validator = new PathSafetyValidator();
        CleanupQuarantineService quarantineService = new CleanupQuarantineService(quarantineDir);
        hygieneService = new RepositoryHygieneService(validator, quarantineService);
    }

    @Test
    void testDryRunDoesNotMutateFiles() throws IOException {
        Path mockRoot = tempDir.resolve("mock_repo");
        Files.createDirectories(mockRoot);

        Path tempFile = mockRoot.resolve("test.tmp");
        Files.writeString(tempFile, "temporary data");

        Path bakFile = mockRoot.resolve("script.cs.bak");
        Files.writeString(bakFile, "backup data");

        Path pomFile = mockRoot.resolve("pom.xml");
        Files.writeString(pomFile, "<project></project>");

        // Dry run
        RepositoryHygieneService.CleanupReport dryReport = hygieneService.performDryRun(mockRoot);
        assertTrue(dryReport.dryRun);
        assertEquals(2, dryReport.deletedCount);
        assertEquals(1, dryReport.protectedCount);

        // Verification: files must STILL exist on disk after dry run!
        assertTrue(Files.exists(tempFile));
        assertTrue(Files.exists(bakFile));
        assertTrue(Files.exists(pomFile));

        // Execution
        RepositoryHygieneService.CleanupReport execReport = hygieneService.executeCleanup(mockRoot);
        assertFalse(execReport.dryRun);
        assertEquals(2, execReport.deletedCount);

        // After execution: temp files must be deleted, pom.xml preserved!
        assertFalse(Files.exists(tempFile));
        assertFalse(Files.exists(bakFile));
        assertTrue(Files.exists(pomFile));
    }
}
