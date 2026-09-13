package com.unityagent.api;

import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.verification.ValidationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AutonomyControllerTest {

    private AutonomousRunController runController;
    private CheckpointService checkpointService;
    private AutonomyController controller;

    @BeforeEach
    void setUp() {
        runController = Mockito.mock(AutonomousRunController.class);
        checkpointService = Mockito.mock(CheckpointService.class);
        controller = new AutonomyController(runController, checkpointService);
    }

    @Test
    @DisplayName("POST /api/autonomy/start returns 200 OK with runId and status")
    void testStartRunSuccess() {
        GameGoal goal = new GameGoal("goal_1", "Platformer Goal");
        AgentPlan plan = new AgentPlan("plan_1", "goal_1");
        AutonomousRunState state = new AutonomousRunState("run_123", "sess_1", "proj_1", goal, plan);
        state.setStatus(AutonomousRunState.RunStatus.RUNNING);
        state.setLastCheckpointId("cp_initial");

        when(runController.startRun(anyString(), anyString(), anyString())).thenReturn(state);

        ResponseEntity<Map<String, Object>> response = controller.startRun(Map.of(
                "prompt", "Build a 2D platformer",
                "projectId", "proj_1"
        ));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("run_123", response.getBody().get("runId"));
        assertEquals("RUNNING", response.getBody().get("status"));
        assertEquals("cp_initial", response.getBody().get("lastCheckpointId"));
    }

    @Test
    @DisplayName("POST /api/autonomy/start with empty prompt returns 400 Bad Request")
    void testStartRunWithEmptyPrompt() {
        ResponseEntity<Map<String, Object>> response = controller.startRun(Map.of("prompt", "   "));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("POST /api/autonomy/{runId}/step executes next sub-goal and returns status")
    void testStepRun() {
        GameGoal goal = new GameGoal("goal_1", "Platformer Goal");
        AgentPlan plan = new AgentPlan("plan_1", "goal_1");
        AutonomousRunState state = new AutonomousRunState("run_123", "sess_1", "proj_1", goal, plan);
        state.setStatus(AutonomousRunState.RunStatus.RUNNING);

        when(runController.getRunState("run_123")).thenReturn(state);
        when(runController.executeNextSubGoal("run_123")).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.stepRun("run_123");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("hasMore"));
        assertEquals("RUNNING", response.getBody().get("status"));
    }

    @Test
    @DisplayName("POST /api/autonomy/{runId}/pause and resume handles lifecycle transitions")
    void testPauseAndResume() {
        GameGoal goal = new GameGoal("goal_1", "Platformer Goal");
        AgentPlan plan = new AgentPlan("plan_1", "goal_1");
        AutonomousRunState state = new AutonomousRunState("run_123", "sess_1", "proj_1", goal, plan);
        state.setStatus(AutonomousRunState.RunStatus.PAUSED);

        when(runController.getRunState("run_123")).thenReturn(state);
        when(runController.pauseRun(eq("run_123"), any())).thenReturn(state);

        ResponseEntity<Map<String, Object>> pauseResp = controller.pauseRun("run_123", Map.of("reason", "manual test"));
        assertEquals(HttpStatus.OK, pauseResp.getStatusCode());
        assertEquals("PAUSED", pauseResp.getBody().get("status"));

        state.setStatus(AutonomousRunState.RunStatus.RUNNING);
        when(runController.resumeRun(eq("run_123"), any(), any())).thenReturn(state);

        ResponseEntity<Map<String, Object>> resumeResp = controller.resumeRun("run_123", Map.of("sceneObjects", List.of("Player")));
        assertEquals(HttpStatus.OK, resumeResp.getStatusCode());
        assertEquals("RUNNING", resumeResp.getBody().get("status"));
    }

    @Test
    @DisplayName("GET /api/autonomy/{runId}/validation returns CompletionGate report")
    void testGetValidationReport() {
        GameGoal goal = new GameGoal("goal_1", "Platformer Goal");
        AgentPlan plan = new AgentPlan("plan_1", "goal_1");
        AutonomousRunState state = new AutonomousRunState("run_123", "sess_1", "proj_1", goal, plan);

        ValidationReport report = new ValidationReport("goal_1");
        report.setGoalCompleted(true);
        report.setCompilationSuccess(true);
        report.setRuntimeErrorsClean(true);
        report.setBehaviorTestsPassed(true);
        report.setRequiredCount(3);
        report.setSatisfiedRequiredCount(3);
        report.setSummary("All criteria passed");
        state.setValidationReport(report);

        when(runController.getRunState("run_123")).thenReturn(state);

        ResponseEntity<Map<String, Object>> response = controller.getValidation("run_123");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("isCompleted"));
        assertEquals(true, response.getBody().get("gatePassed"));
        assertEquals(3, response.getBody().get("satisfiedRequired"));
    }
}
