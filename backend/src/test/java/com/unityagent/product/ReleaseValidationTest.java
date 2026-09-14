package com.unityagent.product;

import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.*;
import com.unityagent.product.service.ArtifactManager;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ReleaseValidationTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private CompletionGate completionGate;
    private ObjectiveValidator objectiveValidator;
    private ArtifactManager artifactManager;
    private VersionManager versionManager;
    private ReleaseValidator validator;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("release_val_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();

        completionGate = Mockito.mock(CompletionGate.class);
        objectiveValidator = Mockito.mock(ObjectiveValidator.class);
        artifactManager = Mockito.mock(ArtifactManager.class);
        versionManager = new VersionManager(db);

        validator = new ReleaseValidator(completionGate, objectiveValidator, artifactManager, versionManager);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testValidationFailsOnInvalidSemanticVersion() {
        Release release = new Release("rel-bad-ver", "proj-1", "bad.version.number", ReleaseChannel.STABLE, ReleaseStatus.DRAFT, "b1", "Initial", Instant.now());
        ReleaseValidationReport report = validator.validateRelease(release, null, tempDir);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("Invalid semantic version format")));
    }

    @Test
    void testValidationFailsWhenCompilationBroken() {
        Release release = new Release("rel-1", "proj-1", "1.0.0", ReleaseChannel.STABLE, ReleaseStatus.DRAFT, "b1", "Initial", Instant.now());

        ValidationReport gateReport = new ValidationReport("goal-test");
        gateReport.setCompilationSuccess(false);
        gateReport.setBehaviorTestsPassed(true);
        gateReport.setRuntimeErrorsClean(true);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(gateReport);

        ReleaseValidationReport report = validator.validateRelease(release, null, tempDir);
        assertFalse(report.isValid());
        assertFalse(report.isCompilationClean());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("Compilation errors")));
    }

    @Test
    void testValidationFailsWhenArtifactCorruptedOrTampered() {
        Release release = new Release("rel-1", "proj-1", "1.0.0", ReleaseChannel.STABLE, ReleaseStatus.DRAFT, "b1", "Initial", Instant.now());

        ValidationReport gateReport = new ValidationReport("goal-test");
        gateReport.setCompilationSuccess(true);
        gateReport.setBehaviorTestsPassed(true);
        gateReport.setRuntimeErrorsClean(true);
        gateReport.setTotalRequirements(1);
        gateReport.setRequiredCount(1);
        gateReport.setSatisfiedRequiredCount(1);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(gateReport);
        when(completionGate.canComplete(any())).thenReturn(true);

        BuildArtifact artifact = new BuildArtifact("art1", "proj-1", "rel-1", "WINDOWS", "x64", "Game.exe", 100L, "hash", ArtifactStatus.VERIFIED, Instant.now());
        // Integrity check reports false (tampered)
        when(artifactManager.verifyArtifactIntegrity(eq("art1"), eq(tempDir))).thenReturn(false);

        ReleaseValidationReport report = validator.validateRelease(release, artifact, tempDir);
        assertFalse(report.isValid());
        assertFalse(report.isArtifactVerified());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("TAMPER/CORRUPTION DETECTED")));
    }

    @Test
    void testValidationSucceedsWhenAllCriteriaPassed() {
        Release release = new Release("rel-1", "proj-1", "1.0.0", ReleaseChannel.STABLE, ReleaseStatus.DRAFT, "b1", "Initial", Instant.now());

        ValidationReport gateReport = new ValidationReport("goal-test");
        gateReport.setCompilationSuccess(true);
        gateReport.setBehaviorTestsPassed(true);
        gateReport.setRuntimeErrorsClean(true);
        gateReport.setTotalRequirements(1);
        gateReport.setRequiredCount(1);
        gateReport.setSatisfiedRequiredCount(1);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(gateReport);
        when(completionGate.canComplete(any())).thenReturn(true);

        BuildArtifact artifact = new BuildArtifact("art1", "proj-1", "rel-1", "WINDOWS", "x64", "Game.exe", 100L, "hash", ArtifactStatus.VERIFIED, Instant.now());
        when(artifactManager.verifyArtifactIntegrity(eq("art1"), eq(tempDir))).thenReturn(true);

        ReleaseValidationReport report = validator.validateRelease(release, artifact, tempDir);
        assertTrue(report.isValid());
        assertTrue(report.isCompilationClean());
        assertTrue(report.isBehaviorPassed());
        assertTrue(report.isCompletionGatePassed());
        assertTrue(report.isArtifactVerified());
        assertTrue(report.isHashVerified());
        assertTrue(report.isSecurityAuditPassed());
    }
}
