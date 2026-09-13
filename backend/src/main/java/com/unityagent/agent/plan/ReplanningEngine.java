package com.unityagent.agent.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamic Replanning Engine for autonomous long-horizon runs.
 *
 * <p>Triggered when:
 * <ul>
 *   <li>Tool failures persist after initial recovery attempts</li>
 *   <li>Compilation errors indicate missing dependencies or syntax issues</li>
 *   <li>Missing prerequisites are discovered during pre-flight checks</li>
 *   <li>New scene objects or assets are discovered that alter required work</li>
 * </ul>
 *
 * <p><b>Hard Invariant:</b> Completed node history is NEVER mutated during replanning.
 * Completed node outputs and states are frozen, and revisions document all changes immutably.
 */
@Service
public class ReplanningEngine {

    private static final Logger log = LoggerFactory.getLogger(ReplanningEngine.class);
    private static final int DEFAULT_MAX_REPLANS = 10;

    private final int maxReplans;

    public ReplanningEngine() {
        this(DEFAULT_MAX_REPLANS);
    }

    public ReplanningEngine(int maxReplans) {
        this.maxReplans = maxReplans;
    }

    public int getMaxReplans() {
        return maxReplans;
    }

    /**
     * Checks if the plan can undergo further replanning without exceeding autonomy limits.
     */
    public boolean canReplan(AgentPlan plan) {
        if (plan == null) return false;
        return plan.getRevisions().size() < maxReplans;
    }

    /**
     * Handles a node failure by surgically inserting repair nodes and rewiring dependencies.
     *
     * @param plan the active agent plan
     * @param failedNodeId the identifier of the failed node
     * @param failureReason diagnostic reason for failure
     * @param missingDependencyOrAsset optional missing script or asset name if applicable
     * @return the generated PlanRevision record
     */
    public synchronized PlanRevision replanOnFailure(AgentPlan plan, String failedNodeId,
                                                    String failureReason, String missingDependencyOrAsset) {
        if (plan == null) {
            throw new IllegalArgumentException("AgentPlan cannot be null");
        }
        if (!canReplan(plan)) {
            log.warn("Max replan limit ({}) reached for plan {}", maxReplans, plan.getPlanId());
            plan.setStatus(AgentPlan.PlanStatus.FAILED);
            return null;
        }

        PlanNode failedNode = plan.getPlanNode(failedNodeId);
        if (failedNode == null) {
            throw new IllegalArgumentException("Node not found in plan: " + failedNodeId);
        }

        List<String> addedNodes = new ArrayList<>();
        List<String> modifiedNodes = new ArrayList<>();
        List<String> removedNodes = new ArrayList<>();

        if (missingDependencyOrAsset != null && !missingDependencyOrAsset.isBlank()) {
            // Surgical repair: inject a repair node for the missing asset/script
            String repairNodeId = "repair_" + sanitizeNodeId(missingDependencyOrAsset) + "_" + (plan.getRevisions().size() + 1);
            String desc = "Repair prerequisite: create or configure " + missingDependencyOrAsset;

            List<String> tools = new ArrayList<>();
            if (missingDependencyOrAsset.endsWith(".cs") || !missingDependencyOrAsset.contains(".")) {
                tools.add("create_script");
                tools.add("compile_project");
            } else {
                tools.add("create_primitive");
            }

            PlanNode repairNode = new PlanNode(repairNodeId, desc, PlanNode.PlanActionType.CREATE, tools);
            repairNode.setStatus(PlanNode.PlanNodeStatus.READY);

            // Copy any prerequisites of the failed node to the repair node if appropriate
            for (String dep : failedNode.getDependencies()) {
                PlanNode depNode = plan.getPlanNode(dep);
                if (depNode != null && depNode.isCompleted()) {
                    repairNode.addDependency(dep);
                }
            }

            plan.addPlanNode(repairNode);
            addedNodes.add(repairNodeId);

            // Rewire failed node to depend on repair node, and reset failed node to PENDING
            failedNode.addDependency(repairNodeId);
            failedNode.setStatus(PlanNode.PlanNodeStatus.PENDING);
            failedNode.setFailureReason("Awaiting repair completion: " + repairNodeId);
            modifiedNodes.add(failedNodeId);

            // Add dependency edge
            plan.addPlanDependency(failedNodeId, repairNodeId, DependencyType.REQUIRES);
        } else {
            // General failure: reset node to PENDING with modified parameter or alternative tool approach
            failedNode.setStatus(PlanNode.PlanNodeStatus.PENDING);
            failedNode.setFailureReason("Replanned: " + failureReason);
            modifiedNodes.add(failedNodeId);
        }

        // Cascade downstream: ensure nodes depending on failedNode remain PENDING or BLOCKED
        cascadeDownstreamState(plan, failedNodeId, modifiedNodes);

        String notes = "Replanned after failure on node " + failedNodeId + ": " + failureReason;
        PlanRevision revision = plan.createRevision("FAILURE_RECOVERY", addedNodes, modifiedNodes, removedNodes, notes);
        log.info("Plan {} replanned: revision {}, added {}, modified {}",
                plan.getPlanId(), revision.getRevisionNumber(), addedNodes, modifiedNodes);

        return revision;
    }

