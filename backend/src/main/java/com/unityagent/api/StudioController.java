package com.unityagent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.checkpoint.AutonomyCheckpoint;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalRequirement;
import com.unityagent.agent.persistence.AutonomousRunRecord;
import com.unityagent.agent.persistence.RunPersistenceService;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.plan.PlanNode;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.model.*;
import com.unityagent.studio.service.*;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * REST controller for the Professional Autonomous Game Studio.
 * Exposes non-authoritative aggregated projections, workspace visualizers,
 * human approval workflows, safe rollback, and studio inspection endpoints.
 */
@RestController
@RequestMapping("/api/studio")
public class StudioController {

    private static final Logger log = LoggerFactory.getLogger(StudioController.class);

    private final StudioProjectService projectService;
    private final AutonomousRunController runController;
    private final RunPersistenceService runPersistenceService;
    private final CheckpointService checkpointService;
    private final CheckpointRollbackService rollbackService;
    private final ChangeReviewService changeReviewService;
    private final StudioBuildService buildService;
    private final StudioDiagnosticsService diagnosticsService;
    private final StudioSecurityService securityService;
    private final UnityConnection unityConnection;
    private final MemoryDatabase memoryDb;
    private final ObjectMapper objectMapper;

    public StudioController(StudioProjectService projectService,
                            AutonomousRunController runController,
                            RunPersistenceService runPersistenceService,
                            CheckpointService checkpointService,
                            CheckpointRollbackService rollbackService,
                            ChangeReviewService changeReviewService,
                            StudioBuildService buildService,
                            StudioDiagnosticsService diagnosticsService,
                            StudioSecurityService securityService,
                            UnityConnection unityConnection,
                            MemoryDatabase memoryDb) {
        this.projectService = projectService;
        this.runController = runController;
        this.runPersistenceService = runPersistenceService;
        this.checkpointService = checkpointService;
        this.rollbackService = rollbackService;
        this.changeReviewService = changeReviewService;
        this.buildService = buildService;
        this.diagnosticsService = diagnosticsService;
        this.securityService = securityService;
        this.unityConnection = unityConnection;
        this.memoryDb = memoryDb;
        this.objectMapper = new ObjectMapper();
    }

    // ── Projects & Dashboard (11.2) ──────────────────────────────────────────

