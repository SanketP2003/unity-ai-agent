package com.unityagent.agent.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Explicit directed edge between PlanNodes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanDependency {

    private String fromNodeId;
    private String toNodeId;
    private DependencyType dependencyType;

    public PlanDependency() {
        this.dependencyType = DependencyType.REQUIRES;
    }

    public PlanDependency(String fromNodeId, String toNodeId, DependencyType dependencyType) {
        this.fromNodeId = Objects.requireNonNull(fromNodeId, "fromNodeId cannot be null");
        this.toNodeId = Objects.requireNonNull(toNodeId, "toNodeId cannot be null");
        this.dependencyType = dependencyType != null ? dependencyType : DependencyType.REQUIRES;
    }

    public String getFromNodeId() { return fromNodeId; }
    public void setFromNodeId(String fromNodeId) { this.fromNodeId = fromNodeId; }

    public String getToNodeId() { return toNodeId; }
    public void setToNodeId(String toNodeId) { this.toNodeId = toNodeId; }

    public DependencyType getDependencyType() { return dependencyType; }
    public void setDependencyType(DependencyType dependencyType) { this.dependencyType = dependencyType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanDependency that = (PlanDependency) o;
        return Objects.equals(fromNodeId, that.fromNodeId) &&
                Objects.equals(toNodeId, that.toNodeId) &&
                dependencyType == that.dependencyType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromNodeId, toNodeId, dependencyType);
    }

    @Override
    public String toString() {
        return fromNodeId + " --[" + dependencyType + "]--> " + toNodeId;
    }
}
