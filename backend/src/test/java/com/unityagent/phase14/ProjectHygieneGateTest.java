package com.unityagent.phase14;

import com.unityagent.product.service.ProjectHygieneGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectHygieneGateTest {

    @TempDir
    Path tempDir;

    private ProjectHygieneGate gate;

    @BeforeEach
    void setUp() {
        gate = new ProjectHygieneGate();
    }

    @Test
    void testCleanUnityProjectPassesGate() throws IOException {
        Path proj = tempDir.resolve("CleanUnityProject");
        Files.createDirectories(proj.resolve("Assets/Scripts"));
        Files.createDirectories(proj.resolve("Packages"));
        Files.createDirectories(proj.resolve("ProjectSettings"));
        Files.writeString(proj.resolve("Packages/manifest.json"), "{\"dependencies\": {}}");

        ProjectHygieneGate.GateResult result = gate.evaluate(proj);
        assertTrue(result.passed());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void testProjectWithStudioPollutionFailsGate() throws IOException {
        Path proj = tempDir.resolve("PollutedUnityProject");
        Files.createDirectories(proj.resolve("Assets"));
        Files.createDirectories(proj.resolve("Packages"));
        Files.writeString(proj.resolve("Packages/manifest.json"), "{\"dependencies\": {}}");

        // Forbidden studio database file in Unity project
        Files.writeString(proj.resolve("Assets/studio_memory.db"), "fake sqlite data");

        ProjectHygieneGate.GateResult result = gate.evaluate(proj);
        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Forbidden studio pollution")));
    }

    @Test
    void testProjectMissingManifestFailsGate() throws IOException {
        Path proj = tempDir.resolve("BrokenUnityProject");
        Files.createDirectories(proj.resolve("Assets"));
        Files.createDirectories(proj.resolve("Packages"));
        // manifest.json is missing

        ProjectHygieneGate.GateResult result = gate.evaluate(proj);
        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Missing Packages/manifest.json")));
    }
}
