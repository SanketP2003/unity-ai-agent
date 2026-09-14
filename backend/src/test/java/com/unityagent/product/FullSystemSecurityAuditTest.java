package com.unityagent.product;

import com.unityagent.product.service.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FullSystemSecurityAuditTest {

    private SecurityAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new SecurityAuditService();
    }

    @Test
    void testCleanWorkspacePassesAudit(@TempDir Path tempDir) throws IOException {
        Path codeDir = tempDir.resolve("Assets/Scripts");
        Files.createDirectories(codeDir);
        Files.writeString(codeDir.resolve("PlayerController.cs"), "public class PlayerController : MonoBehaviour { void Update() {} }");

        Path configDir = tempDir.resolve("Config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("application.properties"), "server.port=8080\nspring.application.name=UnityAgent\n");

        Path logDir = tempDir.resolve("Logs");
        Files.createDirectories(logDir);
        Files.writeString(logDir.resolve("app.log"), "INFO: System started successfully without errors.\n");

        SecurityAuditService.AuditReport report = auditService.auditPaths(List.of(tempDir));

        assertTrue(report.clean());
        assertEquals(0, report.violations().size());
        assertTrue(report.scannedFiles() >= 3);
        assertEquals(1, report.categoryStats().get("CODE_FILES"));
        assertEquals(1, report.categoryStats().get("CONFIG_FILES"));
        assertEquals(1, report.categoryStats().get("LOG_FILES"));
    }

    @Test
    void testSecretDetectionInCode(@TempDir Path tempDir) throws IOException {
        Path scriptFile = tempDir.resolve("DangerousScript.cs");
        Files.writeString(scriptFile, "public class Auth { string key = \"sk-1234567890abcdef1234567890abcdef\"; }");

        SecurityAuditService.AuditReport report = auditService.auditPaths(List.of(tempDir));

        assertFalse(report.clean());
        assertFalse(report.violations().isEmpty());
        assertTrue(report.violations().get(0).contains("Plaintext secret detected in DangerousScript.cs"));
    }

    @Test
    void testSecretDetectionInConfig(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("credentials.json");
        Files.writeString(configFile, "{\"api_key\": \"nvapi-abcdef1234567890abcdef1234567890\"}");

        SecurityAuditService.AuditReport report = auditService.auditPaths(List.of(tempDir));

        assertFalse(report.clean());
        assertFalse(report.violations().isEmpty());
        assertTrue(report.violations().get(0).contains("Plaintext secret detected in credentials.json"));
    }

    @Test
    void testBearerSecretDetectionInLogs(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("server.log");
        Files.writeString(logFile, "DEBUG: Outgoing request headers: Authorization: Bearer abcdef1234567890abcdef\n");

        SecurityAuditService.AuditReport report = auditService.auditPaths(List.of(tempDir));

        assertFalse(report.clean());
        assertFalse(report.violations().isEmpty());
        assertTrue(report.violations().get(0).contains("Plaintext secret detected in server.log"));
    }

    @Test
    void testSecretDetectionInDatabaseFile(@TempDir Path tempDir) throws IOException {
        Path dbFile = tempDir.resolve("app.db");
        byte[] payload = "CREATE TABLE keys; INSERT INTO keys VALUES ('sk-999999999999999999999999');".getBytes();
        Files.write(dbFile, payload);

        SecurityAuditService.AuditReport report = auditService.auditPaths(List.of(tempDir));

        assertFalse(report.clean());
        assertFalse(report.violations().isEmpty());
        assertTrue(report.violations().get(0).contains("Plaintext secret detected in binary/sqlite file app.db"));
    }
}
