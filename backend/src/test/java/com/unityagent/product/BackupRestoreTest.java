package com.unityagent.product;

import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.BackupManifest;
import com.unityagent.product.service.BackupManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class BackupRestoreTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private CompletionGate completionGate;
    private ObjectiveValidator objectiveValidator;
    private BackupManager backupManager;
    private Path projectRoot;
    private Path targetRoot;
    private Path backupZip;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("backup_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        completionGate = Mockito.mock(CompletionGate.class);
        objectiveValidator = Mockito.mock(ObjectiveValidator.class);
        backupManager = new BackupManager(db, completionGate, objectiveValidator);

        projectRoot = tempDir.resolve("source_project");
        targetRoot = tempDir.resolve("restored_project");
        backupZip = tempDir.resolve("backups/backup-1.zip");

        Files.createDirectories(projectRoot.resolve("Assets/Scripts"));
        Files.createDirectories(projectRoot.resolve("ProjectSettings"));

        // Add regular code and a secret file
        Files.writeString(projectRoot.resolve("Assets/Scripts/Player.cs"), "public class Player {}");
        Files.writeString(projectRoot.resolve("ProjectSettings/ProjectSettings.asset"), "productName: MyGame");
        Files.writeString(projectRoot.resolve("Assets/api.secret"), "SK-SECRET-TOKEN-DO-NOT-EXPORT");

        // SSOT project
        repository.upsertProject(new ProjectMemory("proj-bak-1", "Backup Game", "6000.4.7f1", "WINDOWS", "URP", "1.0.0", "fp-bak-1"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testBackupCreationExcludesSecretsAndGeneratesManifest() throws Exception {
        BackupManifest manifest = backupManager.createBackup("bak-1", "proj-bak-1", projectRoot, backupZip);
        assertNotNull(manifest);
        assertEquals("bak-1", manifest.getBackupId());
        assertEquals("proj-bak-1", manifest.getProjectId());
        assertNotNull(manifest.getChecksum());
        assertTrue(Files.exists(backupZip));

        // Verify that api.secret was excluded from the zip
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(backupZip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                assertFalse(entry.getName().contains(".secret"), "Secrets must be excluded from backup");
            }
        }
    }

    @Test
    void testRestoreVerificationPipelineSuccess() throws Exception {
        backupManager.createBackup("bak-2", "proj-bak-1", projectRoot, backupZip);

        ValidationReport gateReport = new ValidationReport("goal-bak");
        gateReport.setCompilationSuccess(true);
        gateReport.setBehaviorTestsPassed(true);
        gateReport.setRuntimeErrorsClean(true);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(gateReport);
        when(completionGate.canComplete(any())).thenReturn(true);

        BackupManager.RestoreResult res = backupManager.restoreBackup("bak-2", "proj-bak-1", targetRoot);
        assertTrue(res.isSuccess());
        assertEquals("SUCCESS", res.getStatus());
        assertTrue(Files.exists(targetRoot.resolve("Assets/Scripts/Player.cs")));
    }

    @Test
    void testRestoreFailsWhenBackupArchiveCorrupted() throws Exception {
        backupManager.createBackup("bak-3", "proj-bak-1", projectRoot, backupZip);

        // Corrupt archive on disk
        Files.writeString(backupZip, "CORRUPTED_BYTES_APPENDED", java.nio.file.StandardOpenOption.APPEND);

        BackupManager.RestoreResult res = backupManager.restoreBackup("bak-3", "proj-bak-1", targetRoot);
        assertFalse(res.isSuccess());
        assertEquals("CHECKSUM_MISMATCH", res.getStatus());
    }

    @Test
    void testRestoreFailsOnCrossProjectAttempt() {
        backupManager.createBackup("bak-4", "proj-bak-1", projectRoot, backupZip);

        BackupManager.RestoreResult res = backupManager.restoreBackup("bak-4", "proj-other", targetRoot);
        assertFalse(res.isSuccess());
        assertEquals("PROJECT_MISMATCH", res.getStatus());
    }
}
