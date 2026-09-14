package com.unityagent.product;

import com.unityagent.agent.security.ScriptSafetyValidator;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.*;
import com.unityagent.product.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Master End-to-End Product Acceptance: Clean Install -> Autonomy -> Tampering Defense")
class MasterEndToEndAcceptanceTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private SQLiteMemoryRepository repository;
    private SystemRequirementsService systemRequirementsService;
    private ProductInstallationService installationService;
    private SecurityAuditService securityAuditService;
    private WorkspaceManager workspaceManager;
    private ProjectTemplateManager templateManager;
    private VersionManager versionManager;
    private ArtifactManager artifactManager;
    private ReleaseValidator releaseValidator;
    private ReleaseManager releaseManager;
    private ProjectPackageService projectPackageService;
    private ScriptSafetyValidator scriptSafetyValidator;

    private Path projectRoot;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("master_e2e.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        systemRequirementsService = new SystemRequirementsService();
        installationService = new ProductInstallationService();
        securityAuditService = new SecurityAuditService();
        workspaceManager = new WorkspaceManager(db);
        templateManager = new ProjectTemplateManager(db);
        templateManager.registerBuiltinTemplates();
        versionManager = new VersionManager(db);
        artifactManager = new ArtifactManager(db);
        projectPackageService = new ProjectPackageService(db);
        scriptSafetyValidator = new ScriptSafetyValidator();

        releaseValidator = mock(ReleaseValidator.class);
        releaseManager = new ReleaseManager(db, releaseValidator, versionManager, artifactManager);

        projectRoot = tempDir.resolve("MasterProject");
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    @DisplayName("Master Journey: Clean Install -> Autonomy -> Artifact Verification -> Tampering Attack Defense")
    void testMasterAutonomousProductAcceptanceJourney() throws Exception {
        String projectId = "proj_master_game";
        repository.upsertProject(new ProjectMemory(projectId, "Master Sovereign Game", "2022.3.20f1", "WINDOWS", "URP", "1.0.0", "fp-master"));

        Files.createDirectories(projectRoot.resolve("Assets/Scripts"));
        Files.createDirectories(projectRoot.resolve("Builds/Windows"));

        // 1. Environment Detection & System Requirements
        SystemRequirementsService.RequirementsReport reqs = systemRequirementsService.checkRequirements(tempDir);
        assertTrue(reqs.satisfied(), "System requirements (Java 21, memory, disk, SQLite) must be satisfied");

        ProductInstallationService.InstallResult installRes = installationService.installOrRepair(tempDir.resolve("StudioInstall"));
        assertTrue(installRes.success(), "Clean product install scaffold must succeed");

        // 2. Security Audit (Zero-Secret Pre-Check)
        SecurityAuditService.AuditReport auditPre = securityAuditService.auditPaths(List.of(tempDir));
        assertTrue(auditPre.clean(), "Workspace must be clean of credentials before run begins");

        // 3. Project Scaffolding
        ProjectTemplate template = templateManager.getTemplate("tpl-2d-game").orElseThrow();
        Workspace ws = workspaceManager.createWorkspace("ws_master", "Master Workspace", projectRoot.toString());
        assertNotNull(ws);

        // 4. Safe Script Synthesis
        String controllerCode = """
                using UnityEngine;
                public class SovereignController : MonoBehaviour {
                    public float speed = 10f;
                    void Update() {
                        transform.Translate(Vector3.right * speed * Time.deltaTime);
                    }
                }
                """;
        String scriptRelPath = "Assets/Scripts/SovereignController.cs";
        ScriptSafetyValidator.ValidationResult safety = scriptSafetyValidator.validate(scriptRelPath, controllerCode);
        assertTrue(safety.isValid());
        Files.writeString(projectRoot.resolve(scriptRelPath), controllerCode);

        // 5. Authoritative CompletionGate Verification
        ValidationReport valReport = new ValidationReport("goal_master");
        valReport.setTotalRequirements(6);
        valReport.setRequiredCount(6);
        valReport.setSatisfiedRequiredCount(6);
        valReport.setCompilationSuccess(true);
        valReport.setRuntimeErrorsClean(true);
        valReport.setBehaviorTestsPassed(true);
        valReport.setSummary("Master build verified across all dimensions.");
        assertTrue(CompletionGate.evaluate(valReport));

        // 6. Binary Creation & Physical Artifact Registration
        Path exePath = projectRoot.resolve("Builds/Windows/SovereignGame.exe");
        Files.write(exePath, "GENUINE_SOVEREIGN_EXECUTABLE_BINARY_STREAM".getBytes());
        assertTrue(Files.exists(exePath));

        String artifactId = "art_master_01";
        String releaseId = "rel_master_v1";
        BuildArtifact artifact = artifactManager.registerArtifact(
                artifactId,
                projectId,
                releaseId,
                "StandaloneWindows64",
                "x86_64",
                projectRoot,
                "Builds/Windows/SovereignGame.exe"
        );
        assertEquals(ArtifactStatus.VERIFIED, artifact.getStatus());
        String initialChecksum = artifact.getSha256Checksum();
        assertNotNull(initialChecksum);

        // 7. Release Candidate Creation & Validation
        Release release = releaseManager.createReleaseCandidate(
                releaseId,
                projectId,
                "1.0.0",
                ReleaseChannel.STABLE,
                "build_master_01",
                "Master Production Release Candidate"
        );
        ReleaseValidationReport relValReport = new ReleaseValidationReport(releaseId);
        relValReport.setValid(true);
        when(releaseValidator.validateRelease(any(), any(), any())).thenReturn(relValReport);

        ReleaseValidationReport validated = releaseManager.validateReleaseCandidate(releaseId, artifact, projectRoot);
        assertTrue(validated.isValid());

        // 8. Human Approval Security Barrier
        assertThrows(SecurityException.class, () ->
                releaseManager.approveRelease(releaseId, "REVIEWER", "autonomous-agent-007", "Self approval")
        );
        Release approved = releaseManager.approveRelease(releaseId, "ADMIN", "director_of_engineering", "Passed full audit");
        assertEquals(ReleaseStatus.APPROVED, approved.getStatus());

        // 9. Atomic Publishing & Immutability Enforcement
        Release published = releaseManager.publishRelease(releaseId, projectId, artifact, projectRoot);
        assertEquals(ReleaseStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isImmutable());

        // 10. Deliberate Post-Publish Tampering Attack
        // An attacker modifies bytes in the published binary on disk
        Files.writeString(exePath, "\nMALICIOUS_PAYLOAD_BYTE_INJECTION", StandardOpenOption.APPEND);

        // Integrity verification catches the modification
        boolean integrityCheck = artifactManager.verifyArtifactIntegrity(artifactId, projectRoot);
        assertFalse(integrityCheck, "Tampered binary MUST fail SHA-256 integrity verification");

        // The artifact status is automatically transitioned to CORRUPTED
        BuildArtifact corruptedArtifact = artifactManager.getArtifact(projectId, artifactId).orElseThrow();
        assertEquals(ArtifactStatus.CORRUPTED, corruptedArtifact.getStatus(), "Tampered artifact MUST be marked CORRUPTED in database");

        // The original published release record preserves historical hash evidence and remains immutable
        Release sealedRelease = releaseManager.getRelease(releaseId).orElseThrow();
        assertEquals(ReleaseStatus.PUBLISHED, sealedRelease.getStatus());

        // 11. Security Audit Confirms Zero-Secret Post-Run Invariant
        SecurityAuditService.AuditReport auditPost = securityAuditService.auditPaths(List.of(tempDir));
        assertTrue(auditPost.clean(), "Zero secrets permitted across entire workspace post-run");
    }
}
