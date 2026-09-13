package com.unityagent.agent.verification;

/**
 * Objective verification types for machine-verifiable requirements.
 */
public enum VerificationType {
    GAME_OBJECT_EXISTS,
    GAME_OBJECT_COUNT,
    COMPONENT_EXISTS,
    COMPONENT_PROPERTY,
    SCRIPT_EXISTS,
    SCRIPT_ATTACHED,
    COMPILE_SUCCESS,
    NO_RUNTIME_ERRORS,
    BEHAVIOR_TEST,
    SCENE_STATE,
    GAMEPLAY_STATE
}
