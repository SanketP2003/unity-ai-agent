package com.unityagent.memory.service;

import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Retrieves relevant persistent memory for a given project and goal.
 * Applies keyword relevance filtering, freshness classification, and
 * context size bounding to produce a compact {@link MemoryContext}.
 *
 * <p>Priority ordering:
 * 1. Current Unity inspection (not handled here — done by AgentLoop)
 * 2. Validation results
 * 3. Recent tool results
 * 4. Persistent project memory (this service)
 * 5. Old conversation history
 * 6. Agent inference
 */
@Service
public class MemoryRetriever {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetriever.class);

    private final MemoryRepository repository;

    @Value("${memory.max-context-size:12000}")
    private int maxContextSize = 12000;

    @Value("${memory.max-recent-conversations:5}")
    private int maxRecentConversations = 5;

    @Value("${memory.max-scripts-in-context:20}")
    private int maxScriptsInContext = 20;

    @Value("${memory.stale-threshold-hours:24}")
    private long staleThresholdHours = 24;

    public MemoryRetriever(MemoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Retrieve relevant context for a project and goal.
     *
     * @param projectId  the project to retrieve memory for
     * @param userGoal   the current user goal (used for relevance filtering)
     * @return compact memory context, or null if no memory exists
     */
    public MemoryContext getRelevantContext(String projectId, String userGoal) {
        if (projectId == null || projectId.isBlank()) return null;

        MemoryContext ctx = new MemoryContext();
        String goalLower = userGoal != null ? userGoal.toLowerCase() : "";

        try {
            // 1. Project identity
            repository.findProject(projectId).ifPresent(project -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Project: ").append(nvl(project.getProjectName(), projectId));
                if (project.getUnityVersion() != null) {
                    sb.append(" (Unity ").append(project.getUnityVersion());
                    if (project.getPlatform() != null) sb.append(", ").append(project.getPlatform());
                    if (project.getRenderPipeline() != null) sb.append(", ").append(project.getRenderPipeline());
                    sb.append(")");
                }
                if (project.getLastAgentRunId() != null) {
                    sb.append("\nLast agent run: ").append(project.getLastAgentRunId());
                }
                ctx.setProjectSummary(sb.toString());
            });

            // 2. Architecture snapshot
            repository.findLatestArchitecture(projectId).ifPresent(arch -> {
                String json = arch.getArchitectureJson();
                if (json != null && !json.isEmpty()) {
                    // Truncate if too large
                    ctx.setArchitectureSummary(json.length() > 3000 ? json.substring(0, 3000) + "..." : json);
                }
            });

            // 3. Script summaries (filtered by relevance)
            List<ScriptMemory> scripts = repository.findScripts(projectId);
            List<String> scriptLines = new ArrayList<>();
            int scriptCount = 0;
            for (ScriptMemory s : scripts) {
                if (scriptCount >= maxScriptsInContext) break;

                // Include if goal mentions the class, path, or if no specific filter
                boolean relevant = goalLower.isEmpty()
                        || containsAny(goalLower, s.getClassName(), s.getScriptPath(), s.getAttachedObjects());

                if (relevant) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(nvl(s.getClassName(), extractFileName(s.getScriptPath())));
                    sb.append(" (").append(s.getScriptPath()).append(")");
                    if (s.getContentHash() != null) {
                        sb.append(" hash:").append(s.getContentHash().substring(0, Math.min(8, s.getContentHash().length())));
                    }
                    if (s.getAttachedObjects() != null && !s.getAttachedObjects().equals("[]")) {
                        sb.append(" → ").append(s.getAttachedObjects());
                    }
                    scriptLines.add(sb.toString());
                    scriptCount++;
                }
            }
            ctx.setScriptSummaries(scriptLines);

            // 4. Recent conversation history
            List<ConversationRecord> conversations = repository.findConversationsByProject(projectId, maxRecentConversations);
            List<String> historyLines = new ArrayList<>();
            for (ConversationRecord conv : conversations) {
                StringBuilder sb = new StringBuilder();
                sb.append("[").append(nvl(conv.getStatus(), "?")).append("] ");
                sb.append("\"").append(truncate(conv.getUserGoal(), 80)).append("\"");
                if (conv.getToolCount() > 0) sb.append(" — ").append(conv.getToolCount()).append(" tools");
                historyLines.add(sb.toString());
            }
            ctx.setRecentHistory(historyLines);

            // 5. Known issues (FAILURE entries)
            List<MemoryEntry> failures = repository.findMemoryEntries(projectId, "FAILURE");
            List<String> issueLines = failures.stream()
                    .map(f -> f.getKey() + ": " + truncate(f.getValue(), 150))
                    .limit(10)
                    .collect(Collectors.toList());
            ctx.setKnownIssues(issueLines);

            // 6. Preferences
            List<UserPreference> prefs = repository.findPreferences(projectId);
            for (UserPreference p : prefs) {
                ctx.getPreferences().put(p.getPreferenceKey(), p.getPreferenceValue());
            }

            // Enforce context size bound
            String formatted = ctx.format();
            if (formatted.length() > maxContextSize) {
                log.debug("Memory context exceeds max size ({}), truncating", formatted.length());
                // Reduce by removing history and issues first
                ctx.setKnownIssues(List.of());
                ctx.setRecentHistory(historyLines.stream().limit(2).collect(Collectors.toList()));
                ctx.setScriptSummaries(scriptLines.stream().limit(10).collect(Collectors.toList()));
            }

        } catch (Exception e) {
            log.warn("Error retrieving memory context for project {}: {}", projectId, e.getMessage());
            return null;
        }

        return ctx.hasContent() ? ctx : null;
    }

    private boolean containsAny(String text, String... candidates) {
        if (text == null || text.isBlank()) return false;
        String[] textWords = text.toLowerCase().split("[^a-zA-Z0-9]+");

        for (String c : candidates) {
            if (c != null && !c.isEmpty()) {
                String lower = c.toLowerCase();
                if (text.contains(lower) || lower.contains(text)) return true;

                // Check if any word from text appears in candidate
                for (String tw : textWords) {
                    if (tw.length() > 2 && lower.contains(tw)) {
                        return true;
                    }
                }

                // Check if any word from candidate appears in text
                for (String word : lower.split("[^a-zA-Z0-9]+")) {
                    if (word.length() > 2 && text.contains(word)) return true;
                }

                // Check camelCase words in candidate
                for (String cw : c.split("(?=[A-Z])|[^a-zA-Z0-9]+")) {
                    if (cw.length() > 2 && text.contains(cw.toLowerCase())) return true;
                }
            }
        }
        return false;
    }

    private String extractFileName(String path) {
        if (path == null) return "unknown";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String nvl(String s, String def) {
        return (s != null && !s.isEmpty()) ? s : def;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
