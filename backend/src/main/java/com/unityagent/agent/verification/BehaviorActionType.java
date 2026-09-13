package com.unityagent.agent.verification;

/**
 * Action primitives for automated game behavioral testing in Unity play mode.
 */
public enum BehaviorActionType {
    SPAWN_PREFAB,
    SIMULATE_INPUT,
    TRIGGER_COLLISION,
    WAIT_SECONDS,
    OBSERVE_TRANSFORM,
    OBSERVE_COMPONENT_FIELD,
    ASSERT_EVENT,
    ASSERT_LOG_ABSENCE,
    ASSERT_POSITION_DELTA
}
