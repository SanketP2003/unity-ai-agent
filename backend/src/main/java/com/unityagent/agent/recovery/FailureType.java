package com.unityagent.agent.recovery;

/**
 * Granular taxonomy of failure modes encountered during autonomous Unity development.
 */
public enum FailureType {
    // Compiler errors
    COMPILATION_SYNTAX,     // e.g. CS1002 (missing semicolon), CS1513 (closing brace)
    COMPILATION_TYPE,       // e.g. CS0246 (type not found), CS0103 (name does not exist)
    COMPILATION_MEMBER,     // e.g. CS1061 (member does not exist), CS0117 (no definition)
    COMPILATION_CONVERSION, // e.g. CS0029 (cannot implicitly convert type)

    // Reference and asset errors
    MISSING_DEPENDENCY,     // Referenced script, prefab, or material not found
    MISSING_COMPONENT,      // MissingComponentException or required component not on GameObject
    SCENE_DRIFT,            // GameObject moved, renamed, destroyed, or instance ID changed

    // Runtime errors
    RUNTIME_NULL_REF,       // NullReferenceException in play mode or test
    RUNTIME_ASSERTION,      // AssertionException or test assertion failed

    // Behavioral & Physics errors
    BEHAVIOR_TIMEOUT,       // Entity failed to respond or reach target within time window
    BEHAVIOR_PHYSICS,       // Falling through floor, no collision, unexpected velocity

    // System & Validation
    TOOL_EXECUTION_ERROR,   // Bridge disconnect, tool failure, invalid JSON
    VALIDATION_FAILED,      // Criteria validation failed in ObjectiveValidator
    UNKNOWN                 // Unclassified
}
