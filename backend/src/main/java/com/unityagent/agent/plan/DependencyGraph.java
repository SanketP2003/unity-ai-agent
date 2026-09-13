package com.unityagent.agent.plan;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight dependency graph tracking relationships between GameObjects,
 * components, scripts, materials, and prefabs in the Unity project.
 * Helps the planner reason about build order and prevents destructive modifications.
 */
public class DependencyGraph {

    public static class DependencyEdge {
        private final String target;
        private final String relationship; // e.g., "REQUIRES_COMPONENT", "TARGETS", "FOLLOWS", "USES_MATERIAL"

        public DependencyEdge(String target, String relationship) {
            this.target = target;
            this.relationship = relationship;
        }

        public String getTarget() { return target; }
        public String getRelationship() { return relationship; }
    }

    // Node -> list of outgoing edges (things this node depends on)
    private final Map<String, List<DependencyEdge>> dependencies = new ConcurrentHashMap<>();
    // Node -> list of incoming edges (things that depend on this node)
    private final Map<String, List<String>> dependents = new ConcurrentHashMap<>();

    public void addDependency(String from, String to, String relationship) {
        if (from == null || to == null || from.equalsIgnoreCase(to)) return;

        dependencies.computeIfAbsent(from.toLowerCase(), k -> new ArrayList<>())
                .add(new DependencyEdge(to.toLowerCase(), relationship));

        dependents.computeIfAbsent(to.toLowerCase(), k -> new ArrayList<>())
                .add(from.toLowerCase());
    }

    public List<DependencyEdge> getDependencies(String node) {
        if (node == null) return List.of();
        return dependencies.getOrDefault(node.toLowerCase(), List.of());
    }

    public List<String> getDependents(String node) {
        if (node == null) return List.of();
        return dependents.getOrDefault(node.toLowerCase(), List.of());
    }

    public boolean isSafeToDelete(String node) {
        if (node == null) return true;
        List<String> deps = dependents.get(node.toLowerCase());
        return deps == null || deps.isEmpty();
    }

    public String explainDependencies(String node) {
        if (node == null) return "None";
        List<String> deps = dependents.get(node.toLowerCase());
        if (deps == null || deps.isEmpty()) return "No other components or objects depend on '" + node + "'";
        return "Warning: The following objects depend on '" + node + "': " + String.join(", ", deps);
    }

    public void removeNode(String node) {
        if (node == null) return;
        String key = node.toLowerCase();
        dependencies.remove(key);
        dependents.remove(key);

        for (List<DependencyEdge> edges : dependencies.values()) {
            edges.removeIf(e -> e.getTarget().equalsIgnoreCase(key));
        }
        for (List<String> depList : dependents.values()) {
            depList.remove(key);
        }
    }

    public void clear() {
        dependencies.clear();
        dependents.clear();
    }
}
