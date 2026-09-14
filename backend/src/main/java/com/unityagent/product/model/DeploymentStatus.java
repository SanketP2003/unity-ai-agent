package com.unityagent.product.model;

/**
 * Lifecycle states of an artifact deployment.
 */
public enum DeploymentStatus {
    QUEUED,
    DEPLOYING,
    SUCCEEDED,
    FAILED,
    ROLLED_BACK
}
