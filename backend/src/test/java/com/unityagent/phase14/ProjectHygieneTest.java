package com.unityagent.phase14;

import com.unityagent.product.service.FileOwnershipTracker;
import com.unityagent.product.service.ProjectHygieneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectHygieneTest {

    @TempDir
    Path tempDir;

    private FileOwnershipTracker tracker;
    private ProjectHygieneService hygieneService;

    @BeforeEach
    void setUp() {
        tracker = new FileOwnershipTracker();
        hygieneService = new ProjectHygieneService(tracker);
    }

    @Test
    void testCleanProjectPassesHygieneCheck() throws Exception {
        Path projDir = tempDir.resolve("CleanGame");
        Path assetsDir = projDir.resolve("Assets");
        Path scriptsDir = assetsDir.resolve("Scripts");
        Files.createDirectories(scriptsDir);

        Files.writeString(scriptsDir.resolve("Movement.cs"), "public class Movement {}");

        var report = hygieneService.performHygieneCheck(projDir.toString());
        assertTrue(report.isClean());
        assertTrue(report.getViolations().isEmpty());
        assertDoesNotThrow(() -> hygieneService.enforcePreRunHygiene(projDir.toString()));
        assertDoesNotThrow(() -> hygieneService.enforcePostRunHygiene(projDir.toString()));
    }

    @Test
    void testPollutedProjectFailsHygieneCheckAndCleansSuccessfully() throws Exception {
        Path projDir = tempDir.resolve("PollutedGame");
        Path assetsDir = projDir.resolve("Assets");
        Path scriptsDir = assetsDir.resolve("Scripts");
        Files.createDirectories(scriptsDir);

        Files.writeString(scriptsDir.resolve("Movement.cs"), "public class Movement {}");

        // Inject illegal studio pollution files
        Files.writeString(projDir.resolve("memory.db"), "SQLITE_HEADER");
        Files.writeString(projDir.resolve("credentials.json"), "{\"api_key\":\"secret\"}");
        Files.writeString(projDir.resolve("autonomous-unity-agent-0.1.0.jar"), "PK_ZIP_JAR");
        Path llmCache = projDir.resolve("llm_cache");
        Files.createDirectories(llmCache);
        Files.writeString(llmCache.resolve("cache.json"), "{}");

        var report = hygieneService.performHygieneCheck(projDir.toString());
        assertFalse(report.isClean());
        assertEquals(4, report.getViolations().size());

        // Enforce throws violation
        assertThrows(IllegalStateException.class, () -> hygieneService.enforcePreRunHygiene(projDir.toString()));

        // Clean pollution
        int cleaned = hygieneService.cleanPollution(projDir.toString(), false);
        assertEquals(4, cleaned);

        // Verify project is now clean
        var postReport = hygieneService.performHygieneCheck(projDir.toString());
        assertTrue(postReport.isClean());

        // Verify user game script remained untouched!
        assertTrue(Files.exists(scriptsDir.resolve("Movement.cs")));
    }
}
