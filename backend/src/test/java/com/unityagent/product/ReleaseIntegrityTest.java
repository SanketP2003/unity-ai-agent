package com.unityagent.product;

import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.*;
import com.unityagent.product.service.ArtifactManager;
import com.unityagent.product.service.ReleaseManager;
import com.unityagent.product.service.ReleaseValidator;
import com.unityagent.product.service.VersionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("Release Cryptographic Integrity & Immutability Tests")
class ReleaseIntegrityTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private ArtifactManager artifactManager;
    private CompletionGate completionGate;
    private ObjectiveValidator objectiveValidator;
    private VersionManager versionManager;
    private ReleaseValidator releaseValidator;
    private ReleaseManager releaseManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("release_integrity_" + UUID.randomUUID() + ".db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        artifactManager = new ArtifactManager(db);
        completionGate = Mockito.mock(CompletionGate.class);
        when(completionGate.canComplete(any())).thenReturn(true);

        objectiveValidator = Mockito.mock(ObjectiveValidator.class);
        ValidationReport report = new ValidationReport("goal-1");
        report.setCompilationSuccess(true);
        report.setBehaviorTestsPassed(true);
        report.setRuntimeErrorsClean(true);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(report);

        versionManager = new VersionManager(db);
        releaseValidator = new ReleaseValidator(completionGate, objectiveValidator, artifactManager, versionManager);
        releaseManager = new ReleaseManager(db, releaseValidator, versionManager, artifactManager);

        repository.upsertProject(new ProjectMemory(
                "proj_integ", "Integ Game", "6000.4.7f1", "WINDOWS", "URP", "1.0.0", "fp-integ"
        ));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    @DisplayName("Verify release publication rejects tampered artifact file on disk")
    void testRejectsTamperedArtifact() throws Exception {
        Path artifactFile = tempDir.resolve("Builds/Game.exe");
        Files.createDirectories(artifactFile.getParent());
        Files.writeString(artifactFile, "ORIGINAL_BINARY_CONTENT");

        BuildArtifact artifact = artifactManager.registerArtifact(
                "art_orig", "proj_integ", null, "WINDOWS", "x64", tempDir, "Builds/Game.exe"
        );

        Release rel = releaseManager.createReleaseCandidate(
                "rel_integ_1", "proj_integ", "1.0.0", ReleaseChannel.STABLE, null, "Initial"
        );

        // Validate candidate -> transitions to READY_FOR_REVIEW
        ReleaseValidationReport valReport = releaseManager.validateReleaseCandidate(rel.getReleaseId(), artifact, tempDir);
        assertTrue(valReport.isValid());

        // Human approval -> transitions to APPROVED
        releaseManager.approveRelease(rel.getReleaseId(), "OWNER", "admin_user", "Approved");

        // Tamper with physical file on disk (simulate corruption)
        Files.writeString(artifactFile, "CORRUPTED_MALICIOUS_CONTENT");

        // Attempt publish -> must fail SHA-256 integrity check
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                releaseManager.publishRelease(rel.getReleaseId(), "proj_integ", artifact, tempDir));

        assertTrue(ex.getMessage().contains("SHA-256") || ex.getMessage().contains("Integrity"),
                "Exception message should mention hash or integrity mismatch: " + ex.getMessage());
    }

    @Test
    @DisplayName("Verify published release is strictly immutable")
    void testPublishedReleaseImmutability() throws Exception {
        Path artifactFile = tempDir.resolve("Builds/Game.exe");
        Files.createDirectories(artifactFile.getParent());
        Files.writeString(artifactFile, "SECURE_VERIFIED_CONTENT");

        BuildArtifact artifact = artifactManager.registerArtifact(
                "art_imm", "proj_integ", null, "WINDOWS", "x64", tempDir, "Builds/Game.exe"
        );

        Release rel = releaseManager.createReleaseCandidate(
                "rel_imm_1", "proj_integ", "1.0.0", ReleaseChannel.STABLE, null, "Initial"
        );

        releaseManager.validateReleaseCandidate(rel.getReleaseId(), artifact, tempDir);
        releaseManager.approveRelease(rel.getReleaseId(), "ADMIN", "lead_dev", "Approved");

        Release published = releaseManager.publishRelease(rel.getReleaseId(), "proj_integ", artifact, tempDir);
        assertEquals(ReleaseStatus.PUBLISHED, published.getStatus());

        // Attempt to re-publish an already published release -> must be rejected
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                releaseManager.publishRelease(rel.getReleaseId(), "proj_integ", artifact, tempDir));
        assertTrue(ex.getMessage().contains("PUBLISHED") || ex.getMessage().contains("status"),
                "Re-publishing must be blocked");

        // Attempt to approve already published release -> must be rejected
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                releaseManager.approveRelease(rel.getReleaseId(), "ADMIN", "lead_dev", "Re-approval"));
        assertTrue(ex2.getMessage().contains("READY_FOR_REVIEW") || ex2.getMessage().contains("status"));
    }
}
