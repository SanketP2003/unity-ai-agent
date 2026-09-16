package com.unityagent.phase14;

import com.unityagent.product.service.RepositoryCleanlinessGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryCleanlinessGateTest {

    @TempDir
    Path tempDir;

    private RepositoryCleanlinessGate gate;

    @BeforeEach
    void setUp() {
        gate = new RepositoryCleanlinessGate();
    }

    @Test
    void testCleanRepositoryPassesGate() throws IOException {
        Path mockRoot = tempDir.resolve("clean_repo");
        Files.createDirectories(mockRoot.resolve("backend/src/main/java"));
        Files.writeString(mockRoot.resolve("README.md"), "# Project");
        Files.writeString(mockRoot.resolve("backend/src/main/java/Main.java"), "public class Main {}");

        RepositoryCleanlinessGate.GateResult result = gate.evaluate(mockRoot);
        assertTrue(result.passed());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void testTemporaryFileFailsGate() throws IOException {
        Path mockRoot = tempDir.resolve("dirty_repo");
        Files.createDirectories(mockRoot);
        Files.writeString(mockRoot.resolve("leftover.bak"), "old backup");

        RepositoryCleanlinessGate.GateResult result = gate.evaluate(mockRoot);
        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Prohibited temporary file")));
    }

    @Test
    void testHardcodedDeveloperPathFailsGate() throws IOException {
        Path mockRoot = tempDir.resolve("path_dirty_repo");
        Path mainFile = mockRoot.resolve("backend/src/main/java/Config.java");
        Files.createDirectories(mainFile.getParent());
        Files.writeString(mainFile, "String p = \"C:\\\\Users\\\\2019s\\\\Desktop\\\\project\";");

        RepositoryCleanlinessGate.GateResult result = gate.evaluate(mockRoot);
        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Hardcoded developer machine path")));
    }

    @Test
    void testRogueEmptyAssetsDirFailsGate() throws IOException {
        Path mockRoot = tempDir.resolve("rogue_assets_repo");
        Files.createDirectories(mockRoot.resolve("Assets"));

        RepositoryCleanlinessGate.GateResult result = gate.evaluate(mockRoot);
        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Rogue empty 'Assets' directory")));
    }

    @Test
    void testActualRepositoryPassesGate() {
        Path repoRoot = Path.of("..").toAbsolutePath().normalize();
        RepositoryCleanlinessGate.GateResult result = gate.evaluate(repoRoot);
        assertTrue(result.passed(), () -> "Violations in actual repository: " + String.join("; ", result.violations()));
    }
}
