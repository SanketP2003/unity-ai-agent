package com.unityagent.api;

import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.checkpoint.AutonomyCheckpoint;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API for Phase 9 Long-Horizon Autonomous Development.
 *
 * <p>Exposes controls to initiate long-horizon runs, step through DAG sub-goals,
 * inspect execution state, pause, resume with state reconciliation, and review
 * unbypassable CompletionGate validation reports.
 */
@RestController
@RequestMapping("/api/autonomy")
public class AutonomyController {

    private static final Logger log = LoggerFactory.getLogger(AutonomyController.class);

    private final AutonomousRunController runController;
    private final CheckpointService checkpointService;
    private final com.unityagent.agent.events.EventJournalService eventJournalService;
    private final com.unityagent.agent.persistence.RunPersistenceService persistenceService;

    @Autowired
    public AutonomyController(AutonomousRunController runController,
                              CheckpointService checkpointService,
                              @Autowired(required = false) com.unityagent.agent.events.EventJournalService eventJournalService,
                              @Autowired(required = false) com.unityagent.agent.persistence.RunPersistenceService persistenceService) {
        this.runController = runController;
        this.checkpointService = checkpointService;
        this.eventJournalService = eventJournalService;
        this.persistenceService = persistenceService;
    }

    public AutonomyController(AutonomousRunController runController, CheckpointService checkpointService) {
        this(runController, checkpointService, null, null);
    }

    /**
     * Start a new autonomous run from a high-level user prompt.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startRun(@RequestBody Map<String, Object> request) {
        String prompt = (String) request.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Goal prompt cannot be empty"));
        }

        String projectId = (String) request.getOrDefault("projectId", "default");
        String sessionId = (String) request.getOrDefault("sessionId", "session_" + System.currentTimeMillis());

        try {
            AutonomousRunState state = runController.startRun(prompt, projectId, sessionId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("runId", state.getRunId());
            response.put("status", state.getStatus().name());
            response.put("goalId", state.getGoal() != null ? state.getGoal().getGoalId() : null);
            response.put("planId", state.getPlan() != null ? state.getPlan().getPlanId() : null);
            response.put("nodeCount", state.getPlan() != null ? state.getPlan().getNodeCount() : 0);
            response.put("readyNodeCount", state.getPlan() != null ? state.getPlan().getReadyPlanNodes().size() : 0);
            response.put("lastCheckpointId", state.getLastCheckpointId());

            return ResponseEntity.ok(response);
        } catch (com.unityagent.agent.concurrency.ProjectConflictException pce) {
            log.warn("Project lock conflict: {}", pce.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", pce.getMessage(),
                            "conflictProjectId", pce.getProjectId(),
                            "activeRunId", pce.getActiveRunId() != null ? pce.getActiveRunId() : ""
                    ));
        } catch (com.unityagent.agent.budget.BudgetExceededException bee) {
            log.warn("Resource budget exceeded: {}", bee.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", bee.getMessage(),
                            "reason", bee.getReason().name()
                    ));
        } catch (Exception e) {
            log.error("Failed to start autonomous run: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal server error"));
        }
    }

    /**
     * Executes the next ready sub-goal in the DAG plan.
     */
    @PostMapping("/{runId}/step")
    public ResponseEntity<Map<String, Object>> stepRun(@PathVariable String runId) {
        AutonomousRunState state = runController.getRunState(runId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            boolean hasMore = runController.executeNextSubGoal(runId);
            AutonomousRunState updated = runController.getRunState(runId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("runId", runId);
            response.put("hasMore", hasMore);
            response.put("status", updated.getStatus().name());
            response.put("completedNodes", updated.getExecutionState() != null ? updated.getExecutionState().getCompletedNodes() : List.of());
            response.put("totalToolCalls", updated.getExecutionState() != null ? updated.getExecutionState().getTotalToolCalls() : 0);
            response.put("recoveryCycles", updated.getExecutionState() != null ? updated.getExecutionState().getRecoveryCycles() : 0);
            response.put("replans", updated.getExecutionState() != null ? updated.getExecutionState().getReplans() : 0);
            response.put("lastCheckpointId", updated.getLastCheckpointId());

            if (updated.getCurrentIntervention() != null) {
                response.put("intervention", Map.of(
                        "reason", updated.getCurrentIntervention().getReason().name(),
                        "message", updated.getCurrentIntervention().getMessage(),
                        "requiredAction", updated.getCurrentIntervention().getRequiredAction()
                ));
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error executing step for run {}: {}", runId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error executing step"));
        }
    }

