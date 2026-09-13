package com.unityagent.agent.model;

/**
 * Lifecycle state of an autonomous Unity development agent run.
 */
public enum AgentState {
    IDLE,
    PLANNING,
    INSPECTING,
    EXECUTING,
    COMPILING,
    DIAGNOSING,
    REPAIRING,
    VALIDATING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