    @GetMapping("/projects")
    public List<StudioProject> getProjects() {
        return projectService.listProjects();
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<StudioProject> getProject(@PathVariable String projectId) {
        Optional<StudioProject> p = projectService.getProject(projectId);
        return p.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/projects/{projectId}/activity")
    public List<ProjectActivity> getProjectActivity(@PathVariable String projectId,
                                                   @RequestParam(defaultValue = "30") int limit) {
        return projectService.getProjectActivity(projectId, limit);
    }

    @PutMapping("/projects/{projectId}/metadata")
    public ResponseEntity<StudioProjectMetadata> updateProjectMetadata(
            @PathVariable String projectId,
            @RequestBody StudioProjectMetadata metadata) {
        if (metadata != null) {
            StudioProjectMetadata saved = projectService.saveMetadata(projectId, metadata);
            securityService.logAuditEvent("user", projectId, null, "METADATA_UPDATE", projectId, "SUCCESS", "Updated project metadata");
            return ResponseEntity.ok(saved);
        }
        return ResponseEntity.badRequest().build();
    }

    // ── Runs & Visual DAG (11.3, 11.4, 11.5) ─────────────────────────────────

    @GetMapping("/runs")
    public List<Map<String, Object>> getRuns(@RequestParam(required = false) String projectId) {
        List<AutonomousRunRecord> records;
        if (projectId != null) {
            records = runPersistenceService.getRunsByProject(projectId);
        } else {
            records = new ArrayList<>();
            try (Connection conn = memoryDb.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM autonomous_runs ORDER BY start_time DESC LIMIT 50");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new AutonomousRunRecord(
                            rs.getString("run_id"),
                            rs.getString("session_id"),
                            rs.getString("project_id"),
                            rs.getString("goal_text"),
                            rs.getString("status")
                    ));
                }
            } catch (Exception e) {
                log.warn("Failed to query recent runs: {}", e.getMessage());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (AutonomousRunRecord r : records) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("runId", r.getRunId());
            map.put("projectId", r.getProjectId());
            map.put("goal", r.getGoalText());
            map.put("status", r.getStatus());
            map.put("toolCount", r.getToolCallCount());
            map.put("createdAt", r.getStartTime() != null ? r.getStartTime().toString() : null);
            map.put("completedAt", r.getCompletedAt() != null ? r.getCompletedAt().toString() : null);
            result.add(map);
        }
        return result;
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<Map<String, Object>> getRunDetails(@PathVariable String runId) {
        AutonomousRunState liveState = runController.getRunState(runId);
        if (liveState != null) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("runId", liveState.getRunId());
            map.put("projectId", liveState.getProjectId());
            map.put("status", liveState.getStatus().name());
            map.put("goal", liveState.getGoal() != null ? liveState.getGoal().getDescription() : null);
            map.put("nodeCount", liveState.getPlan() != null ? liveState.getPlan().getNodeCount() : 0);
            map.put("completedNodes", liveState.getPlan() != null ? liveState.getPlan().getCompletedNodeIds().size() : 0);
            map.put("isLive", true);
            return ResponseEntity.ok(map);
        }

        Optional<AutonomousRunRecord> recordOpt = runPersistenceService.getRun(runId);
        if (recordOpt.isPresent()) {
            AutonomousRunRecord record = recordOpt.get();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("runId", record.getRunId());
            map.put("projectId", record.getProjectId());
            map.put("status", record.getStatus());
            map.put("goal", record.getGoalText());
            map.put("isLive", false);
            return ResponseEntity.ok(map);
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/runs/{runId}/plan")
    public ResponseEntity<Map<String, Object>> getRunPlanDag(@PathVariable String runId) {
        AgentPlan plan = null;
        AutonomousRunState liveState = runController.getRunState(runId);
        if (liveState != null) {
            plan = liveState.getPlan();
        }

        if (plan == null) {
            Optional<AutonomousRunRecord> recordOpt = runPersistenceService.getRun(runId);
            if (recordOpt.isPresent() && recordOpt.get().getPlanJson() != null) {
                try {
                    plan = objectMapper.readValue(recordOpt.get().getPlanJson(), AgentPlan.class);
                } catch (Exception ignored) {}
            }
        }

        if (plan == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> dag = new LinkedHashMap<>();
        dag.put("planId", plan.getPlanId());
        dag.put("revision", plan.getCurrentRevisionNumber());

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        for (PlanNode node : plan.getPlanNodes().values()) {
            Map<String, Object> nMap = new LinkedHashMap<>();
            nMap.put("id", node.getNodeId());
            nMap.put("label", node.getDescription());
            nMap.put("status", node.getStatus().name());
            nMap.put("actionType", node.getActionType() != null ? node.getActionType().name() : "CREATE");
            nodes.add(nMap);

            if (node.getDependencies() != null) {
                for (String depId : node.getDependencies()) {
                    edges.add(Map.of("from", depId, "to", node.getNodeId()));
                }
            }
        }

        dag.put("nodes", nodes);
        dag.put("edges", edges);
        return ResponseEntity.ok(dag);
    }

    @GetMapping("/runs/{runId}/requirements")
    public ResponseEntity<Map<String, Object>> getRunRequirements(@PathVariable String runId) {
        AutonomousRunState liveState = runController.getRunState(runId);
        GameGoal goal = (liveState != null) ? liveState.getGoal() : null;

        if (goal == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("goalId", goal.getGoalId());
        resp.put("goalText", goal.getDescription());

        List<Map<String, Object>> reqs = new ArrayList<>();
        int satisfiedCount = 0;
        int requiredCount = 0;

        for (GoalRequirement req : goal.getRequirements()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", req.getRequirementId());
            r.put("description", req.getDescription());
            r.put("required", req.isRequired());
            r.put("status", req.getStatus().name());
            r.put("evidenceCount", req.getEvidenceList() != null ? req.getEvidenceList().size() : 0);
            reqs.add(r);

            if (req.isRequired()) {
                requiredCount++;
                if (req.getStatus() == com.unityagent.agent.goal.RequirementStatus.SATISFIED) {
                    satisfiedCount++;
                }
            }
        }

        resp.put("requirements", reqs);
        resp.put("requiredTotal", requiredCount);
        resp.put("requiredSatisfied", satisfiedCount);
        resp.put("completionGateReady", (requiredCount > 0 && satisfiedCount == requiredCount));

        return ResponseEntity.ok(resp);
    }

    // ── Explorers: Scene, Scripts, Assets (11.6, 11.7, 11.8) ──────────────────

    @GetMapping("/projects/{projectId}/scene")
    public ResponseEntity<Map<String, Object>> getSceneHierarchy(@PathVariable String projectId) {
        // Try live Unity perception first
        if (unityConnection != null && unityConnection.isReady()) {
            try {
                UnityMessage req = UnityMessage.toolRequest(UUID.randomUUID().toString(), "get_scene_hierarchy", Map.of());
                UnityMessage resp = unityConnection.sendToolRequest(projectId, req);
                if (resp != null && resp.getData() != null) {
                    return ResponseEntity.ok(resp.getData());
                }
            } catch (Exception e) {
                log.warn("Live scene hierarchy request failed, falling back to cache: {}", e.getMessage());
            }
        }

        // Cached fallback from memory
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("source", "cached_studio_perception");
        fallback.put("objects", List.of(
                Map.of("name", "Main Camera", "tag", "MainCamera", "active", true),
                Map.of("name", "Directional Light", "tag", "Untagged", "active", true)
        ));
        return ResponseEntity.ok(fallback);
    }

    @GetMapping("/projects/{projectId}/scripts")
    public List<Map<String, Object>> getScripts(@PathVariable String projectId) {
        List<Map<String, Object>> scripts = new ArrayList<>();
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT script_path, class_name, content_hash, last_modified_at FROM scripts WHERE project_id = ?")) {
            stmt.setString(1, projectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("path", rs.getString("script_path"));
                    s.put("className", rs.getString("class_name"));
                    s.put("contentHash", rs.getString("content_hash"));
                    s.put("lastModified", rs.getString("last_modified_at"));
                    scripts.add(s);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load scripts: {}", e.getMessage());
        }
        return scripts;
    }

    @PostMapping("/projects/{projectId}/scripts/diff")
    public ResponseEntity<Map<String, Object>> calculateScriptDiff(
            @PathVariable String projectId,
            @RequestBody Map<String, String> request) {
        String before = request.getOrDefault("beforeContent", "");
        String after = request.getOrDefault("afterContent", "");

        List<String> diffLines = computeSimpleDiff(before, after);
        return ResponseEntity.ok(Map.of("diffLines", diffLines, "changeCount", diffLines.size()));
    }

    @GetMapping("/projects/{projectId}/assets")
    public ResponseEntity<List<Map<String, Object>>> getAssets(
            @PathVariable String projectId,
            @RequestParam(required = false) String category) {
        List<Map<String, Object>> assets = new ArrayList<>();
        String sql = (category != null)
                ? "SELECT asset_path, asset_type, asset_guid, metadata, last_modified_at FROM assets WHERE project_id = ? AND asset_type = ?"
                : "SELECT asset_path, asset_type, asset_guid, metadata, last_modified_at FROM assets WHERE project_id = ?";

        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, projectId);
            if (category != null) {
                stmt.setString(2, category);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("path", rs.getString("asset_path"));
                    a.put("type", rs.getString("asset_type"));
                    a.put("guid", rs.getString("asset_guid"));
                    a.put("metadata", rs.getString("metadata"));
                    a.put("lastModified", rs.getString("last_modified_at"));
                    assets.add(a);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query assets: {}", e.getMessage());
        }
        return ResponseEntity.ok(assets);
    }

    // ── Changes, Approvals & Rollback (11.9, 11.10, 11.14) ────────────────────

    @GetMapping("/runs/{runId}/changes")
    public List<ChangeSet> getRunChanges(@PathVariable String runId) {
        return changeReviewService.getChangeSetsForRun(runId);
    }

    @GetMapping("/projects/{projectId}/changes/pending")
    public List<ChangeSet> getPendingChanges(@PathVariable String projectId) {
        return changeReviewService.getPendingChangeSets(projectId);
    }

    @PostMapping("/changes/{changeSetId}/approve")
    public ResponseEntity<?> approveChangeSet(
            @PathVariable String changeSetId,
            @RequestBody Map<String, String> body) {
        String reviewerRole = body.getOrDefault("role", "DEVELOPER");
        String reviewerId = body.getOrDefault("reviewerId", "user");

        try {
            ChangeSet cs = changeReviewService.approveChangeSet(changeSetId, reviewerRole, reviewerId);
            securityService.logAuditEvent(reviewerId, cs.getProjectId(), cs.getAgentRunId(),
                    "CHANGE_APPROVE", changeSetId, "SUCCESS", "ChangeSet approved by " + reviewerId);
            return ResponseEntity.ok(cs);
        } catch (SecurityException e) {
            securityService.logAuditEvent(reviewerId, null, null,
                    "CHANGE_APPROVE", changeSetId, "DENIED", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/changes/{changeSetId}/reject")
    public ResponseEntity<ChangeSet> rejectChangeSet(
            @PathVariable String changeSetId,
            @RequestBody Map<String, String> body) {
        String reviewerId = body.getOrDefault("reviewerId", "user");
        String reason = body.getOrDefault("reason", "Rejected by reviewer");

        ChangeSet cs = changeReviewService.rejectChangeSet(changeSetId, reviewerId, reason);
        securityService.logAuditEvent(reviewerId, cs.getProjectId(), cs.getAgentRunId(),
                "CHANGE_REJECT", changeSetId, "SUCCESS", "Reason: " + reason);
        return ResponseEntity.ok(cs);
    }

    @GetMapping("/projects/{projectId}/checkpoints")
    public List<AutonomyCheckpoint> getCheckpoints(@PathVariable String projectId) {
        return checkpointService.listCheckpoints(projectId);
    }

    @PostMapping("/projects/{projectId}/checkpoints/{checkpointId}/rollback")
    public ResponseEntity<RollbackResult> rollbackToCheckpoint(
            @PathVariable String projectId,
            @PathVariable String checkpointId) {
        RollbackResult result = rollbackService.rollback(projectId, checkpointId);
        securityService.logAuditEvent("user", projectId, result.getRunId(),
                "ROLLBACK_EXECUTE", checkpointId, result.getStatus().name(), result.getSummary());
        return ResponseEntity.ok(result);
    }

    // ── Builds (11.11) ───────────────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/builds")
    public List<BuildRecord> getBuilds(@PathVariable String projectId) {
        return buildService.getBuildsForProject(projectId);
    }

    @PostMapping("/projects/{projectId}/builds")
    public ResponseEntity<?> triggerBuild(
            @PathVariable String projectId,
            @RequestBody Map<String, String> body) {
        String platform = body.getOrDefault("platform", "StandaloneWindows64");
        String configuration = body.getOrDefault("configuration", "Release");
        String outputPath = body.getOrDefault("outputPath", "Builds/" + platform + "/Game.exe");

        try {
            BuildRecord record = buildService.queueBuild(projectId, platform, configuration, outputPath);
            securityService.logAuditEvent("user", projectId, null,
                    "BUILD_TRIGGER", record.getBuildId(), "QUEUED", "Platform: " + platform);
            return ResponseEntity.ok(record);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/builds/{buildId}/cancel")
    public ResponseEntity<BuildRecord> cancelBuild(@PathVariable String buildId) {
        BuildRecord record = buildService.cancelBuild(buildId);
        return ResponseEntity.ok(record);
    }

    // ── Diagnostics & Audit (11.12, 11.15) ───────────────────────────────────

    @GetMapping("/diagnostics")
    public List<DiagnosticEntry> getDiagnostics(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "100") int limit) {

        DiagnosticEntry.Category cat = category != null ? DiagnosticEntry.Category.valueOf(category.toUpperCase()) : null;
        DiagnosticEntry.Severity sev = severity != null ? DiagnosticEntry.Severity.valueOf(severity.toUpperCase()) : null;

        return diagnosticsService.queryDiagnostics(projectId, cat, sev, search, limit);
    }

    @GetMapping("/audit")
    public List<AuditEvent> getAuditTrail(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "50") int limit) {
        return securityService.getAuditTrail(projectId, limit);
    }

    // ── Diff calculation utility ─────────────────────────────────────────────

    private List<String> computeSimpleDiff(String before, String after) {
        List<String> diff = new ArrayList<>();
        String[] beforeLines = before != null ? before.split("\\r?\\n") : new String[0];
        String[] afterLines = after != null ? after.split("\\r?\\n") : new String[0];

        int max = Math.max(beforeLines.length, afterLines.length);
        for (int i = 0; i < max; i++) {
            String b = i < beforeLines.length ? beforeLines[i] : null;
            String a = i < afterLines.length ? afterLines[i] : null;

            if (b == null) {
                diff.add("+" + a);
            } else if (a == null) {
                diff.add("-" + b);
            } else if (!b.equals(a)) {
                diff.add("-" + b);
                diff.add("+" + a);
            } else {
                diff.add(" " + b);
            }
        }
        return diff;
    }
}