    /**
     * Get live execution status and DAG node metrics.
     */
    @GetMapping("/{runId}/state")
    public ResponseEntity<AutonomousRunState> getRunState(@PathVariable String runId) {
        AutonomousRunState state = runController.getRunState(runId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }

    /**
     * Pause an active run.
     */
    @PostMapping("/{runId}/pause")
    public ResponseEntity<Map<String, Object>> pauseRun(@PathVariable String runId,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? (String) body.get("reason") : "Manual pause by developer";
        AutonomousRunState state = runController.pauseRun(runId, reason);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("runId", runId, "status", state.getStatus().name(), "reason", reason));
    }

    /**
     * Resume a paused run, reconciling against current scene entities without replaying past work.
     */
    @PostMapping("/{runId}/resume")
    public ResponseEntity<Map<String, Object>> resumeRun(@PathVariable String runId,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        AutonomousRunState state = runController.getRunState(runId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        String checkpointId = body != null && body.containsKey("checkpointId")
                ? (String) body.get("checkpointId")
                : state.getLastCheckpointId();

        @SuppressWarnings("unchecked")
        List<String> sceneObjects = body != null && body.containsKey("sceneObjects")
                ? (List<String>) body.get("sceneObjects")
                : List.of();

        AutonomousRunState resumed = runController.resumeRun(runId, checkpointId, sceneObjects);
        return ResponseEntity.ok(Map.of(
                "runId", runId,
                "status", resumed.getStatus().name(),
                "resumedFromCheckpoint", checkpointId != null ? checkpointId : "latest"
        ));
    }

    /**
     * Cancel an active run.
     */
    @PostMapping("/{runId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelRun(@PathVariable String runId) {
        AutonomousRunState state = runController.cancelRun(runId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("runId", runId, "status", state.getStatus().name()));
    }

    /**
     * Resolve a human intervention boundary.
     */
    @PostMapping("/{runId}/intervene")
    public ResponseEntity<Map<String, Object>> intervene(@PathVariable String runId,
                                                         @RequestBody Map<String, Object> body) {
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String notes = (String) body.getOrDefault("notes", "");

        AutonomousRunState state = runController.approveIntervention(runId, approve, notes);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "runId", runId,
                "approved", approve,
                "status", state.getStatus().name()
        ));
    }

    /**
     * Retrieve latest validation report and CompletionGate decision.
     */
    @GetMapping("/{runId}/validation")
    public ResponseEntity<Map<String, Object>> getValidation(@PathVariable String runId) {
        AutonomousRunState state = runController.getRunState(runId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        ValidationReport report = state.getValidationReport();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("runId", runId);
        resp.put("hasReport", report != null);
        if (report != null) {
            resp.put("isCompleted", report.isGoalCompleted());
            resp.put("gatePassed", CompletionGate.evaluate(report));
            resp.put("compilationSuccess", report.isCompilationSuccess());
            resp.put("runtimeErrorsClean", report.isRuntimeErrorsClean());
            resp.put("behaviorTestsPassed", report.isBehaviorTestsPassed());
            resp.put("satisfiedRequired", report.getSatisfiedRequiredCount());
            resp.put("totalRequired", report.getRequiredCount());
            resp.put("summary", report.getSummary());
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * List all checkpoints for the project.
     */
    @GetMapping("/{runId}/checkpoints")
    public ResponseEntity<List<AutonomyCheckpoint>> listCheckpoints(@PathVariable String runId) {
        AutonomousRunState state = runController.getRunState(runId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        List<AutonomyCheckpoint> list = checkpointService.listCheckpoints(state.getProjectId());
        return ResponseEntity.ok(list);
    }

    /**
     * Retrieve durable event journal for a run (used to reconstruct UI timeline on browser refresh).
     */
    @GetMapping("/{runId}/events")
    public ResponseEntity<List<com.unityagent.agent.events.RunEventRecord>> getRunEvents(
            @PathVariable String runId,
            @RequestParam(defaultValue = "0") long sinceSequence) {
        if (eventJournalService == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(eventJournalService.getEventsSince(runId, sinceSequence));
    }

    /**
     * Retrieve persistent run records for a project.
     */
    @GetMapping("/projects/{projectId}/runs")
    public ResponseEntity<List<com.unityagent.agent.persistence.AutonomousRunRecord>> getProjectRuns(@PathVariable String projectId) {
        if (persistenceService == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(persistenceService.getRunsByProject(projectId));
    }

    /**
     * Retrieve single persistent run record.
     */
    @GetMapping("/{runId}/record")
    public ResponseEntity<com.unityagent.agent.persistence.AutonomousRunRecord> getRunRecord(@PathVariable String runId) {
        if (persistenceService == null) {
            return ResponseEntity.notFound().build();
        }
        return persistenceService.getRun(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
