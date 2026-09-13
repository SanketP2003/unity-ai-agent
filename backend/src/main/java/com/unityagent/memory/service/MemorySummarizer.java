package com.unityagent.memory.service;

import com.unityagent.agent.event.AgentEvent;
import com.unityagent.agent.model.AgentRunResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Deterministic fact extraction from agent run results and events.
 * No LLM calls — uses structured event data to produce compact JSON summaries.
 *
 * <p>Extracts: goals, created objects, scripts, compilation results,
 * runtime failures, repairs, and validation results.
 */
@Service
public class MemorySummarizer {

    private static final Logger log = LoggerFactory.getLogger(MemorySummarizer.class);
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Extract a compact summary from a completed agent run.
     *
     * @param result the agent run result
     * @param events list of events from the run
     * @return JSON string summary
     */
    public String extractSummary(AgentRunResult result, List<AgentEvent> events) {
        return extractSummary(null, result, events);
    }

    /**
     * Extract a compact summary from a completed agent run with an optional fallback goal.
     *
     * @param fallbackGoal fallback goal if not present on result
     * @param result the agent run result
     * @param events list of events from the run
     * @return JSON string summary
     */
    public String extractSummary(String fallbackGoal, AgentRunResult result, List<AgentEvent> events) {
        Map<String, Object> summary = new LinkedHashMap<>();

        // Goal and status
        String goal = (result != null && result.getUserMessage() != null) ? result.getUserMessage() : fallbackGoal;
        String status = result != null ? (result.isSuccess() ? "COMPLETED" : "FAILED") : "UNKNOWN";
        summary.put("goal", goal);
        summary.put("status", status);

        // Extract facts from events
        List<String> createdObjects = new ArrayList<>();
        List<String> createdScripts = new ArrayList<>();
        List<String> modifiedScripts = new ArrayList<>();
        String compilationResult = null;
        List<String> runtimeErrors = new ArrayList<>();
        List<String> repairs = new ArrayList<>();
        String validationResult = null;
        int toolCount = 0;

        if (events != null) {
            for (AgentEvent event : events) {
                String type = event.getType();
                String tool = event.getTool();
                Object data = event.getResultData();

                if ("TOOL_COMPLETED".equals(type)) {
                    toolCount++;

                    if ("create_gameobject".equals(tool) || "create_primitive".equals(tool)) {
                        String name = extractFieldFromData(data, "name");
                        if (name != null) createdObjects.add(name);
                    } else if ("create_script".equals(tool)) {
                        String path = extractFieldFromData(data, "path");
                        if (path != null) createdScripts.add(path);
                    } else if ("update_script".equals(tool)) {
                        String path = extractFieldFromData(data, "path");
                        if (path != null) modifiedScripts.add(path);
                    } else if ("compile_project".equals(tool)) {
                        compilationResult = event.getErrorMessage() != null ? "FAILED" : "SUCCESS";
                    } else if ("validate_game_state".equals(tool)) {
                        validationResult = event.getErrorMessage() != null ? "FAILED" : String.valueOf(data);
                    }
                } else if ("TOOL_FAILED".equals(type)) {
                    toolCount++;
                    if ("run_game_test".equals(tool) || "enter_play_mode".equals(tool)) {
                        String err = event.getErrorMessage();
                        if (err != null) runtimeErrors.add(tool + ": " + truncate(err, 200));
                    }
                } else if ("REPAIR_STARTED".equals(type)) {
                    String activity = event.getActivity();
                    if (activity != null) repairs.add(truncate(activity, 200));
                }
            }
        }

        summary.put("createdObjects", createdObjects);
        summary.put("createdScripts", createdScripts);
        summary.put("modifiedScripts", modifiedScripts);
        summary.put("compilationResult", compilationResult);
        summary.put("runtimeErrors", runtimeErrors);
        summary.put("repairs", repairs);
        summary.put("validationResult", validationResult);
        summary.put("toolCount", toolCount);

        if (result != null) {
            summary.put("iterations", result.getIterations());
        }

        try {
            return mapper.writeValueAsString(summary);
        } catch (Exception e) {
            log.warn("Failed to serialize summary: {}", e.getMessage());
            return "{\"goal\":\"" + (goal != null ? goal.replace("\"", "'") : "") + "\",\"status\":\"" + status + "\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractFieldFromData(Object data, String field) {
        if (data == null) return null;
        if (data instanceof Map<?, ?> map) {
            Object val = map.get(field);
            return val != null ? val.toString() : null;
        }
        // Try string parsing as fallback
        String str = data.toString();
        // Simple field extraction: "field=value" or "\"field\":\"value\""
        String jsonPattern = "\"" + field + "\":\"";
        int idx = str.indexOf(jsonPattern);
        if (idx >= 0) {
            int start = idx + jsonPattern.length();
            int end = str.indexOf("\"", start);
            if (end > start) return str.substring(start, end);
        }
        return null;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
