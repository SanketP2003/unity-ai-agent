package com.unityagent.agent.goal;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Goal representation for an autonomous agent run.
 * Tracks high-level objective and granular, verifiable requirements.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameGoal {

    public enum GoalStatus {
        ACTIVE,
        VERIFYING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private String goalId;
    private String sessionId;
    private String description;
    private GoalStatus status;
    private List<GoalRequirement> requirements;
    private Instant createdAt;
    private Instant updatedAt;

    public GameGoal() {
        this.requirements = new ArrayList<>();
        this.status = GoalStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public GameGoal(String goalId, String sessionId, String description) {
        this.goalId = goalId != null ? goalId : "goal_" + UUID.randomUUID().toString().substring(0, 8);
        this.sessionId = sessionId;
        this.description = description;
        this.status = GoalStatus.ACTIVE;
        this.requirements = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getGoalId() { return goalId; }
    public void setGoalId(String goalId) { this.goalId = goalId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public GoalStatus getStatus() { return status; }
    public void setStatus(GoalStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public List<GoalRequirement> getRequirements() { return requirements; }
    public void setRequirements(List<GoalRequirement> requirements) {
        this.requirements = requirements != null ? new ArrayList<>(requirements) : new ArrayList<>();
        this.updatedAt = Instant.now();
    }

    public void addRequirement(GoalRequirement requirement) {
        if (requirement != null) {
            this.requirements.add(requirement);
            this.updatedAt = Instant.now();
        }
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean allRequirementsSatisfied() {
        if (requirements.isEmpty()) {
            return false;
        }
        return requirements.stream().allMatch(r -> r.getStatus() == GoalRequirement.RequirementStatus.SATISFIED);
    }
}
