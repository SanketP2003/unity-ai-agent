package com.unityagent.product;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ReleaseManagerAtomicPublishingTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private ReleaseManager releaseManager;
    private ReleaseValidator releaseValidator;
    private VersionManager versionManager;
    private ArtifactManager artifactManager;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("release_pub_test_" + UUID.randomUUID() + ".db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        releaseValidator = Mockito.mock(ReleaseValidator.class);
        versionManager = new VersionManager(db);
        artifactManager = Mockito.mock(ArtifactManager.class);

        releaseManager = new ReleaseManager(db, releaseValidator, versionManager, artifactManager);

        // SSOT projects
        repository.upsertProject(new ProjectMemory("proj-pub-1", "Publish Game", "6000.4.7f1", "WINDOWS", "URP", "1.0.0", "fp-pub-1"));
        repository.upsertProject(new ProjectMemory("proj-pub-2", "Other Game", "6000.4.7f1", "WINDOWS", "URP", "1.0.0", "fp-pub-2"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private Release createApprovedReleaseCandidate(String releaseId, String projectId, String version) throws Exception {
        Release rel = releaseManager.createReleaseCandidate(releaseId, projectId, version, ReleaseChannel.STABLE, "V" + version, "b-1", "art-1");
        // Set validation report
        ReleaseValidationReport valReport = new ReleaseValidationReport(releaseId);
        valReport.setValid(true);
        valReport.setCompilationClean(true);
        valReport.setBehaviorPassed(true);
        valReport.setCompletionGatePassed(true);
        valReport.setArtifactVerified(true);
        valReport.setHashVerified(true);
        String reportJson = objectMapper.writeValueAsString(valReport);
        rel.setValidationReportJson(reportJson);
        releaseManager.updateValidationReport(releaseId, reportJson);
        releaseManager.updateReleaseStatus(releaseId, ReleaseStatus.READY_FOR_REVIEW);

        // Approve
        return releaseManager.approveRelease(releaseId, "REVIEWER", "lead-reviewer", "LGTM for production");
    }

    @Test
    void testAtomicPublishSuccessVerifyingAllConditions() throws Exception {
        Release rel = createApprovedReleaseCandidate("rel-100", "proj-pub-1", "1.0.0");
        assertEquals(ReleaseStatus.APPROVED, rel.getStatus());

        BuildArtifact artifact = new BuildArtifact("art-1", "proj-pub-1", "rel-100", "WINDOWS", "x64", "Game.exe", 2048L, "valid-sha256", ArtifactStatus.VERIFIED, Instant.now());
        when(artifactManager.verifyArtifactIntegrity(eq("art-1"), eq(tempDir))).thenReturn(true);

        Release published = releaseManager.publishRelease("rel-100", "proj-pub-1", artifact, tempDir);

        assertNotNull(published);
        assertEquals(ReleaseStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isImmutable(), "Published release must be locked as immutable");
        assertNotNull(published.getPublishedAt());
    }

    @Test
    void testPublishAbortsWhenProjectMismatches() throws Exception {
        Release rel = createApprovedReleaseCandidate("rel-101", "proj-pub-1", "1.0.0");
        BuildArtifact artifact = new BuildArtifact("art-1", "proj-pub-1", "rel-101", "WINDOWS", "x64", "Game.exe", 2048L, "sha", ArtifactStatus.VERIFIED, Instant.now());

        assertThrows(SecurityException.class, () -> {
            releaseManager.publishRelease("rel-101", "proj-pub-2", artifact, tempDir);
        }, "Project mismatch must abort publishing transaction");
    }

    @Test
    void testPublishAbortsWhenHumanApprovalMissing() {
        Release rel = releaseManager.createReleaseCandidate("rel-102", "proj-pub-1", "1.0.0", ReleaseChannel.STABLE, "V1.0", "b1", "art1");
        // Not approved (status is DRAFT)
        BuildArtifact artifact = new BuildArtifact("art-1", "proj-pub-1", "rel-102", "WINDOWS", "x64", "Game.exe", 2048L, "sha", ArtifactStatus.VERIFIED, Instant.now());

        assertThrows(IllegalStateException.class, () -> {
            releaseManager.publishRelease("rel-102", "proj-pub-1", artifact, tempDir);
        }, "Publish must abort if not approved");
    }

    @Test
    void testPublishAbortsWhenArtifactTamperedOrCorrupted() throws Exception {
        Release rel = createApprovedReleaseCandidate("rel-103", "proj-pub-1", "1.0.0");
        BuildArtifact artifact = new BuildArtifact("art-1", "proj-pub-1", "rel-103", "WINDOWS", "x64", "Game.exe", 2048L, "sha", ArtifactStatus.VERIFIED, Instant.now());

        // Artifact verification fails
        when(artifactManager.verifyArtifactIntegrity(eq("art-1"), eq(tempDir))).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> {
            releaseManager.publishRelease("rel-103", "proj-pub-1", artifact, tempDir);
        }, "Publish must abort if artifact verification fails");
    }

    @Test
    void testPublishAbortsOnDuplicateVersionCollision() throws Exception {
        // Publish version 1.0.0 first
        Release relA = createApprovedReleaseCandidate("rel-pub-A", "proj-pub-1", "1.0.0");
        BuildArtifact artifactA = new BuildArtifact("art-1", "proj-pub-1", "rel-pub-A", "WINDOWS", "x64", "Game.exe", 2048L, "sha", ArtifactStatus.VERIFIED, Instant.now());
        when(artifactManager.verifyArtifactIntegrity(any(), any())).thenReturn(true);
        releaseManager.publishRelease("rel-pub-A", "proj-pub-1", artifactA, tempDir);

        // Now attempt to publish another release candidate with the same version 1.0.0
        try (var conn = db.getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO game_releases (release_id, project_id, version_string, channel, status, created_at, validation_report_json, approval_record_json) " +
                     "VALUES ('rel-pub-B', 'proj-pub-1', '1.0.0', 'STABLE', 'APPROVED', ?, '{}', '{\"approvedBy\":\"lead\"}')")) {
            ps.setString(1, Instant.now().toString());
            ps.executeUpdate();
        }
        BuildArtifact artifactB = new BuildArtifact("art-2", "proj-pub-1", "rel-pub-B", "WINDOWS", "x64", "Game.exe", 2048L, "sha", ArtifactStatus.VERIFIED, Instant.now());

        assertThrows(IllegalStateException.class, () -> {
            releaseManager.publishRelease("rel-pub-B", "proj-pub-1", artifactB, tempDir);
        }, "Publish must abort on duplicate version");
    }

    @Test
    void testRollbackRelease() throws Exception {
        Release rel = createApprovedReleaseCandidate("rel-roll", "proj-pub-1", "1.0.0");
        BuildArtifact artifact = new BuildArtifact("art-1", "proj-pub-1", "rel-roll", "WINDOWS", "x64", "Game.exe", 2048L, "sha", ArtifactStatus.VERIFIED, Instant.now());
        when(artifactManager.verifyArtifactIntegrity(any(), any())).thenReturn(true);
        releaseManager.publishRelease("rel-roll", "proj-pub-1", artifact, tempDir);

        Release rolled = releaseManager.rollbackRelease("rel-roll", "proj-pub-1");
        assertEquals(ReleaseStatus.ROLLED_BACK, rolled.getStatus());

        // Cross-project rollback attempt rejected
        assertThrows(SecurityException.class, () -> {
            releaseManager.rollbackRelease("rel-roll", "proj-pub-2");
        });
    }
}
