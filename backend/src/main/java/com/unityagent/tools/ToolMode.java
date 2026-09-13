package com.unityagent.tools;

/**
 * Declares the Unity execution mode in which a tool is allowed to run.
 */
public enum ToolMode {
    /** Tool is only permitted when Unity is in Editor edit mode. */
    EDITOR,

    /** Tool is only permitted when Unity is actively running Play Mode simulation. */
    PLAY_MODE,

    /** Tool is permitted in both Editor edit mode and Play Mode simulation. */
    BOTH
}
