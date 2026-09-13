package com.unityagent.studio;

import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalRequirement;
import com.unityagent.agent.goal.RequirementStatus;
import com.unityagent.agent.persistence.RunPersistenceService;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.plan.PlanNode;
import com.unityagent.api.StudioController;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.service.StudioDiagnosticsService;
import com.unityagent.studio.service.StudioProjectService;
import com.unityagent.studio.service.StudioSecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RunWorkspaceTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private AutonomousRunController runController;
    private RunPersistenceService runPersistenceService;
    private StudioController studioController;

    @BeforeEach
    void setUp() {
        memoryDb = new MemoryDatabase(tempDir.resolve("run_workspace_test.db").toString());
        memoryDb.initialize();

        runController = Mockito.mock(AutonomousRunController.class);
        runPersistenceService = new RunPersistenceService(memoryDb, new com.fasterxml.jackson.databind.ObjectMapper());
        StudioProjectService projectService = Mockito.mock(StudioProjectService.class);

        studioController = new StudioController(
                projectService,
                runController,
                runPersistenceService,
                null,
                null,
                null,
                null,
                new StudioDiagnosticsService(),
                new StudioSecurityService(memoryDb),
                null,
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
    @DisplayName("GET /api/studio/runs/{runId}/plan should return visual DAG nodes and edges")
    void testGetRunPlanDag() {
        AgentPlan plan = new AgentPlan("plan_dag_1", "Build Mini Arena");
        PlanNode n1 = new PlanNode("node_ground", "Create Ground Arena", PlanNode.PlanActionType.CREATE, List.of("create_primitive"));
        PlanNode n2 = new PlanNode("node_player", "Spawn Player", PlanNode.PlanActionType.CREATE, List.of("create_primitive"));
        n2.setDependencies(List.of("node_ground"));

        plan.addPlanNode(n1);
        plan.addPlanNode(n2);

        GameGoal goal = new GameGoal("goal_1", "Build Arena");
        AutonomousRunState liveRun = new AutonomousRunState("run_dag_100", "sess_1", "proj_arena", goal, plan);
        when(runController.getRunState("run_dag_100")).thenReturn(liveRun);

        ResponseEntity<Map<String, Object>> resp = studioController.getRunPlanDag("run_dag_100");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());

        List<?> nodes = (List<?>) resp.getBody().get("nodes");
        List<?> edges = (List<?>) resp.getBody().get("edges");

        assertEquals(2, nodes.size());
        assertEquals(1, edges.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> edge = (Map<String, Object>) edges.get(0);
        assertEquals("node_ground", edge.get("from"));
        assertEquals("node_player", edge.get("to"));
    }

    @Test
    @DisplayName("GET /api/studio/runs/{runId}/requirements should return checklist and CompletionGate readiness")
    void testGetRunRequirements() {
        GameGoal goal = new GameGoal("goal_req_1", "Complete Level 1");
        GoalRequirement r1 = new GoalRequirement("req_arena", "goal_req_1", "Arena Exists");
        r1.setStatus(RequirementStatus.SATISFIED);
        r1.setRequired(true);

        GoalRequirement r2 = new GoalRequirement("req_player", "goal_req_1", "Player Spawned");
        r2.setStatus(RequirementStatus.PENDING);
        r2.setRequired(true);

        goal.addRequirement(r1);
        goal.addRequirement(r2);

        AutonomousRunState liveRun = new AutonomousRunState("run_req_200", "sess_1", "proj_req", goal, new AgentPlan());
        when(runController.getRunState("run_req_200")).thenReturn(liveRun);

        ResponseEntity<Map<String, Object>> resp = studioController.getRunRequirements("run_req_200");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());

        assertEquals(2, resp.getBody().get("requiredTotal"));
        assertEquals(1, resp.getBody().get("requiredSatisfied"));
        assertEquals(false, resp.getBody().get("completionGateReady"));

        // Now satisfy r2
        r2.setStatus(RequirementStatus.SATISFIED);
        ResponseEntity<Map<String, Object>> resp2 = studioController.getRunRequirements("run_req_200");
        assertEquals(2, resp2.getBody().get("requiredSatisfied"));
        assertEquals(true, resp2.getBody().get("completionGateReady"));
    }

    @Test
    @DisplayName("GET /api/studio/runs/{runId} should return live state details")
    void testGetRunDetails() {
        GameGoal goal = new GameGoal("g1", "Make Game");
        AutonomousRunState liveRun = new AutonomousRunState("run_det_300", "sess_1", "proj_1", goal, new AgentPlan());
        when(runController.getRunState("run_det_300")).thenReturn(liveRun);

        ResponseEntity<Map<String, Object>> resp = studioController.getRunDetails("run_det_300");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("run_det_300", resp.getBody().get("runId"));
        assertTrue((Boolean) resp.getBody().get("isLive"));
    }
}
