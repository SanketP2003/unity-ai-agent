package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.*;
import com.unityagent.product.service.ArtifactManager;
import com.unityagent.product.service.ReleaseManager;
import com.unityagent.product.service.ReleaseValidator;
import com.unityagent.product.service.VersionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReleaseStateMachineAndTamperingTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private SQLiteMemoryRepository repository;
    private VersionManager versionManager;
    private ArtifactManager artifactManager;
    private ReleaseValidator releaseValidator;
    private ReleaseManager releaseManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("release_tamper_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        versionManager = new VersionManager(db);
        artifactManager = new ArtifactManager(db);
        releaseValidator = mock(ReleaseValidator.class);
        releaseManager = new ReleaseManager(db, releaseValidator, versionManager, artifactManager);

        repository.upsertProject(new ProjectMemory("proj-tamper", "Tamper Test Game", "2022.3.20f1", "WINDOWS", "URP", "1.0.0", "fp-tamper"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testCompleteReleaseLifecycleAndStateTransitions() throws IOException {
        Path buildDir = tempDir.resolve("Builds/Windows");
        Files.createDirectories(buildDir);
        Path exePath = buildDir.resolve("Game.exe");
        Files.writeString(exePath, "GENUINE_GAME_EXECUTABLE_CONTENT");

        // 1. DRAFT candidate
        Release release = releaseManager.createReleaseCandidate("rel-state-1", "proj-tamper", "1.0.0",
                ReleaseChannel.STABLE, "build-1", "Initial Release Candidate");
        assertEquals(ReleaseStatus.DRAFT, release.getStatus());

        BuildArtifact artifact = artifactManager.registerArtifact("art-state-1", "proj-tamper", "rel-state-1",
                "StandaloneWindows64", "x86_64", tempDir, "Builds/Windows/Game.exe");
        assertEquals(ArtifactStatus.VERIFIED, artifact.getStatus());

        // 2. VALIDATING -> READY_FOR_REVIEW
        ReleaseValidationReport report = new ReleaseValidationReport("rel-state-1");
        report.setValid(true);
        when(releaseValidator.validateRelease(any(), any(), any())).thenReturn(report);

        ReleaseValidationReport valRes = releaseManager.validateReleaseCandidate("rel-state-1", artifact, tempDir);
        assertTrue(valRes.isValid());

        Release reviewed = releaseManager.getRelease("rel-state-1").orElseThrow();
        assertEquals(ReleaseStatus.READY_FOR_REVIEW, reviewed.getStatus());

        // 3. READY_FOR_REVIEW -> APPROVED
        Release approved = releaseManager.approveRelease("rel-state-1", "ADMIN", "human-lead-alice", "Approved for release");
        assertEquals(ReleaseStatus.APPROVED, approved.getStatus());

        // 4. APPROVED -> PUBLISHING -> PUBLISHED (atomic publish)
        Release published = releaseManager.publishRelease("rel-state-1", "proj-tamper", artifact, tempDir);
        assertEquals(ReleaseStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isImmutable());

        // Immutability: direct status mutation throws exception
        assertThrows(IllegalStateException.class, () -> published.setStatus(ReleaseStatus.DRAFT));
        assertThrows(IllegalStateException.class, () -> published.setVersionString("2.0.0"));

        // 5. Rollback: PUBLISHED -> ROLLED_BACK -> ARCHIVED
        Release rolledBack = releaseManager.rollbackRelease("rel-state-1", "proj-tamper");
        assertEquals(ReleaseStatus.ROLLED_BACK, rolledBack.getStatus());

        Release archived = releaseManager.archiveRelease("rel-state-1", "proj-tamper");
        assertEquals(ReleaseStatus.ARCHIVED, archived.getStatus());
    }

    @Test
    void testDeliberatePostPublishTamperingAttack() throws IOException {
        Path buildDir = tempDir.resolve("Builds/Windows");
        Files.createDirectories(buildDir);
        Path exePath = buildDir.resolve("Game.exe");
        Files.writeString(exePath, "ORIGINAL_SAFE_BINARY_PAYLOAD_12345");

        // Register valid artifact
        BuildArtifact artifact = artifactManager.registerArtifact("art-attack-1", "proj-tamper", "rel-attack-1",
                "StandaloneWindows64", "x86_64", tempDir, "Builds/Windows/Game.exe");
        assertEquals(ArtifactStatus.VERIFIED, artifact.getStatus());
        String originalHash = artifact.getSha256Checksum();

        // Create, validate, approve, and publish release
        Release release = releaseManager.createReleaseCandidate("rel-attack-1", "proj-tamper", "1.0.0",
                ReleaseChannel.STABLE, "build-attack-1", "Production Release");
        ReleaseValidationReport report = new ReleaseValidationReport("rel-attack-1");
        report.setValid(true);
        when(releaseValidator.validateRelease(any(), any(), any())).thenReturn(report);
        releaseManager.validateReleaseCandidate("rel-attack-1", artifact, tempDir);
        releaseManager.approveRelease("rel-attack-1", "ADMIN", "human-lead-bob", "Security signed");

        Release published = releaseManager.publishRelease("rel-attack-1", "proj-tamper", artifact, tempDir);
        assertEquals(ReleaseStatus.PUBLISHED, published.getStatus());

        // Verify initial integrity before attack
        assertTrue(artifactManager.verifyArtifactIntegrity("art-attack-1", tempDir));

        // DELIBERATE ATTACK: Modify binary bytes on disk post-publication!
        Files.writeString(exePath, "MALICIOUS_TAMPERED_INJECTED_CODE", StandardOpenOption.APPEND);

        // System detects tampering!
        boolean intactAfterAttack = artifactManager.verifyArtifactIntegrity("art-attack-1", tempDir);
        assertFalse(intactAfterAttack, "Integrity check must fail when physical file on disk is altered");

        // Database status must transition to CORRUPTED
        BuildArtifact attackedArt = artifactManager.getArtifactRaw("art-attack-1").orElseThrow();
        assertEquals(ArtifactStatus.CORRUPTED, attackedArt.getStatus(), "Artifact status must be marked CORRUPTED");
        assertEquals(originalHash, attackedArt.getSha256Checksum(), "Recorded hash must remain immutable to prevent silent pointer rewrite");

        // Attempting to publish another release candidate with this tampered artifact must be hard-blocked
        Release rel2 = releaseManager.createReleaseCandidate("rel-attack-2", "proj-tamper", "1.0.1",
                ReleaseChannel.STABLE, "build-attack-2", "Followup");
        releaseManager.validateReleaseCandidate("rel-attack-2", attackedArt, tempDir);
        releaseManager.approveRelease("rel-attack-2", "ADMIN", "human-lead-bob", "Signed");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            releaseManager.publishRelease("rel-attack-2", "proj-tamper", attackedArt, tempDir);
        });
        assertTrue(ex.getMessage().contains("CORRUPTED"));
    }
}
