package com.unityagent.agent.plan;

/**
 * Categorized relationship types between dependency nodes in the autonomous Unity pipeline.
 */
public enum DependencyType {
    REQUIRES,
    PRODUCES,
    MODIFIES,
    INVALIDATES
}
