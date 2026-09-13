package com.unityagent.agent.goal;

/**
 * Lifecycle states for an objective GoalRequirement.
 */
public enum RequirementStatus {
    PENDING,
    READY,
    IN_PROGRESS,
    SATISFIED,
    FAILED,
    BLOCKED,
    STALE
}
