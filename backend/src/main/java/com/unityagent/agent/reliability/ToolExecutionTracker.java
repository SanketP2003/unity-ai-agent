package com.unityagent.agent.reliability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-flight tool executions and reconciles UNKNOWN outcomes upon connection loss.
 * Prevents blind retries and duplicate entity creation (Invariant 7).
 */
@Service
public class ToolExecutionTracker {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionTracker.class);

    private final Map<String, ToolExecutionRecord> executionsByOperationId = new ConcurrentHashMap<>();

    /**
     * Registers a new pending tool execution.
     */
    public ToolExecutionRecord registerExecution(String toolCallId, String operationId, String projectId,
                                                String agentRunId, String toolName, Map<String, Object> parameters,
                                                int timeoutSeconds) {
        ToolExecutionRecord record = new ToolExecutionRecord(
                toolCallId, operationId, projectId, agentRunId, toolName, parameters, timeoutSeconds
        );
        executionsByOperationId.put(record.getOperationId(), record);
        return record;
    }

    public Optional<ToolExecutionRecord> getExecution(String operationId) {
        if (operationId == null) return Optional.empty();
        return Optional.ofNullable(executionsByOperationId.get(operationId));
    }

    /**
     * Marks all in-flight (RUNNING) executions for a project as UNKNOWN upon disconnect.
     */
    public List<ToolExecutionRecord> markInFlightAsUnknown(String projectId, String reason) {
        List<ToolExecutionRecord> unknownRecords = new ArrayList<>();
        for (ToolExecutionRecord record : executionsByOperationId.values()) {
            if ((projectId == null || projectId.equals(record.getProjectId()))
                    && record.getResultStatus() == ToolExecutionStatus.RUNNING) {
                record.markUnknown(reason);
                unknownRecords.add(record);
                log.warn("Marked tool command {} ({}) as UNKNOWN due to: {}",
                        record.getToolName(), record.getOperationId(), reason);
            }
        }
        return unknownRecords;
    }

    /**
     * Reconciles an UNKNOWN command outcome against live scene inspection.
     *
     * @param operationId       the operation to reconcile
     * @param liveSceneObjects  names/paths of objects verified in the active scene
     * @return reconciled status (SUCCEEDED if target object exists, FAILED/safe-to-retry otherwise)
     */
    public ToolExecutionStatus reconcileUnknownOperation(String operationId, Collection<String> liveSceneObjects) {
        ToolExecutionRecord record = executionsByOperationId.get(operationId);
        if (record == null) {
            return ToolExecutionStatus.UNKNOWN;
        }

        if (record.getResultStatus() != ToolExecutionStatus.UNKNOWN) {
            return record.getResultStatus();
        }

        String tool = record.getToolName();
        Map<String, Object> params = record.getParameters() != null ? record.getParameters() : Collections.emptyMap();

        log.info("Reconciling UNKNOWN outcome for tool '{}' (operationId={}) against {} live scene objects",
                tool, operationId, liveSceneObjects != null ? liveSceneObjects.size() : 0);

        // Check object creation tools: create_primitive, create_gameobject, create_empty_gameobject, create_light
        if (tool.contains("create") || tool.contains("primitive") || tool.contains("gameobject")) {
            String targetName = extractTargetName(params);
            if (targetName != null && liveSceneObjects != null) {
                boolean existsInScene = liveSceneObjects.stream()
                        .anyMatch(obj -> obj.equalsIgnoreCase(targetName) || obj.toLowerCase().endsWith("/" + targetName.toLowerCase()));

                if (existsInScene) {
                    log.info("Reconciliation SUCCESS: Target entity '{}' found in scene. Marking tool as SUCCEEDED without replay.",
                            targetName);
                    record.markSucceeded(Map.of("reconciled", true, "entityFound", targetName));
                    return ToolExecutionStatus.SUCCEEDED;
                } else {
                    log.info("Reconciliation: Target entity '{}' NOT found in scene. Marking as safe to retry.", targetName);
                    record.markFailed("Reconciled: Target entity '" + targetName + "' was not created before connection dropped.");
                    return ToolExecutionStatus.FAILED;
                }
            }
        }

        // Default if entity cannot be determined
        record.markFailed("Reconciled: Unknown outcome resolved as unverified.");
        return ToolExecutionStatus.FAILED;
    }

    private String extractTargetName(Map<String, Object> params) {
        if (params.containsKey("name")) return String.valueOf(params.get("name"));
        if (params.containsKey("gameObjectName")) return String.valueOf(params.get("gameObjectName"));
        if (params.containsKey("objectName")) return String.valueOf(params.get("objectName"));
        if (params.containsKey("primitiveType")) return String.valueOf(params.get("primitiveType"));
        return null;
    }
}
