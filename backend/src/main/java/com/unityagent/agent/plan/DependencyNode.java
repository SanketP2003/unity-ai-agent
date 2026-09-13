package com.unityagent.agent.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Node in the autonomous agent's DependencyGraph.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DependencyNode {

    public enum NodeCategory {
        GAMEOBJECT,
        SCRIPT,
        COMPONENT,
        SYSTEM,
        REQUIREMENT,
        ASSET,
        SCENE
    }

    private String nodeId;
    private String label;
    private NodeCategory category;
    private boolean satisfied;
    private boolean invalidated;
    private Map<String, Object> metadata;

    public DependencyNode() {
        this.metadata = new LinkedHashMap<>();
        this.satisfied = false;
        this.invalidated = false;
    }

    public DependencyNode(String nodeId, String label, NodeCategory category) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.label = label != null ? label : nodeId;
        this.category = category != null ? category : NodeCategory.SYSTEM;
        this.satisfied = false;
        this.invalidated = false;
        this.metadata = new LinkedHashMap<>();
    }

    public static DependencyNode of(String nodeId, NodeCategory category) {
        return new DependencyNode(nodeId, nodeId, category);
    }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public NodeCategory getCategory() { return category; }
    public void setCategory(NodeCategory category) { this.category = category; }

    public boolean isSatisfied() { return satisfied; }
    public void setSatisfied(boolean satisfied) { this.satisfied = satisfied; }

    public boolean isInvalidated() { return invalidated; }
    public void setInvalidated(boolean invalidated) { this.invalidated = invalidated; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata != null ? metadata : new LinkedHashMap<>(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyNode that = (DependencyNode) o;
        return Objects.equals(nodeId.toLowerCase(), that.nodeId.toLowerCase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId.toLowerCase());
    }

    @Override
    public String toString() {
        return "DependencyNode{" +
                "id='" + nodeId + '\'' +
                ", cat=" + category +
                ", satisfied=" + satisfied +
                ", invalidated=" + invalidated +
                '}';
    }
}
