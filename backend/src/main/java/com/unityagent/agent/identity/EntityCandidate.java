package com.unityagent.agent.identity;

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of an existing or target entity (GameObject or asset) in Unity.
 */
public class EntityCandidate {

    private String entityId;
    private String name;
    private String hierarchyPath;
    private List<String> components;
    private String scriptName;
    private String assetGuid;
    private String tag;

    public EntityCandidate() {
        this.components = new ArrayList<>();
    }

    public EntityCandidate(String entityId, String name, String hierarchyPath, List<String> components) {
        this.entityId = entityId;
        this.name = name;
        this.hierarchyPath = hierarchyPath;
        this.components = components != null ? new ArrayList<>(components) : new ArrayList<>();
    }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHierarchyPath() { return hierarchyPath; }
    public void setHierarchyPath(String hierarchyPath) { this.hierarchyPath = hierarchyPath; }

    public List<String> getComponents() { return components; }
    public void setComponents(List<String> components) {
        this.components = components != null ? new ArrayList<>(components) : new ArrayList<>();
    }

    public String getScriptName() { return scriptName; }
    public void setScriptName(String scriptName) { this.scriptName = scriptName; }

    public String getAssetGuid() { return assetGuid; }
    public void setAssetGuid(String assetGuid) { this.assetGuid = assetGuid; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
}
