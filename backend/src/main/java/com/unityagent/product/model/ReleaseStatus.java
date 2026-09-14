package com.unityagent.product.model;

/**
 * Lifecycle states of a game release.
 */
public enum ReleaseStatus {
    DRAFT,
    BUILDING,
    VALIDATING,
    READY_FOR_REVIEW,
    APPROVED,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    ROLLED_BACK,
    ARCHIVED
}
