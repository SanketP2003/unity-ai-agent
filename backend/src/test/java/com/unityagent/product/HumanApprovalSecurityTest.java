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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HumanApprovalSecurityTest {

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
        File dbFile = tempDir.resolve("human_approval_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        versionManager = new VersionManager(db);
        artifactManager = new ArtifactManager(db);
        releaseValidator = mock(ReleaseValidator.class);
        releaseManager = new ReleaseManager(db, releaseValidator, versionManager, artifactManager);

        repository.upsertProject(new ProjectMemory("proj-approval", "Approval Test Game", "2022.3.20f1", "WINDOWS", "URP", "1.0.0", "fp-approval"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private Release setupReadyForReviewRelease(String releaseId, String version) throws IOException {
        Path buildDir = tempDir.resolve("Builds/Windows");
        Files.createDirectories(buildDir);
        Path exePath = buildDir.resolve("Game_" + releaseId + ".exe");
        Files.writeString(exePath, "GENUINE_GAME_EXE_CONTENT_" + releaseId);

        Release release = releaseManager.createReleaseCandidate(releaseId, "proj-approval", version,
                ReleaseChannel.STABLE, "build-" + releaseId, "Release " + version);

        BuildArtifact artifact = artifactManager.registerArtifact("art-" + releaseId, "proj-approval", releaseId,
                "StandaloneWindows64", "x86_64", tempDir, "Builds/Windows/Game_" + releaseId + ".exe");

        ReleaseValidationReport report = new ReleaseValidationReport(releaseId);
        report.setValid(true);
        when(releaseValidator.validateRelease(any(), any(), any())).thenReturn(report);

        releaseManager.validateReleaseCandidate(releaseId, artifact, tempDir);
        return release;
    }

    @Test
    void testAgentOrLlmSelfApprovalStrictlyForbidden() throws IOException {
        setupReadyForReviewRelease("rel-agent-1", "1.0.0");

        // Attempt 1: agent
        SecurityException ex1 = assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-agent-1", "ADMIN", "agent", "Self-approving release");
        });
        assertTrue(ex1.getMessage().contains("Automated agents and LLMs are strictly forbidden"));

        // Attempt 2: llm
        SecurityException ex2 = assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-agent-1", "ADMIN", "llm", "Self-approving release");
        });
        assertTrue(ex2.getMessage().contains("Automated agents and LLMs are strictly forbidden"));

        // Attempt 3: autonomous-subagent
        SecurityException ex3 = assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-agent-1", "ADMIN", "autonomous-builder", "Self-approving release");
        });
        assertTrue(ex3.getMessage().contains("Automated agents and LLMs are strictly forbidden"));

        // Attempt 4: null reviewerId
        assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-agent-1", "ADMIN", null, "Self-approving release");
        });
    }

    @Test
    void testUnauthorizedRolesCannotApprove() throws IOException {
        setupReadyForReviewRelease("rel-role-1", "1.1.0");

        // Attempt with VIEWER role
        SecurityException ex1 = assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-role-1", "VIEWER", "human-viewer-dave", "Looks good");
        });
        assertTrue(ex1.getMessage().contains("does not have permission to approve releases"));

        // Attempt with DEVELOPER role
        SecurityException ex2 = assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-role-1", "DEVELOPER", "human-dev-carol", "Approved by dev");
        });
        assertTrue(ex2.getMessage().contains("does not have permission to approve releases"));
    }

    @Test
    void testPublishingWithoutApprovalIsHardBlocked() throws IOException {
        setupReadyForReviewRelease("rel-unapproved-1", "1.2.0");

        BuildArtifact artifact = artifactManager.getArtifactRaw("art-rel-unapproved-1").orElseThrow();

        // Release is in READY_FOR_REVIEW state (NOT APPROVED)
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            releaseManager.publishRelease("rel-unapproved-1", "proj-approval", artifact, tempDir);
        });
        assertTrue(ex.getMessage().contains("Release must be in APPROVED state"));
    }

    @Test
    void testLegitimateHumanLeadApprovalSucceeds() throws IOException {
        setupReadyForReviewRelease("rel-human-1", "1.3.0");
        BuildArtifact artifact = artifactManager.getArtifactRaw("art-rel-human-1").orElseThrow();

        // Valid REVIEWER approval
        Release approved = releaseManager.approveRelease("rel-human-1", "REVIEWER", "human-qa-lead-sarah", "Sign-off from QA");
        assertEquals(ReleaseStatus.APPROVED, approved.getStatus());

        Release published = releaseManager.publishRelease("rel-human-1", "proj-approval", artifact, tempDir);
        assertEquals(ReleaseStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isImmutable());
    }
}
