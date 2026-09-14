package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.Release;
import com.unityagent.product.model.ReleaseChannel;
import com.unityagent.product.model.ReleaseStatus;
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

import static org.junit.jupiter.api.Assertions.*;

class ReleaseApprovalTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private ReleaseManager releaseManager;
    private ReleaseValidator releaseValidator;
    private VersionManager versionManager;
    private ArtifactManager artifactManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("release_appr_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        releaseValidator = Mockito.mock(ReleaseValidator.class);
        versionManager = new VersionManager(db);
        artifactManager = Mockito.mock(ArtifactManager.class);

        releaseManager = new ReleaseManager(db, releaseValidator, versionManager, artifactManager);

        // SSOT project
        repository.upsertProject(new ProjectMemory("proj-appr-1", "Appr Game", "6000.4.7f1", "WINDOWS", "URP", "1.0.0", "fp-appr-1"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testRejectionOfAgentOrLLMSelfApproval() {
        Release candidate = releaseManager.createReleaseCandidate("rel-appr-1", "proj-appr-1", "1.0.0", ReleaseChannel.STABLE, "Initial", "b1", "art1");
        releaseManager.updateReleaseStatus("rel-appr-1", ReleaseStatus.READY_FOR_REVIEW);

        // Agent self-approval must be rejected
        assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-appr-1", "REVIEWER", "agent", "Automated self-approval");
        }, "Agent self-approval must be rejected");

        // LLM self-approval must be rejected
        assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-appr-1", "ADMIN", "llm", "LLM self-approval");
        }, "LLM self-approval must be rejected");

        // Autonomous agent prefix rejected
        assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-appr-1", "OWNER", "autonomous-bot", "Bot approval");
        }, "Autonomous bot approval must be rejected");
    }

    @Test
    void testRejectionOfUnauthorizedRoles() {
        releaseManager.createReleaseCandidate("rel-appr-2", "proj-appr-1", "1.0.0", ReleaseChannel.STABLE, "Initial", "b1", "art1");
        releaseManager.updateReleaseStatus("rel-appr-2", ReleaseStatus.READY_FOR_REVIEW);

        // DEVELOPER and VIEWER roles cannot approve releases
        assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-appr-2", "DEVELOPER", "user-john", "Dev approval");
        });

        assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-appr-2", "VIEWER", "user-sarah", "Viewer approval");
        });
    }

    @Test
    void testRejectionWhenReleaseNotInReadyForReviewStatus() {
        // Release is currently DRAFT
        releaseManager.createReleaseCandidate("rel-appr-3", "proj-appr-1", "1.0.0", ReleaseChannel.STABLE, "Initial", "b1", "art1");

        assertThrows(IllegalStateException.class, () -> {
            releaseManager.approveRelease("rel-appr-3", "REVIEWER", "lead-reviewer", "Premature approval");
        }, "Cannot approve release that is still in DRAFT status");
    }

    @Test
    void testSuccessfulHumanLeadApproval() {
        releaseManager.createReleaseCandidate("rel-appr-4", "proj-appr-1", "1.0.0", ReleaseChannel.STABLE, "Initial", "b1", "art1");
        releaseManager.updateReleaseStatus("rel-appr-4", ReleaseStatus.READY_FOR_REVIEW);

        Release approved = releaseManager.approveRelease("rel-appr-4", "REVIEWER", "lead-reviewer", "Signed off by QA Lead");
        assertNotNull(approved);
        assertEquals(ReleaseStatus.APPROVED, approved.getStatus());
        assertNotNull(approved.getApprovalRecordJson());
        assertTrue(approved.getApprovalRecordJson().contains("lead-reviewer"));
        assertTrue(approved.getApprovalRecordJson().contains("REVIEWER"));
    }
}
