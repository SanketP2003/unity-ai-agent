package com.unityagent.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.checkpoint.AutonomyCheckpoint;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.verification.BehaviorTestEngine;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.studio.model.*;
import com.unityagent.studio.service.*;
import com.unityagent.unity.UnityConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class StudioIntegrationTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private SQLiteMemoryRepository repository;
    private StudioProjectService projectService;
    private ChangeReviewService changeReviewService;
    private StudioBuildService buildService;
    private CheckpointService checkpointService;
    private CheckpointRollbackService rollbackService;
    private StudioSecurityService securityService;
    private StudioDiagnosticsService diagnosticsService;
    private UnityConnection unityConnection;

    @BeforeEach
    void setUp() {
        memoryDb = new MemoryDatabase(tempDir.resolve("integration_test.db").toString());
        memoryDb.initialize();

        repository = new SQLiteMemoryRepository(memoryDb);
        unityConnection = Mockito.mock(UnityConnection.class);
        checkpointService = Mockito.mock(CheckpointService.class);

        projectService = new StudioProjectService(memoryDb, repository, unityConnection, null, null, null);
        changeReviewService = new ChangeReviewService(memoryDb);
        buildService = new StudioBuildService(memoryDb, unityConnection);
        securityService = new StudioSecurityService(memoryDb);
        diagnosticsService = new StudioDiagnosticsService();

        rollbackService = new CheckpointRollbackService(
                checkpointService,
                unityConnection,
                new BehaviorTestEngine(),
                new ObjectiveValidator(),
                new CompletionGate(),
                memoryDb
        );
    }

    @AfterEach
    void tearDown() {
        if (memoryDb != null) {
            memoryDb.shutdown();
        }
    }

    @Test
    @DisplayName("End-to-End Studio Flow: Project Projection -> Change Review -> Build -> Safe Reconciled Rollback -> Audit")
    void testEndToEndStudioWorkflow() throws Exception {
        String projectId = "proj_e2e_game";

        // 1. Authoritative project identity in memory database
        ProjectMemory pm = new ProjectMemory(projectId, "E2E Cyberpunk Game", "2022.3.15f1", "Windows", "URP", "1.0.0", null);
        repository.upsertProject(pm);

        // 2. Project projection
        Optional<StudioProject> spOpt = projectService.getProject(projectId);
        assertTrue(spOpt.isPresent());
        assertEquals("E2E Cyberpunk Game", spOpt.get().getProjectName());

        // 3. Change review workflow
        ChangeSet cs = changeReviewService.createChangeSet(projectId, "run_e2e_1", "node_scene", "Update Main Scene");
        changeReviewService.addEntry(cs.getChangeSetId(), ChangeType.FILE_MODIFY, "Assets/Scenes/Main.unity",
                "h_before", "h_after", "diff_data", "Scene entity modification");

        assertEquals(RiskLevel.HIGH, cs.getRiskLevel());

        // Human boundary: LLM is prohibited
        assertThrows(SecurityException.class, () ->
            changeReviewService.approveChangeSet(cs.getChangeSetId(), "LLM_AGENT", "agent_loop")
        );

        // Human reviewer approves
        ChangeSet approvedCs = changeReviewService.approveChangeSet(cs.getChangeSetId(), "REVIEWER", "lead_reviewer");
        assertEquals(ApprovalState.APPROVED, approvedCs.getStatus());

        // 4. Build execution with physical artifact validation
        Path buildArtifact = tempDir.resolve("Build_Cyberpunk.exe");
        Files.writeString(buildArtifact, "MOCK_BINARY_PAYLOAD_FOR_STUDIO_TEST");

        BuildRecord buildRec = buildService.queueBuild(projectId, "StandaloneWindows64", "Release", buildArtifact.toString());
        BuildRecord completedBuild = buildService.executeBuild(buildRec.getBuildId());
        assertEquals(BuildRecord.BuildStatus.SUCCEEDED, completedBuild.getStatus());
        assertTrue(completedBuild.getArtifactSize() > 0);

        // 5. Safe reconciled rollback with mandatory 4-gate verification
        AutonomyCheckpoint cp = new AutonomyCheckpoint("cp_e2e_milestone", projectId, "run_e2e_1", "plan_1",
                1, List.of("node_terrain"), null, Map.of(), Map.of());
        when(checkpointService.getCheckpoint(projectId, "cp_e2e_milestone")).thenReturn(cp);

        rollbackService.setCustomVerifier(new CheckpointRollbackService.PostRollbackVerifier() {
            @Override public List<String> verifyCompilation(String projectId, AutonomyCheckpoint checkpoint) { return List.of(); }
            @Override public List<String> inspectScene(String projectId, AutonomyCheckpoint checkpoint) { return List.of(); }
            @Override public List<String> validateBehavior(String projectId, AutonomyCheckpoint checkpoint) { return List.of(); }
            @Override public boolean evaluateCompletionGate(String projectId, AutonomyCheckpoint checkpoint, ValidationReport report) { return true; }
        });

        RollbackResult rbResult = rollbackService.rollback(projectId, "cp_e2e_milestone");
        assertEquals(RollbackResult.RollbackStatus.SUCCESS, rbResult.getStatus());
        assertTrue(rbResult.isCompletionGatePassed());

        // 6. Audit trail
        securityService.logAuditEvent("lead_reviewer", projectId, "run_e2e_1", "FULL_E2E_VERIFIED", "project", "SUCCESS", "All steps verified");
        List<AuditEvent> auditTrail = securityService.getAuditTrail(projectId, 10);
        assertFalse(auditTrail.isEmpty());
        assertEquals("FULL_E2E_VERIFIED", auditTrail.get(0).getAction());
    }
}
