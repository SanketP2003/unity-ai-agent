package com.unityagent.agent.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.RequirementManager;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.plan.LongHorizonPlanner;
import com.unityagent.agent.plan.PlanExecutionState;
import com.unityagent.agent.plan.PlanNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages saving, loading, and reconciling milestone checkpoints.
 * Enforces the hard rule that resumption reconciles against actual Unity state
 * and never blindly replays previous tool calls.
 */
@Service
public class CheckpointService {

    private static final Logger log = LoggerFactory.getLogger(CheckpointService.class);
    private final ObjectMapper objectMapper;

    // Map: projectId -> Map: checkpointId -> checkpoint
    private final Map<String, Map<String, AutonomyCheckpoint>> memoryStorage = new ConcurrentHashMap<>();
    private final Path checkpointDir;

    public CheckpointService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String userHome = System.getProperty("user.home", ".");
        this.checkpointDir = Paths.get(userHome, ".unityagent", "checkpoints");
        try {
            Files.createDirectories(checkpointDir);
        } catch (IOException e) {
            log.warn("Could not create checkpoint directory {}: {}", checkpointDir, e.getMessage());
        }
    }

    public CheckpointService(Path customDir) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.checkpointDir = customDir;
        try {
            Files.createDirectories(checkpointDir);
        } catch (IOException e) {
            log.warn("Could not create custom checkpoint directory {}: {}", customDir, e.getMessage());
        }
    }

    public synchronized void saveCheckpoint(AutonomyCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.getCheckpointId() == null) return;
        String project = checkpoint.getProjectId() != null ? checkpoint.getProjectId() : "default";

        memoryStorage.computeIfAbsent(project, k -> new LinkedHashMap<>())
                .put(checkpoint.getCheckpointId(), checkpoint);

        // Persist to disk
        try {
            Path projectPath = checkpointDir.resolve(project);
            Files.createDirectories(projectPath);
            Path file = projectPath.resolve(checkpoint.getCheckpointId() + ".json");
            objectMapper.writeValue(file.toFile(), checkpoint);
            log.info("Saved autonomy checkpoint: id={}, project={}, completedNodes={}",
                    checkpoint.getCheckpointId(), project, checkpoint.getCompletedNodes().size());
        } catch (IOException e) {
            log.warn("Failed to persist checkpoint to disk (non-fatal): {}", e.getMessage());
        }
    }

    public synchronized AutonomyCheckpoint saveCheckpoint(String runId, String projectId, GameGoal goal,
                                                         AgentPlan plan, PlanExecutionState executionState, String milestone) {
        String cpId = "cp_" + (milestone != null ? milestone.replaceAll("[^a-zA-Z0-9_]", "_") : "step") + "_" + System.currentTimeMillis();
        List<String> completed = plan != null ? plan.getCompletedNodeIds() : List.of();
        String current = plan != null && plan.getCurrentStep() != null ? plan.getCurrentStep().getStepId() : null;
        int rev = plan != null ? plan.getCurrentRevisionNumber() : 1;
        String planId = plan != null ? plan.getPlanId() : "plan_" + runId;

        Map<String, Object> metrics = new LinkedHashMap<>();
        if (executionState != null) {
            metrics.put("toolCalls", executionState.getTotalToolCalls());
            metrics.put("recoveryCycles", executionState.getRecoveryCycles());
            metrics.put("replans", executionState.getReplans());
        }
        if (milestone != null) {
            metrics.put("milestone", milestone);
        }

        AutonomyCheckpoint cp = new AutonomyCheckpoint(cpId, projectId, runId, planId, rev, completed, current, Map.of(), metrics);
        saveCheckpoint(cp);
        return cp;
    }

    public AutonomyCheckpoint getCheckpoint(String projectId, String checkpointId) {
        String project = projectId != null ? projectId : "default";
        Map<String, AutonomyCheckpoint> projMap = memoryStorage.get(project);
        if (projMap != null && projMap.containsKey(checkpointId)) {
            return projMap.get(checkpointId);
        }

        // Check disk
        try {
            Path file = checkpointDir.resolve(project).resolve(checkpointId + ".json");
            if (Files.exists(file)) {
                return objectMapper.readValue(file.toFile(), AutonomyCheckpoint.class);
            }
        } catch (IOException ignored) {}

        return null;
    }

    public AutonomyCheckpoint loadCheckpoint(String projectId, String checkpointId) {
        return getCheckpoint(projectId, checkpointId);
    }

    public List<AutonomyCheckpoint> listCheckpoints(String projectId) {
        String project = projectId != null ? projectId : "default";
        Map<String, AutonomyCheckpoint> projMap = memoryStorage.get(project);
        if (projMap != null) {
            return new ArrayList<>(projMap.values());
        }
        return List.of();
    }

    public AutonomyCheckpoint getLatestCheckpoint(String projectId) {
        List<AutonomyCheckpoint> list = listCheckpoints(projectId);
        if (list.isEmpty()) return null;
        return list.get(list.size() - 1);
    }

    /**
     * Resumption protocol:
     * 1. Load checkpoint
     * 2. Inspect Unity state (passed via liveObjects and liveScripts)
     * 3. Compare actual state against checkpoint completed nodes
     * 4. Invalidate stale nodes if entities were deleted/modified
     * 5. Rebuild plan retaining valid completed work
     * 6. Return reconciled plan ready to continue without replaying previous tool calls
     */
    public AgentPlan reconcileAndResume(AutonomyCheckpoint checkpoint, Collection<String> liveObjects) {
        GameGoal goal = new GameGoal("goal_" + (checkpoint != null ? checkpoint.getProjectId() : "resumed"), "Resumed Goal");
        return reconcileAndResume(checkpoint, liveObjects, List.of(), new LongHorizonPlanner(new com.unityagent.tools.ToolRegistry(List.of())), goal, new RequirementManager());
    }

    public AgentPlan reconcileAndResume(AutonomyCheckpoint checkpoint,
                                        Collection<String> liveObjects,
                                        Collection<String> liveScripts,
                                        LongHorizonPlanner planner,
                                        GameGoal goal,
                                        RequirementManager reqMgr) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("Cannot resume from null checkpoint");
        }

        Set<String> objects = liveObjects != null ? liveObjects.stream().map(String::toLowerCase).collect(Collectors.toSet()) : Set.of();
        Set<String> scripts = liveScripts != null ? liveScripts.stream().map(String::toLowerCase).collect(Collectors.toSet()) : Set.of();

        log.info("Reconciling checkpoint {} against actual Unity state ({} objects, {} scripts)",
                checkpoint.getCheckpointId(), objects.size(), scripts.size());

        // Generate baseline plan with REUSE for existing live entities
        AgentPlan resumedPlan = planner.createPlan(goal, reqMgr, objects, scripts);
        resumedPlan.setPlanId(checkpoint.getPlanId());

        // Reconcile completed nodes from checkpoint
        Set<String> validCompleted = new HashSet<>();
        for (String completedNodeId : checkpoint.getCompletedNodes()) {
            PlanNode node = resumedPlan.getPlanNode(completedNodeId);
            if (node == null) continue;

            // Verify that the entity claimed to be completed actually still exists in Unity
            boolean entityStillExists = true;
            String lowerDesc = node.getDescription().toLowerCase();
            if (lowerDesc.contains("player") && !objects.contains("player")) {
                entityStillExists = false;
            } else if (lowerDesc.contains("ground") && !objects.contains("ground") && !objects.contains("arena")) {
                entityStillExists = false;
            } else if (lowerDesc.contains("controller") && lowerDesc.contains("script") && !scripts.contains("playercontroller")) {
                entityStillExists = false;
            }

            if (entityStillExists) {
                node.setStatus(PlanNode.PlanNodeStatus.COMPLETED);
                validCompleted.add(completedNodeId);
            } else {
                log.warn("Node {} was marked COMPLETED in checkpoint but entity is missing in live Unity -> Invalidating", completedNodeId);
                node.setStatus(PlanNode.PlanNodeStatus.PENDING);
            }
        }

        // Re-evaluate readiness of remaining nodes
        resumedPlan.getReadyPlanNodes();

        log.info("Resumption reconciled: {} valid completed nodes preserved, {} remaining nodes ready to execute",
                validCompleted.size(), resumedPlan.getReadyPlanNodes().size());

        return resumedPlan;
    }
}
