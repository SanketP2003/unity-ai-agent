package com.unityagent.studio.model;

/**
 * High-level operational status of a Unity project workspace in the Studio.
 */
public enum ProjectStatus {
    READY,
    BUILDING,
    PAUSED,
    WAITING_FOR_UNITY,
    WAITING_FOR_PROVIDER,
    RECOVERING,
    FAILED,
    COMPLETED,
    DISCONNECTED
}