    /**
     * Adapts the plan when existing scene objects are discovered, switching CREATE actions to REUSE
     * to prevent redundant duplicate objects.
     */
    public synchronized PlanRevision replanOnDiscoveredEntities(AgentPlan plan, List<String> discoveredObjectNames) {
        if (plan == null || discoveredObjectNames == null || discoveredObjectNames.isEmpty()) {
            return null;
        }

        List<String> addedNodes = new ArrayList<>();
        List<String> modifiedNodes = new ArrayList<>();
        List<String> removedNodes = new ArrayList<>();

        for (String objectName : discoveredObjectNames) {
            for (PlanNode node : plan.getPlanNodes().values()) {
                // NEVER mutate completed history
                if (node.isCompleted()) continue;

                if (node.getActionType() == PlanNode.PlanActionType.CREATE &&
                        (node.getDescription().toLowerCase().contains(objectName.toLowerCase()) ||
                         node.getNodeId().toLowerCase().contains(objectName.toLowerCase()))) {

                    node.setActionType(PlanNode.PlanActionType.REUSE);
                    node.setDescription("Reuse existing " + objectName + " in scene (auto-detected)");
                    node.getParameters().put("reuseExisting", true);
                    node.getParameters().put("targetObject", objectName);
                    modifiedNodes.add(node.getNodeId());
                }
            }
        }

        if (modifiedNodes.isEmpty()) {
            return null;
        }

        String notes = "Adapted plan for discovered scene entities: " + discoveredObjectNames;
        return plan.createRevision("ENTITY_DISCOVERY", addedNodes, modifiedNodes, removedNodes, notes);
    }

    /**
     * Cascades invalidation downstream from a modified or invalidated node.
     * Guaranteed: COMPLETED nodes are NOT invalidated unless explicitly passed in.
     */
    private void cascadeDownstreamState(AgentPlan plan, String sourceNodeId, List<String> modifiedNodes) {
        Queue<String> queue = new ArrayDeque<>();
        queue.add(sourceNodeId);
        Set<String> visited = new HashSet<>();
        visited.add(sourceNodeId);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            for (PlanNode node : plan.getPlanNodes().values()) {
                // Completed nodes are preserved
                if (node.isCompleted()) continue;

                if (node.getDependencies().contains(currentId)) {
                    if (node.getStatus() == PlanNode.PlanNodeStatus.READY) {
                        node.setStatus(PlanNode.PlanNodeStatus.PENDING);
                        if (!modifiedNodes.contains(node.getNodeId())) {
                            modifiedNodes.add(node.getNodeId());
                        }
                    }
                    if (visited.add(node.getNodeId())) {
                        queue.add(node.getNodeId());
                    }
                }
            }
        }
    }

    private String sanitizeNodeId(String raw) {
        if (raw == null) return "entity";
        return raw.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
