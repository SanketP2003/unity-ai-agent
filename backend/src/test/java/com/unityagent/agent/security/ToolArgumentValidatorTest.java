package com.unityagent.agent.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolArgumentValidatorTest {

    private ToolArgumentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ToolArgumentValidator();
    }

    @Test
    void testValidArgumentsPass() {
        Map<String, Object> params = Map.of(
                "name", "Player",
                "scenePath", "Assets/Scenes/MainScene.unity",
                "primitiveType", "Cube"
        );
        ToolArgumentValidator.ValidationResult result = validator.validate("create_primitive", params);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testPathTraversalRejected() {
        Map<String, Object> params = Map.of(
                "assetPath", "Assets/../../etc/passwd"
        );
        ToolArgumentValidator.ValidationResult result = validator.validate("load_asset", params);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("path traversal"));
    }

    @Test
    void testDriveLettersRejected() {
        Map<String, Object> params = Map.of(
                "filePath", "C:/Windows/System32/cmd.exe"
        );
        ToolArgumentValidator.ValidationResult result = validator.validate("read_file", params);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("absolute drive letter"));
    }

    @Test
    void testUncPathRejected() {
        Map<String, Object> params = Map.of(
                "scenePath", "//192.168.1.100/share/scene.unity"
        );
        ToolArgumentValidator.ValidationResult result = validator.validate("open_scene", params);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("UNC"));
    }

    @Test
    void testEnvVarEscapeRejected() {
        Map<String, Object> params = Map.of(
                "path", "%APPDATA%/secret.txt"
        );
        ToolArgumentValidator.ValidationResult result = validator.validate("save_file", params);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("environment variable escape"));
    }

    @Test
    void testNullByteRejected() {
        Map<String, Object> params = Map.of(
                "name", "BadObject\0Null"
        );
        ToolArgumentValidator.ValidationResult result = validator.validate("create_gameobject", params);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("null byte"));
    }

    @Test
    void testOversizedPayloadRejected() {
        Map<String, Object> params = Map.of(
                "description", "X".repeat(70000)
        );
        ToolArgumentValidator.ValidationResult result = validator.validate("set_property", params);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("exceeds maximum allowed length"));
    }
}
