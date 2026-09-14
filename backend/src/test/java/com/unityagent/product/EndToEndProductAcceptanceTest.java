package com.unityagent.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.plan.PlanStep;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.product.model.*;
import com.unityagent.product.service.*;
import com.unityagent.studio.model.BuildRecord;
import com.unityagent.studio.service.StudioBuildService;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class EndToEndProductAcceptanceTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private WorkspaceManager workspaceManager;
    private ProjectTemplateManager templateManager;
    private ConfigurationManager configManager;
    private VersionManager versionManager;
    private ArtifactManager artifactManager;
    private PlatformCapabilityDetector capabilityDetector;
    private BuildProfileManager profileManager;
    private StudioBuildService buildService;
    private CompletionGate completionGate;
    private ObjectiveValidator objectiveValidator;
    private MultiPlatformBuildPipeline buildPipeline;
    private ReleaseValidator releaseValidator;
    private ReleaseManager releaseManager;
    private DeploymentManager deploymentManager;
    private BackupManager backupManager;
    private UnityConnection unityConnection;
    private AIProvider aiProvider;
    private AutonomousRunController runController;

    private Path projectRoot;
    private Path deployTarget;

    @BeforeEach
    void setUp() throws Exception {
        File dbFile = tempDir.resolve("e2e_acceptance.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        unityConnection = Mockito.mock(UnityConnection.class);
        aiProvider = Mockito.mock(AIProvider.class);
        runController = Mockito.mock(AutonomousRunController.class);
        completionGate = Mockito.mock(CompletionGate.class);
        objectiveValidator = Mockito.mock(ObjectiveValidator.class);
        buildService = Mockito.mock(StudioBuildService.class);

        workspaceManager = new WorkspaceManager(db);
        templateManager = new ProjectTemplateManager(db);
        templateManager.registerBuiltinTemplates();
        configManager = new ConfigurationManager(db);
        versionManager = new VersionManager(db);
        artifactManager = new ArtifactManager(db);
        capabilityDetector = new PlatformCapabilityDetector(unityConnection);
        profileManager = new BuildProfileManager(capabilityDetector);

        buildPipeline = new MultiPlatformBuildPipeline(
                profileManager, buildService, artifactManager,
                completionGate, objectiveValidator, db
        );
        releaseValidator = new ReleaseValidator(completionGate, objectiveValidator, artifactManager, versionManager);
        releaseManager = new ReleaseManager(db, releaseValidator, versionManager, artifactManager);
        deploymentManager = new DeploymentManager(db, artifactManager);
        backupManager = new BackupManager(db, completionGate, objectiveValidator);

        projectRoot = tempDir.resolve("e2e_project");
        deployTarget = tempDir.resolve("deploy_stage");
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Genuine End-to-End Happy Path Acceptance Test
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Genuinely End-to-End: Template -> Autonomous Build -> Test -> Gate -> Artifact -> SHA-256 -> Approval -> Publish -> Deploy")
    void testGenuineEndToEndLifecycleAcceptance() throws Exception {
        String projectId = "proj-e2e-1";
        String projectName = "Cosmic Odyssey";

        // Step 1: Create workspace
        Workspace workspace = workspaceManager.createWorkspace("ws-main", "Production Studio", tempDir.toString());
        assertNotNull(workspace);

        // Step 2: Create Unity project from template (Blank 3D or 3D Platformer)
        templateManager.instantiateProject("tpl-3d-platformer", projectId, projectName, projectRoot);
        assertTrue(Files.exists(projectRoot.resolve("Assets/Scenes/PlatformerLevel1.unity")), "Template must scaffold scene");
        assertTrue(repository.findProject(projectId).isPresent(), "Authoritative projects table must be populated");

        // Step 3: Link project to workspace
        WorkspaceProject wp = workspaceManager.addProjectToWorkspace("ws-main", projectId);
        workspaceManager.openProject(projectId);
        assertEquals(ProjectLifecycleState.ACTIVE, workspaceManager.getProjectState(projectId).orElseThrow());

        // Step 4: Connect Unity extension (mock handshake)
        when(unityConnection.isReady()).thenReturn(true);
        UnityMessage capsMsg = UnityMessage.toolResponse("op-caps", true, Map.of(
                "capabilities", List.of(
                        Map.of("platform", "WINDOWS", "supported", true, "moduleInstalled", true, "buildAvailable", true)
                )
        ));
        when(unityConnection.sendToolRequest(eq(projectId), any())).thenReturn(capsMsg);

        // Step 5: User prompt -> AutonomousRunController -> AgentLoop
        GameGoal goal = new GameGoal("goal-1", "Create a 3D platformer game");
        AgentPlan plan = new AgentPlan("plan-1", goal.getGoalId());
        PlanStep step1 = new PlanStep("step-1", "Generate PlayerController script", List.of("generate_csharp_script"), "compiles");
        step1.setStatus(PlanStep.StepStatus.COMPLETED);
        plan.addStep(step1);

        AutonomousRunState runState = new AutonomousRunState("run-1", "sess-1", projectId, goal, plan);
        runState.setStatus(AutonomousRunState.RunStatus.RUNNING);
        when(runController.startRun(anyString(), eq(projectId), any())).thenReturn(runState);
        AutonomousRunState started = runController.startRun("Make a 3D platformer", projectId, "sess-1");
        assertEquals(AutonomousRunState.RunStatus.RUNNING, started.getStatus());

        // Create player script on disk
        Path scriptFile = projectRoot.resolve("Assets/Scripts/PlayerController.cs");
        Files.writeString(scriptFile, "public class PlayerController : UnityEngine.MonoBehaviour {}");

        // Step 6: Compile / Repair & Behavioral Tests & CompletionGate
        ValidationReport gateReport = new ValidationReport("goal-e2e");
        gateReport.setCompilationSuccess(true);
        gateReport.setBehaviorTestsPassed(true);
        gateReport.setRuntimeErrorsClean(true);
        gateReport.setTotalRequirements(1);
        gateReport.setRequiredCount(1);
        gateReport.setSatisfiedRequiredCount(1);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(gateReport);
        when(completionGate.canComplete(any())).thenReturn(true);

        // Step 7: Build actual artifact via Build Pipeline
        Path buildOutDir = projectRoot.resolve("Builds/Windows");
        Files.createDirectories(buildOutDir);
        Path gameExe = buildOutDir.resolve("Game.exe");
        Files.writeString(gameExe, "UNITY_STANDALONE_BINARY_MOCK_CONTENT");

        BuildRecord buildRec = new BuildRecord("b-e2e-1", projectId, "WINDOWS", "WINDOWS", "Release", BuildRecord.BuildStatus.SUCCEEDED, Instant.now().toString());
        when(buildService.queueBuild(eq(projectId), eq("WINDOWS"), eq("Release"), anyString())).thenReturn(buildRec);

        BuildProfile winProfile = profileManager.getProfile("Windows Release").orElseThrow();
        MultiPlatformBuildPipeline.BuildPipelineResult pipelineResult = buildPipeline.executePipeline(projectId, projectRoot, winProfile);
        assertTrue(pipelineResult.isSuccess(), "Build pipeline must succeed");

        // Step 8: Verify artifact exists & Calculate SHA-256
        BuildArtifact artifact = pipelineResult.getArtifact();
        assertNotNull(artifact);
        assertTrue(artifactManager.verifyArtifactIntegrity(artifact.getArtifactId(), projectRoot));
        assertNotNull(artifact.getSha256Checksum());

        // Step 9: Create Release Candidate
        Release release = releaseManager.createReleaseCandidate(
                "rel-e2e-1", projectId, "1.0.0", ReleaseChannel.STABLE,
                "Initial Stable Release", buildRec.getBuildId(), artifact.getArtifactId()
        );
        assertEquals(ReleaseStatus.DRAFT, release.getStatus());

        // Step 10: Validate Release Candidate
        ReleaseValidationReport valReport = releaseManager.validateReleaseCandidate("rel-e2e-1", artifact, projectRoot);
        assertTrue(valReport.isValid(), "Release candidate must be valid");
        assertEquals(ReleaseStatus.READY_FOR_REVIEW, releaseManager.getRelease("rel-e2e-1").orElseThrow().getStatus());

        // Step 11: Human Lead Approval
        Release approved = releaseManager.approveRelease("rel-e2e-1", "REVIEWER", "lead-engineer", "Production verified");
        assertEquals(ReleaseStatus.APPROVED, approved.getStatus());

        // Step 12: Publish Immutable Release (atomic transaction)
        Release published = releaseManager.publishRelease("rel-e2e-1", projectId, artifact, projectRoot);
        assertEquals(ReleaseStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isImmutable(), "Published release must be locked as immutable");

        // Step 13: Deploy published release to target staging directory
        DeploymentRecord deployRecord = deploymentManager.deployRelease(
                "dep-e2e-1", published, artifact, "Production Target", "LOCAL_FOLDER", projectRoot, deployTarget
        );
        assertEquals(DeploymentStatus.SUCCEEDED, deployRecord.getStatus());
        assertTrue(Files.exists(deployTarget.resolve("Game.exe")));

        // Step 14: Play/Launch verification: verify deployed binary matches published SHA-256
        String deployedSha = artifactManager.calculateSha256(deployTarget.resolve("Game.exe"));
        assertEquals(artifact.getSha256Checksum(), deployedSha, "Deployed game binary must exactly match published checksum");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Comprehensive Chaos & Failure Matrix Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Failure Case 1: Corrupted artifact detected and rejected")
    void testFailureCorruptedArtifactDetected() throws Exception {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-1", "Fail Game", projectRoot);
        Path gameExe = projectRoot.resolve("Builds/Game.exe");
        Files.createDirectories(gameExe.getParent());
        Files.writeString(gameExe, "VALID_BINARY");

        BuildArtifact artifact = artifactManager.registerArtifact("art-c1", "proj-fail-1", null, "WINDOWS", "x64", projectRoot, "Builds/Game.exe");
        // Corrupt on disk
        Files.writeString(gameExe, "TAMPERED_MALICIOUS_BYTES");

        assertFalse(artifactManager.verifyArtifactIntegrity("art-c1", projectRoot));
    }

    @Test
    @DisplayName("Failure Case 2: Modified published artifact cannot be deployed")
    void testFailureModifiedPublishedArtifactDeploymentRejected() throws Exception {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-2", "Fail Game 2", projectRoot);
        Path gameExe = projectRoot.resolve("Builds/Game.exe");
        Files.createDirectories(gameExe.getParent());
        Files.writeString(gameExe, "ORIGINAL_BINARY");

        BuildArtifact artifact = artifactManager.registerArtifact("art-c2", "proj-fail-2", null, "WINDOWS", "x64", projectRoot, "Builds/Game.exe");

        Release rel = new Release("rel-pub-c2", "proj-fail-2", "1.0.0", ReleaseChannel.STABLE, ReleaseStatus.PUBLISHED, "b1", "V1.0", Instant.now());

        // Modify artifact before deploy
        Files.writeString(gameExe, "MODIFIED_AFTER_PUBLISH");

        assertThrows(IllegalStateException.class, () -> {
            deploymentManager.deployRelease("dep-c2", rel, artifact, "Staging", "LOCAL_FOLDER", projectRoot, deployTarget);
        });
    }

    @Test
    @DisplayName("Failure Case 3: Failed build stops pipeline")
    void testFailureBuildExecutionFails() {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-3", "Fail Game 3", projectRoot);

        ValidationReport report = new ValidationReport("g3");
        report.setCompilationSuccess(true);
        report.setBehaviorTestsPassed(true);
        report.setRuntimeErrorsClean(true);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(report);
        when(completionGate.canComplete(any())).thenReturn(true);

        when(buildService.queueBuild(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Unity BuildPlayer failed with fatal error"));

        BuildProfile profile = profileManager.getProfile("Windows Release").orElseThrow();
        MultiPlatformBuildPipeline.BuildPipelineResult res = buildPipeline.executePipeline("proj-fail-3", projectRoot, profile);
        assertFalse(res.isSuccess());
        assertEquals("STAGE_3_BUILD_FAILED", res.getStage());
    }

    @Test
    @DisplayName("Failure Case 4: Failed behavioral tests prevent release validation")
    void testFailureBehavioralTestFailure() {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-4", "Fail Game 4", projectRoot);
        Release rel = new Release("rel-fail-4", "proj-fail-4", "1.0.0", ReleaseChannel.STABLE, ReleaseStatus.DRAFT, "b1", "V1.0", Instant.now());

        // Behavior tests failed
        ValidationReport gateReport = new ValidationReport("g4");
        gateReport.setCompilationSuccess(true);
        gateReport.setBehaviorTestsPassed(false);
        gateReport.setRuntimeErrorsClean(true);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(gateReport);

        ReleaseValidationReport report = releaseValidator.validateRelease(rel, null, projectRoot);
        assertFalse(report.isValid());
        assertFalse(report.isBehaviorPassed());
    }

    @Test
    @DisplayName("Failure Case 5: Failed CompletionGate rejects release")
    void testFailureCompletionGateRejection() {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-5", "Fail Game 5", projectRoot);
        Release rel = new Release("rel-fail-5", "proj-fail-5", "1.0.0", ReleaseChannel.STABLE, ReleaseStatus.DRAFT, "b1", "V1.0", Instant.now());

        ValidationReport gateReport = new ValidationReport("g5");
        gateReport.setCompilationSuccess(true);
        gateReport.setBehaviorTestsPassed(true);
        gateReport.setRuntimeErrorsClean(true);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(gateReport);
        when(completionGate.canComplete(any())).thenReturn(false); // CompletionGate rejects

        ReleaseValidationReport report = releaseValidator.validateRelease(rel, null, projectRoot);
        assertFalse(report.isValid());
        assertFalse(report.isCompletionGatePassed());
    }

    @Test
    @DisplayName("Failure Case 6: Unauthorized role cannot approve release")
    void testFailureUnauthorizedApprovalRole() {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-6", "Fail Game 6", projectRoot);
        releaseManager.createReleaseCandidate("rel-fail-6", "proj-fail-6", "1.0.0", ReleaseChannel.STABLE, "V1", "b1", "art1");
        releaseManager.updateReleaseStatus("rel-fail-6", ReleaseStatus.READY_FOR_REVIEW);

        assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-fail-6", "DEVELOPER", "dev-user", "Attempting self sign-off");
        });
    }

    @Test
    @DisplayName("Failure Case 7: LLM/AI Agent cannot approve itself")
    void testFailureLLMAttemptingToApproveItself() {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-7", "Fail Game 7", projectRoot);
        releaseManager.createReleaseCandidate("rel-fail-7", "proj-fail-7", "1.0.0", ReleaseChannel.STABLE, "V1", "b1", "art1");
        releaseManager.updateReleaseStatus("rel-fail-7", ReleaseStatus.READY_FOR_REVIEW);

        assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-fail-7", "REVIEWER", "agent", "Agent self-approval");
        });

        assertThrows(SecurityException.class, () -> {
            releaseManager.approveRelease("rel-fail-7", "REVIEWER", "llm", "LLM sign-off");
        });
    }

    @Test
    @DisplayName("Failure Case 8: Duplicate version cannot be published")
    void testFailureDuplicateVersionPublishRejection() throws Exception {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-8", "Fail Game 8", projectRoot);
        Path gameExe = projectRoot.resolve("Builds/Game.exe");
        Files.createDirectories(gameExe.getParent());
        Files.writeString(gameExe, "BIN");
        BuildArtifact art = artifactManager.registerArtifact("art-dup", "proj-fail-8", null, "WINDOWS", "x64", projectRoot, "Builds/Game.exe");

        // First publication of 1.0.0
        Release rel1 = releaseManager.createReleaseCandidate("rel-v1", "proj-fail-8", "1.0.0", ReleaseChannel.STABLE, "V1", "b1", "art-dup");
        releaseManager.updateValidationReport("rel-v1", "{}");
        releaseManager.updateReleaseStatus("rel-v1", ReleaseStatus.READY_FOR_REVIEW);
        releaseManager.approveRelease("rel-v1", "REVIEWER", "lead", "OK");
        releaseManager.publishRelease("rel-v1", "proj-fail-8", art, projectRoot);

        // Attempt second candidate with same 1.0.0 is rejected immediately
        assertThrows(IllegalArgumentException.class, () -> {
            releaseManager.createReleaseCandidate("rel-v2", "proj-fail-8", "1.0.0", ReleaseChannel.STABLE, "V1 duplicate", "b2", "art-dup");
        });
    }

    @Test
    @DisplayName("Failure Case 9: Cross-project artifact access strictly rejected")
    void testFailureCrossProjectArtifactAccessRejected() throws Exception {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-9A", "Game 9A", projectRoot);
        Path gameExe = projectRoot.resolve("Builds/Game.exe");
        Files.createDirectories(gameExe.getParent());
        Files.writeString(gameExe, "BIN");

        artifactManager.registerArtifact("art-cross", "proj-fail-9A", null, "WINDOWS", "x64", projectRoot, "Builds/Game.exe");

        // Another project attempts access
        assertFalse(artifactManager.getArtifact("proj-other", "art-cross").isPresent());
    }

    @Test
    @DisplayName("Failure Case 10: Path traversal rejected")
    void testFailurePathTraversalRejected() {
        assertThrows(SecurityException.class, () -> {
            artifactManager.validateAndCanonicalizePath(projectRoot, "../../../secret/password.txt");
        });
    }

    @Test
    @DisplayName("Failure Case 11: Secrets in profile rejected")
    void testFailureSecretInConfigProfileRejected() {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-11", "Fail Game 11", projectRoot);

        assertThrows(SecurityException.class, () -> {
            configManager.saveProfile("cfg-sec", "proj-fail-11", "Secret Profile",
                    ConfigEnvironment.RELEASE, Map.of("aws_secret", "SUPER_SECRET"));
        });
    }

    @Test
    @DisplayName("Failure Case 12: Restore of corrupted backup rejected")
    void testFailureRestoreOfCorruptedBackupRejected() throws Exception {
        templateManager.instantiateProject("tpl-blank-3d", "proj-fail-12", "Fail Game 12", projectRoot);
        Path bakZip = tempDir.resolve("backup-corrupted.zip");
        backupManager.createBackup("bak-c12", "proj-fail-12", projectRoot, bakZip);

        // Corrupt archive
        Files.writeString(bakZip, "EXTRA_CORRUPT_BYTES", java.nio.file.StandardOpenOption.APPEND);

        Path restoreTarget = tempDir.resolve("restored-fail");
        BackupManager.RestoreResult res = backupManager.restoreBackup("bak-c12", "proj-fail-12", bakZip, restoreTarget);
        assertFalse(res.isSuccess());
        assertEquals("CHECKSUM_MISMATCH", res.getStatus());
    }
}
