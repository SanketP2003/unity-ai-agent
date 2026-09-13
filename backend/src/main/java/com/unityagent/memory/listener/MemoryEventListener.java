package com.unityagent.memory.listener;

import com.unityagent.agent.event.AgentEvent;
import com.unityagent.agent.event.AgentEventListener;
import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.model.ToolExecutionResult;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.model.ConversationRecord;
import com.unityagent.memory.model.ScriptMemory;
import com.unityagent.memory.service.MemorySummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listens to agent lifecycle events and writes meaningful facts to persistent memory.
 *
 * <p>Records: object creation, script creation/modification, compilation results,
 * validation results, goal completion/failure.
 *
 * <p>Does NOT record: heartbeats, "Thinking..." activity, raw tool arguments,
 * hidden reasoning, API credentials.
 */
@Component
public class MemoryEventListener implements AgentEventListener {

    private static final Logger log = LoggerFactory.getLogger(MemoryEventListener.class);

    private final MemoryRepository repository;
    private final MemorySummarizer summarizer;

    // Per-run event accumulation for summarization
    private final List<AgentEvent> currentRunEvents = new CopyOnWriteArrayList<>();
    private volatile String currentProjectId;
    private volatile String currentSessionId;
    private volatile String currentAgentRunId;
    private volatile String currentUserGoal;

    public MemoryEventListener(MemoryRepository repository, MemorySummarizer summarizer) {
        this.repository = repository;
        this.summarizer = summarizer;
    }

    /**
     * Set the project context for the current run.
     * Must be called before the run starts.
     */
    public void setRunContext(String projectId, String sessionId, String agentRunId, String userGoal) {
        this.currentProjectId = projectId;
        this.currentSessionId = sessionId;
        this.currentAgentRunId = agentRunId;
        this.currentUserGoal = userGoal;
        this.currentRunEvents.clear();
    }

    @Override
    public void onRunStarted(String sessionId, String agentRunId, String userMessage) {
        this.currentSessionId = sessionId;
        this.currentAgentRunId = agentRunId;
        this.currentUserGoal = userMessage;
        this.currentRunEvents.clear();
    }

    @Override
    public void onActivity(String agentRunId, String activity) {
        // Do not persist activity/thinking events
    }

    @Override
    public void onToolStarted(String agentRunId, String toolCallId, String toolName, Map<String, Object> arguments) {
        // Do not persist tool start events
    }

