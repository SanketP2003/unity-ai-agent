package com.unityagent.agent.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.unityagent.agent.CancellationToken;
import com.unityagent.agent.event.AgentEvent;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.model.AgentState;
import com.unityagent.agent.plan.AgentPlan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Authoritative in-memory state tracking for an autonomous agent run.
 * Serves as the source of truth for REST queries (GET /api/agent/run/{id})
 * and reconnecting SSE clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentRunState {

    public enum Status {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final String agentRunId;
    private final String sessionId;
    private final String userMessage;
    private volatile String projectId;
    private volatile Status status;
    private volatile AgentState agentState = AgentState.IDLE;
    private volatile GameGoal goal;
    private volatile AgentPlan plan;
    private volatile String currentActivity;
    private final List<AgentEvent> events;
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    @JsonIgnore
    private final CancellationToken cancellationToken;

    private volatile AgentRunResult result;
    private volatile String errorMessage;
    private final Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant completedAt;

    public AgentRunState(String agentRunId, String sessionId, String userMessage, CancellationToken cancellationToken) {
        this.agentRunId = agentRunId;
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.cancellationToken = cancellationToken != null ? cancellationToken : new CancellationToken();
        this.status = Status.RUNNING;
        this.currentActivity = "Thinking...";
        this.events = new CopyOnWriteArrayList<>();
        this.createdAt = Instant.now();
        this.startedAt = Instant.now();
    }

    public long nextSequence() {
        return sequenceCounter.incrementAndGet();
    }

    public void addEvent(AgentEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    public void markRunning() {
        this.status = Status.RUNNING;
        this.startedAt = Instant.now();
        this.currentActivity = "Thinking...";
    }

    public void markCompleted(AgentRunResult result) {
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
        this.result = result;
        this.currentActivity = "Completed";
    }

    public void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
        this.currentActivity = "Failed: " + errorMessage;
    }

    public void markCancelled() {
        this.status = Status.CANCELLED;
        this.completedAt = Instant.now();
        this.currentActivity = "Cancelled";
        if (cancellationToken != null) {
            cancellationToken.cancel();
        }
    }

    public String getAgentRunId() { return agentRunId; }
    public String getSessionId() { return sessionId; }
    public String getUserMessage() { return userMessage; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public Status getStatus() { return status; }
    public AgentState getAgentState() { return agentState; }
    public void setAgentState(AgentState agentState) { this.agentState = agentState; }
    public GameGoal getGoal() { return goal; }
    public void setGoal(GameGoal goal) { this.goal = goal; }
    public AgentPlan getPlan() { return plan; }
    public void setPlan(AgentPlan plan) { this.plan = plan; }
    public String getCurrentActivity() { return currentActivity; }
    public void setCurrentActivity(String activity) { this.currentActivity = activity; }
    public List<AgentEvent> getEvents() { return new ArrayList<>(events); }
    public CancellationToken getCancellationToken() { return cancellationToken; }
    public AgentRunResult getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public boolean isActive() {
        return status == Status.QUEUED || status == Status.RUNNING;
    }
}
