package com.unityagent.memory.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact, bounded context for LLM injection.
 * Contains relevant project memory formatted as text.
 * Output is bounded by max-context-size to prevent prompt overflow.
 */
public class MemoryContext {

    private String projectSummary;
    private String architectureSummary;
    private List<String> scriptSummaries = new ArrayList<>();
    private List<String> recentHistory = new ArrayList<>();
    private List<String> knownIssues = new ArrayList<>();
    private Map<String, String> preferences = new LinkedHashMap<>();

    public boolean hasContent() {
        return (projectSummary != null && !projectSummary.isEmpty())
                || (architectureSummary != null && !architectureSummary.isEmpty())
                || !scriptSummaries.isEmpty()
                || !recentHistory.isEmpty()
                || !knownIssues.isEmpty();
    }

    /**
     * Format as compact text for LLM injection.
     * Bounded to prevent exceeding context limits.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();

        if (projectSummary != null && !projectSummary.isEmpty()) {
            sb.append("=== PROJECT ===\n");
            sb.append(projectSummary).append("\n\n");
        }

        if (architectureSummary != null && !architectureSummary.isEmpty()) {
            sb.append("=== ARCHITECTURE ===\n");
            sb.append(architectureSummary).append("\n\n");
        }

        if (!scriptSummaries.isEmpty()) {
            sb.append("=== SCRIPTS ===\n");
            for (String s : scriptSummaries) {
                sb.append(s).append("\n");
            }
            sb.append("\n");
        }

        if (!recentHistory.isEmpty()) {
            sb.append("=== RECENT HISTORY ===\n");
            for (String h : recentHistory) {
                sb.append(h).append("\n");
            }
            sb.append("\n");
        }

        if (!knownIssues.isEmpty()) {
            sb.append("=== KNOWN ISSUES ===\n");
            for (String issue : knownIssues) {
                sb.append(issue).append("\n");
            }
            sb.append("\n");
        }

        if (!preferences.isEmpty()) {
            sb.append("=== PREFERENCES ===\n");
            for (Map.Entry<String, String> e : preferences.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Rough token estimate (chars / 4).
     */
    public int estimateTokens() {
        return format().length() / 4;
    }

    // ── Setters ────────────────────────────────────────────────────────

    public String getProjectSummary() { return projectSummary; }
    public void setProjectSummary(String projectSummary) { this.projectSummary = projectSummary; }

    public String getArchitectureSummary() { return architectureSummary; }
    public void setArchitectureSummary(String architectureSummary) { this.architectureSummary = architectureSummary; }

    public List<String> getScriptSummaries() { return scriptSummaries; }
    public void setScriptSummaries(List<String> scriptSummaries) { this.scriptSummaries = scriptSummaries; }

    public List<String> getRecentHistory() { return recentHistory; }
    public void setRecentHistory(List<String> recentHistory) { this.recentHistory = recentHistory; }

    public List<String> getKnownIssues() { return knownIssues; }
    public void setKnownIssues(List<String> knownIssues) { this.knownIssues = knownIssues; }

    public Map<String, String> getPreferences() { return preferences; }
    public void setPreferences(Map<String, String> preferences) { this.preferences = preferences; }
}
