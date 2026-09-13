package com.unityagent.memory.model;

/**
 * Memory freshness classification.
 * Used to determine whether a memory entry should be trusted or re-verified.
 */
public enum MemoryFreshness {
    /** Recently verified against Unity state. */
    FRESH,
    /** Not verified within the staleness threshold. */
    STALE,
    /** Never verified or verification status unknown. */
    UNKNOWN
}
