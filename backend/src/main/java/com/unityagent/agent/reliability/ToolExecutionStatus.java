package com.unityagent.agent.reliability;

/**
 * Status lifecycle of a tool execution command sent to the Unity engine.
 */
public enum ToolExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    UNKNOWN
}
