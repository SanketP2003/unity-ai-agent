package com.unityagent.studio.model;

/**
 * Types of changes tracked in change sets.
 */
public enum ChangeType {
    FILE_CREATE,
    FILE_MODIFY,
    FILE_DELETE,
    SCENE_OBJECT_CREATE,
    SCENE_OBJECT_MODIFY,
    SCENE_OBJECT_DELETE,
    COMPONENT_ADD,
    COMPONENT_MODIFY,
    ASSET_IMPORT
}
