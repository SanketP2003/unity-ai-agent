package com.unityagent.phase14;

import com.unityagent.product.service.PathSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathSafetyValidatorTest {

    @TempDir
    Path tempDir;

    private PathSafetyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PathSafetyValidator();
    }

    @Test
    void testSafeSubpathValidation() throws IOException {
        Path subFile = tempDir.resolve("Assets/Scripts/Player.cs");
        Files.createDirectories(subFile.getParent());
        Files.writeString(subFile, "class Player {}");

        Path validated = validator.validateSafePath(tempDir, "Assets/Scripts/Player.cs");
        assertNotNull(validated);
        assertTrue(validated.startsWith(tempDir.toRealPath()));
        assertTrue(validator.isSafe(tempDir, "Assets/Scripts/Player.cs"));
    }

    @Test
    void testPathTraversalRejection() {
        assertThrows(SecurityException.class, () -> {
            validator.validateSafePath(tempDir, "../escape.txt");
        });

        assertThrows(SecurityException.class, () -> {
            validator.validateSafePath(tempDir, "Assets/../../escape.txt");
        });

        assertFalse(validator.isSafe(tempDir, "../escape.txt"));
    }

    @Test
    void testExternalAbsolutePathRejection() throws IOException {
        Path outsideDir = Files.createTempDirectory("outside_project");
        try {
            Path outsideFile = outsideDir.resolve("secret.txt");
            Files.writeString(outsideFile, "secret");

            assertThrows(SecurityException.class, () -> {
                validator.validateSafePath(tempDir, outsideFile.toAbsolutePath().toString());
            });
        } finally {
            Files.deleteIfExists(outsideDir.resolve("secret.txt"));
            Files.deleteIfExists(outsideDir);
        }
    }
}
