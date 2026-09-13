package com.unityagent.studio;

import com.unityagent.agent.checkpoint.AutonomyCheckpoint;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.verification.BehaviorTestEngine;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.model.RollbackResult;
import com.unityagent.studio.service.CheckpointRollbackService;
import com.unityagent.unity.UnityConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class CheckpointRollbackTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private CheckpointService checkpointService;
    private UnityConnection unityConnection;
    private BehaviorTestEngine behaviorTestEngine;
    private ObjectiveValidator objectiveValidator;
    private CompletionGate completionGate;
    private CheckpointRollbackService rollbackService;

    @BeforeEach
    void setUp() {
        memoryDb = new MemoryDatabase(tempDir.resolve("rollback_test.db").toString());
        memoryDb.initialize();

        checkpointService = Mockito.mock(CheckpointService.class);
        unityConnection = Mockito.mock(UnityConnection.class);
        behaviorTestEngine = new BehaviorTestEngine();
        objectiveValidator = new ObjectiveValidator();
        completionGate = new CompletionGate();

        rollbackService = new CheckpointRollbackService(
                checkpointService,
                unityConnection,
                behaviorTestEngine,
                objectiveValidator,
                completionGate,
                memoryDb
        );
    }

    @AfterEach
    void tearDown() {
        if (memoryDb != null) {
            memoryDb.shutdown();
        }
    }

    private AutonomyCheckpoint createMockCheckpoint(String projectId, String checkpointId) {
        return new AutonomyCheckpoint(
                checkpointId,
                projectId,
                "run_100",
                "plan_1",
                1,
                List.of("node_arena", "node_player"),
                "node_controller",
                Map.of(),
                Map.of("milestone", "Milestone 1")
        );
    }

    @Test
    @DisplayName("Should return FAILED when checkpoint does not exist")
    void testRollbackCheckpointNotFound() {
        when(checkpointService.getCheckpoint("proj_1", "cp_missing")).thenReturn(null);

        RollbackResult result = rollbackService.rollback("proj_1", "cp_missing");
        assertNotNull(result);
        assertEquals(RollbackResult.RollbackStatus.FAILED, result.getStatus());
        assertTrue(result.getSummary().contains("Checkpoint not found"));
    }

    @Test
    @DisplayName("Should succeed when all 4 post-rollback gates pass: compile, scene, behavior, CompletionGate")
    void testRollbackAllGatesPass() {
        AutonomyCheckpoint cp = createMockCheckpoint("proj_1", "cp_valid_1");
        when(checkpointService.getCheckpoint("proj_1", "cp_valid_1")).thenReturn(cp);

        // All checks pass
        rollbackService.setCustomVerifier(new CheckpointRollbackService.PostRollbackVerifier() {
            @Override
            public List<String> verifyCompilation(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public List<String> inspectScene(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public List<String> validateBehavior(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public boolean evaluateCompletionGate(String projectId, AutonomyCheckpoint checkpoint, ValidationReport report) {
                return true;
            }
        });

        RollbackResult result = rollbackService.rollback("proj_1", "cp_valid_1");

        assertNotNull(result);
        assertEquals(RollbackResult.RollbackStatus.SUCCESS, result.getStatus());
        assertTrue(result.isCompilePassed());
        assertTrue(result.isSceneInspectionPassed());
        assertTrue(result.isBehavioralValidationPassed());
        assertTrue(result.isCompletionGatePassed());
        assertTrue(result.getFilesRestored() > 0);
        assertTrue(result.getSummary().contains("verified across all post-rollback gates"));
    }

    @Test
    @DisplayName("Mandatory pipeline: Never report success merely because files were restored - fails on compile error")
    void testRollbackFailsOnCompileError() {
        AutonomyCheckpoint cp = createMockCheckpoint("proj_1", "cp_comp_fail");
        when(checkpointService.getCheckpoint("proj_1", "cp_comp_fail")).thenReturn(cp);

        // Compile fails
        rollbackService.setCustomVerifier(new CheckpointRollbackService.PostRollbackVerifier() {
            @Override
            public List<String> verifyCompilation(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of("CS0246: The type or namespace name 'MissingType' could not be found");
            }

            @Override
            public List<String> inspectScene(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public List<String> validateBehavior(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public boolean evaluateCompletionGate(String projectId, AutonomyCheckpoint checkpoint, ValidationReport report) {
                return false;
            }
        });

        RollbackResult result = rollbackService.rollback("proj_1", "cp_comp_fail");

        assertNotNull(result);
        // CRITICAL GUARANTEE: Even though files were restored, status must NOT be SUCCESS
        assertEquals(RollbackResult.RollbackStatus.ROLLBACK_REQUIRES_REVIEW, result.getStatus());
        assertFalse(result.isCompilePassed());
        assertEquals(1, result.getCompileErrors().size());
        assertTrue(result.getCompileErrors().get(0).contains("CS0246"));
        assertTrue(result.getSummary().contains("Compile errors"));
        assertTrue(result.getSummary().contains("Requires human review"));
    }

    @Test
    @DisplayName("Mandatory pipeline: Fails on scene inspection drift")
    void testRollbackFailsOnSceneDrift() {
        AutonomyCheckpoint cp = createMockCheckpoint("proj_1", "cp_scene_fail");
        when(checkpointService.getCheckpoint("proj_1", "cp_scene_fail")).thenReturn(cp);

        // Scene inspection fails
        rollbackService.setCustomVerifier(new CheckpointRollbackService.PostRollbackVerifier() {
            @Override
            public List<String> verifyCompilation(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public List<String> inspectScene(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of("Expected root 'Arena' was missing from hierarchy after rollback");
            }

            @Override
            public List<String> validateBehavior(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public boolean evaluateCompletionGate(String projectId, AutonomyCheckpoint checkpoint, ValidationReport report) {
                return false;
            }
        });

        RollbackResult result = rollbackService.rollback("proj_1", "cp_scene_fail");

        assertNotNull(result);
        assertEquals(RollbackResult.RollbackStatus.ROLLBACK_REQUIRES_REVIEW, result.getStatus());
        assertTrue(result.isCompilePassed());
        assertFalse(result.isSceneInspectionPassed());
        assertEquals(1, result.getSceneErrors().size());
        assertTrue(result.getSummary().contains("Scene inspection drift"));
    }

    @Test
    @DisplayName("Mandatory pipeline: Fails on behavioral test assertion failure")
    void testRollbackFailsOnBehavioralError() {
        AutonomyCheckpoint cp = createMockCheckpoint("proj_1", "cp_behavior_fail");
        when(checkpointService.getCheckpoint("proj_1", "cp_behavior_fail")).thenReturn(cp);

        // Behavior validation fails
        rollbackService.setCustomVerifier(new CheckpointRollbackService.PostRollbackVerifier() {
            @Override
            public List<String> verifyCompilation(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public List<String> inspectScene(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public List<String> validateBehavior(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of("NullReferenceException detected in PlayerController.Update()");
            }

            @Override
            public boolean evaluateCompletionGate(String projectId, AutonomyCheckpoint checkpoint, ValidationReport report) {
                return false;
            }
        });

        RollbackResult result = rollbackService.rollback("proj_1", "cp_behavior_fail");

        assertNotNull(result);
        assertEquals(RollbackResult.RollbackStatus.ROLLBACK_REQUIRES_REVIEW, result.getStatus());
        assertFalse(result.isBehavioralValidationPassed());
        assertEquals(1, result.getBehavioralErrors().size());
        assertTrue(result.getSummary().contains("Behavior validation failure"));
    }

    @Test
    @DisplayName("Mandatory pipeline: Fails when CompletionGate rejects rollback state")
    void testRollbackFailsWhenCompletionGateRejects() {
        AutonomyCheckpoint cp = createMockCheckpoint("proj_1", "cp_gate_fail");
        when(checkpointService.getCheckpoint("proj_1", "cp_gate_fail")).thenReturn(cp);

        // Individual steps report ok, but CompletionGate evaluates to false
        rollbackService.setCustomVerifier(new CheckpointRollbackService.PostRollbackVerifier() {
            @Override
            public List<String> verifyCompilation(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public List<String> inspectScene(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public List<String> validateBehavior(String projectId, AutonomyCheckpoint checkpoint) {
                return List.of();
            }

            @Override
            public boolean evaluateCompletionGate(String projectId, AutonomyCheckpoint checkpoint, ValidationReport report) {
                return false; // Rejected by gate
            }
        });

        RollbackResult result = rollbackService.rollback("proj_1", "cp_gate_fail");

        assertNotNull(result);
        assertEquals(RollbackResult.RollbackStatus.ROLLBACK_REQUIRES_REVIEW, result.getStatus());
        assertFalse(result.isCompletionGatePassed());
        assertTrue(result.getSummary().contains("CompletionGate rejected"));
    }
}
