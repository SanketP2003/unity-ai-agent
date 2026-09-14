package com.unityagent.product;

import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.BackupManifest;
import com.unityagent.product.service.BackupManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BackupRestoreAcceptanceTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private SQLiteMemoryRepository repository;
    private CompletionGate completionGate;
    private ObjectiveValidator objectiveValidator;
    private BackupManager backupManager;
    private Path projectRoot;
    private Path targetRoot;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("backup_acceptance.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        completionGate = mock(CompletionGate.class);
        objectiveValidator = mock(ObjectiveValidator.class);
        backupManager = new BackupManager(db, completionGate, objectiveValidator);

        projectRoot = tempDir.resolve("acceptance_project");
        targetRoot = tempDir.resolve("restored_project");

        Files.createDirectories(projectRoot.resolve("Assets/Scripts"));
        Files.createDirectories(projectRoot.resolve("ProjectSettings"));

        Files.writeString(projectRoot.resolve("Assets/Scripts/GameManager.cs"), "public class GameManager {}");
        Files.writeString(projectRoot.resolve("ProjectSettings/ProjectVersion.txt"), "m_EditorVersion: 2022.3.20f1");

        repository.upsertProject(new ProjectMemory("proj-acc-1", "Acceptance Game", "2022.3.20f1", "WINDOWS", "URP", "1.0.0", "fp-1"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testEndToEndBackupAndRestoreCycle() throws Exception {
        Path backupZip = tempDir.resolve("backups/acc-backup.zip");

        BackupManifest manifest = backupManager.createBackup("bak-acc-1", "proj-acc-1", projectRoot, backupZip);
        assertNotNull(manifest);
        assertEquals("bak-acc-1", manifest.getBackupId());
        assertTrue(Files.exists(backupZip));
        assertTrue(manifest.getTotalBytes() > 0);
        assertNotNull(manifest.getChecksum());

        ValidationReport report = new ValidationReport("restore-goal");
        report.setCompilationSuccess(true);
        report.setBehaviorTestsPassed(true);
        report.setRuntimeErrorsClean(true);

        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(report);
        when(completionGate.canComplete(any())).thenReturn(true);

        BackupManager.RestoreResult res = backupManager.restoreBackup("bak-acc-1", "proj-acc-1", targetRoot);
        assertTrue(res.isSuccess());
        assertEquals("SUCCESS", res.getStatus());
        assertTrue(Files.exists(targetRoot.resolve("Assets/Scripts/GameManager.cs")));
    }

    @Test
    void testZipSlipDefenseInBackupRestore() throws Exception {
        Path maliciousZip = tempDir.resolve("malicious_slip.zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(maliciousZip.toFile()))) {
            ZipEntry entry = new ZipEntry("../../../evil.txt");
            zos.putNextEntry(entry);
            zos.write("malicious payload".getBytes());
            zos.closeEntry();
        }

        String checksum = com.unityagent.agent.security.ScriptSafetyValidator.computeHash(Files.readString(maliciousZip, java.nio.charset.StandardCharsets.ISO_8859_1));
        BackupManifest fakeManifest = new BackupManifest("bak-slip", "proj-acc-1", "2022.3.20f1", 4, checksum, 1, Files.size(maliciousZip), java.time.Instant.now());

        // Register fake backup in DB pointing to maliciousZip
        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO backups (backup_id, project_id, archive_path, manifest_json, checksum, created_at) VALUES (?, ?, ?, ?, ?, ?)"
             )) {
            stmt.setString(1, "bak-slip");
            stmt.setString(2, "proj-acc-1");
            stmt.setString(3, maliciousZip.toAbsolutePath().toString());
            stmt.setString(4, new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(fakeManifest));
            stmt.setString(5, checksum);
            stmt.setString(6, java.time.Instant.now().toString());
            stmt.executeUpdate();
        }

        BackupManager.RestoreResult res = backupManager.restoreBackup("bak-slip", "proj-acc-1", targetRoot);
        assertFalse(res.isSuccess());
        // Blocked either by traversal detection or checksum mismatch
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    }
}
