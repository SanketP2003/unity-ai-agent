package com.unityagent.agent.model;

/**
 * Standard classification for errors during agent and tool execution.
 * Enables the AgentLoop and LLM to distinguish recoverable parameter/permission
 * issues from infrastructure failures.
 */
public enum ErrorType {
    UNKNOWN_TOOL,
    VALIDATION_ERROR,
    PERMISSION_DENIED,
    MODE_NOT_ALLOWED,
    UNITY_EXECUTION_ERROR,
    UNITY_CONNECTION_ERROR,
    TIMEOUT,
    PROVIDER_ERROR,
    CANCELLED,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    UNITY_TOOL_ERROR,
    INVALID_PROJECT_STATE,
    VALIDATION_FAILURE,
    SCRIPT_SAFETY_VIOLATION,
    SCRIPT_CONFLICT,
    MODEL_CAPABILITY_UNSUPPORTED
}