    @Override
    public void onToolCompleted(String agentRunId, String toolCallId, String toolName, ToolExecutionResult result) {
        if (currentProjectId == null) return;

        try {
            Object data = result.getResultData();

            switch (toolName) {
                case "create_gameobject", "create_primitive" -> {
                    String name = extractField(data, "name");
                    if (name != null) {
                        repository.upsertMemoryEntry(currentProjectId, "ARCHITECTURE",
                                "object:" + name, name, "AGENT_RESULT", 1.0);
                    }
                }
                case "create_script" -> {
                    String path = extractField(data, "path");
                    String hash = extractField(data, "hash");
                    if (path != null) {
                        ScriptMemory script = new ScriptMemory();
                        script.setProjectId(currentProjectId);
                        script.setScriptPath(path);
                        script.setClassName(extractClassNameFromPath(path));
                        script.setContentHash(hash);
                        script.setSource("AGENT_RESULT");
                        repository.upsertScript(script);
                    }
                }
                case "update_script" -> {
                    String path = extractField(data, "path");
                    String hash = extractField(data, "hash");
                    if (path != null) {
                        repository.findScriptByPath(currentProjectId, path).ifPresent(existing -> {
                            existing.setContentHash(hash);
                            existing.setLastModifiedAt(Instant.now());
                            existing.setSource("AGENT_RESULT");
                            repository.upsertScript(existing);
                        });
                    }
                }
                case "compile_project" -> {
                    repository.upsertMemoryEntry(currentProjectId, "VALIDATION",
                            "last_compilation", "SUCCESS", "AGENT_RESULT", 1.0);
                }
                case "validate_game_state" -> {
                    String dataStr = data != null ? data.toString() : "";
                    boolean satisfied = dataStr.contains("allRequirementsSatisfied\":true")
                            || dataStr.contains("allRequirementsSatisfied=true");
                    repository.upsertMemoryEntry(currentProjectId, "VALIDATION",
                            "last_validation", satisfied ? "ALL_SATISFIED" : "INCOMPLETE",
                            "VALIDATION", 1.0);
                }
                default -> {
                    // Record object creation for set_/add_ tools
                    if (toolName.startsWith("create_") || toolName.startsWith("assign_")) {
                        String name = extractField(data, "name");
                        if (name == null) name = extractField(data, "target");
                        if (name != null) {
                            repository.upsertMemoryEntry(currentProjectId, "ARCHITECTURE",
                                    "component:" + name, toolName + " → " + name, "AGENT_RESULT", 0.8);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Memory event processing error (non-fatal): {}", e.getMessage());
        }

        // Accumulate events for end-of-run summarization
        // Create a minimal event record (avoid storing raw arguments)
        AgentEvent minimalEvent = AgentEvent.toolCompleted(0, currentSessionId, agentRunId,
                toolCallId, toolName, result.getResultData());
        currentRunEvents.add(minimalEvent);
    }

    @Override
    public void onToolFailed(String agentRunId, String toolCallId, String toolName, ToolExecutionResult result) {
        if (currentProjectId == null) return;

        try {
            if ("compile_project".equals(toolName)) {
                repository.upsertMemoryEntry(currentProjectId, "FAILURE",
                        "compilation_failure", result.getErrorMessage(), "AGENT_RESULT", 1.0);
                repository.upsertMemoryEntry(currentProjectId, "VALIDATION",
                        "last_compilation", "FAILED", "AGENT_RESULT", 1.0);
            }
        } catch (Exception e) {
            log.debug("Memory event failure processing error (non-fatal): {}", e.getMessage());
        }

        AgentEvent minimalEvent = AgentEvent.toolFailed(0, currentSessionId, agentRunId,
                toolCallId, toolName, result.getErrorMessage());
        currentRunEvents.add(minimalEvent);
    }

    @Override
    public void onRunCompleted(String sessionId, String agentRunId, AgentRunResult result) {
        if (currentProjectId == null) return;

        try {
            // Generate summary
            String summary = summarizer.extractSummary(result, currentRunEvents);

            // Record conversation
            ConversationRecord record = new ConversationRecord(
                    sessionId, currentProjectId, agentRunId,
                    currentUserGoal, "COMPLETED",
                    result != null ? result.getTotalToolCalls() : 0,
                    summary
            );
            record.setCompletedAt(Instant.now());
            repository.insertConversation(record);

            // Update project last run
            repository.findProject(currentProjectId).ifPresent(p -> {
                p.setLastAgentRunId(agentRunId);
                repository.upsertProject(p);
            });

            log.info("Recorded completed conversation in memory: project={}, run={}", currentProjectId, agentRunId);
        } catch (Exception e) {
            log.warn("Failed to record conversation memory (non-fatal): {}", e.getMessage());
        }

        currentRunEvents.clear();
    }

    @Override
    public void onRunFailed(String sessionId, String agentRunId, String errorMessage) {
        if (currentProjectId == null) return;

        try {
            ConversationRecord record = new ConversationRecord(
                    sessionId, currentProjectId, agentRunId,
                    currentUserGoal, "FAILED", 0,
                    "{\"goal\":\"" + (currentUserGoal != null ? currentUserGoal.replace("\"", "'") : "") +
                            "\",\"status\":\"FAILED\",\"error\":\"" + (errorMessage != null ? errorMessage.replace("\"", "'") : "") + "\"}"
            );
            record.setCompletedAt(Instant.now());
            repository.insertConversation(record);

            repository.upsertMemoryEntry(currentProjectId, "FAILURE",
                    "run_failure:" + agentRunId, errorMessage, "AGENT_RESULT", 1.0);
        } catch (Exception e) {
            log.warn("Failed to record failure memory (non-fatal): {}", e.getMessage());
        }

        currentRunEvents.clear();
    }

    @Override
    public void onRunCancelled(String sessionId, String agentRunId) {
        currentRunEvents.clear();
    }

    @SuppressWarnings("unchecked")
    private String extractField(Object data, String field) {
        if (data == null) return null;
        if (data instanceof Map<?, ?> map) {
            Object val = map.get(field);
            return val != null ? val.toString() : null;
        }
        return null;
    }

    private String extractClassNameFromPath(String path) {
        if (path == null) return null;
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return fileName.endsWith(".cs") ? fileName.substring(0, fileName.length() - 3) : fileName;
    }
}
