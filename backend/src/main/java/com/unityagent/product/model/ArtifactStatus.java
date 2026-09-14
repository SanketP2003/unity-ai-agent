package com.unityagent.product.model;

/**
 * Verification and lifecycle status of a build artifact.
 */
public enum ArtifactStatus {
    PENDING,
    VERIFIED,
    CORRUPTED,
    DEPRECATED
}
