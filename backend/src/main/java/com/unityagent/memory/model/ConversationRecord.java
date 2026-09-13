package com.unityagent.memory.model;

import java.time.Instant;

/**
 * Record of a completed agent conversation/run.
 * Stores extracted summary facts, not raw conversation history.
 */
public class ConversationRecord {

    private long id;
    private String sessionId;
    private String projectId;
    private String agentRunId;
    private String userGoal;
    private String status;       // COMPLETED | FAILED | CANCELLED
    private int toolCount;
    private String summary;      // JSON — extracted facts
    private Instant createdAt;
    private Instant completedAt;

    public ConversationRecord() {
        this.createdAt = Instant.now();
    }

    public ConversationRecord(String sessionId, String projectId, String agentRunId,
                               String userGoal, String status, int toolCount, String summary) {
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.agentRunId = agentRunId;
        this.userGoal = userGoal;
        this.status = status;
        this.toolCount = toolCount;
        this.summary = summary;
        this.createdAt = Instant.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }

    public String getUserGoal() { return userGoal; }
    public void setUserGoal(String userGoal) { this.userGoal = userGoal; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getToolCount() { return toolCount; }
    public void setToolCount(int toolCount) { this.toolCount = toolCount; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
