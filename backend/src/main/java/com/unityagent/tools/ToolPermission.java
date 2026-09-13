package com.unityagent.tools;

/**
 * Permission levels governing tool execution safety.
 * Supports granular Phase 7 permission tiers while maintaining backwards-compatibility.
 */
public enum ToolPermission {
    /** Pure read-only inspection operations (e.g., get_scene_hierarchy, get_asset_info). */
    READ_ONLY,

    /** Safe write operations that create or modify scene objects non-destructively. */
    SAFE_WRITE,

    /** Project-level writes that create or update scripts, build settings, or project config. */
    PROJECT_WRITE,

    /** Destructive operations that delete or irreversibly destroy assets or scene objects. */
    DESTRUCTIVE,

    /** Build pipeline triggers that produce standalone executables. */
    BUILD,

    /** Runtime simulation, Play Mode triggers, and behavioral tests. */
    RUNTIME,

    // --- Phase 6 Backwards-Compatibility Aliases ---
    /** Legacy safe operations (mapped conceptually to SAFE_WRITE / READ_ONLY). */
    SAFE,

    /** Operations requiring supervised user consent in supervised mode. */
    SUPERVISED,

    /** Operations completely blocked from automated agent execution. */
    BLOCKED;

    public boolean isReadOnly() {
        return this == READ_ONLY;
    }

    public boolean isDestructiveLevel() {
        return this == DESTRUCTIVE;
    }

    public boolean isBlocked() {
        return this == BLOCKED;
    }
}

