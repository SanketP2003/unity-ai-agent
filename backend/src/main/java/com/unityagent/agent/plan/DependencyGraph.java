package com.unityagent.agent.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Dependency graph tracking relationships between GameObjects, components,
 * scripts, materials, and goal requirements in the Unity project.
 * Supports prerequisite resolution, cycle detection, and cascading invalidation.
 */
public class DependencyGraph {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraph.class);

    public static class DependencyEdge {
        private final String target;
        private final String relationship; // backward-compatible string
        private final DependencyType type;

        public DependencyEdge(String target, String relationship) {
            this.target = target;
            this.relationship = relationship;
            this.type = parseType(relationship);
        }

        public DependencyEdge(String target, DependencyType type) {
            this.target = target;
            this.type = type != null ? type : DependencyType.REQUIRES;
            this.relationship = this.type.name();
        }

        private static DependencyType parseType(String rel) {
            if (rel == null) return DependencyType.REQUIRES;
            try {
                return DependencyType.valueOf(rel.toUpperCase());
            } catch (IllegalArgumentException e) {
                return DependencyType.REQUIRES;
            }
        }

        public String getTarget() { return target; }
        public String getRelationship() { return relationship; }
        public DependencyType getType() { return type; }

        @Override
        public String toString() {
            return target + " (" + type + ")";
        }
    }

    private final Map<String, DependencyNode> nodes = new ConcurrentHashMap<>();
    // fromNode -> list of edges pointing to targets (things fromNode depends on / modifies / produces)
    private final Map<String, List<DependencyEdge>> dependencies = new ConcurrentHashMap<>();
    // toNode -> list of nodes that depend on toNode
    private final Map<String, List<String>> dependents = new ConcurrentHashMap<>();

    public void addNode(DependencyNode node) {
        if (node == null || node.getNodeId() == null) return;
        nodes.put(node.getNodeId().toLowerCase(), node);
    }

    public DependencyNode getNode(String nodeId) {
        if (nodeId == null) return null;
        return nodes.get(nodeId.toLowerCase());
    }

    public void addDependency(String from, String to, String relationship) {
        if (from == null || to == null || from.equalsIgnoreCase(to)) return;

        ensureNodeExists(from);
        ensureNodeExists(to);

        dependencies.computeIfAbsent(from.toLowerCase(), k -> new ArrayList<>())
                .add(new DependencyEdge(to.toLowerCase(), relationship));

        dependents.computeIfAbsent(to.toLowerCase(), k -> new ArrayList<>())
                .add(from.toLowerCase());
    }

    public void addDependency(String from, String to, DependencyType type) {
        if (from == null || to == null || from.equalsIgnoreCase(to)) return;

        ensureNodeExists(from);
        ensureNodeExists(to);

        dependencies.computeIfAbsent(from.toLowerCase(), k -> new ArrayList<>())
                .add(new DependencyEdge(to.toLowerCase(), type));

        dependents.computeIfAbsent(to.toLowerCase(), k -> new ArrayList<>())
                .add(from.toLowerCase());
    }

    public void removeDependency(String from, String to) {
        if (from == null || to == null) return;
        String fromKey = from.toLowerCase();
        String toKey = to.toLowerCase();

        List<DependencyEdge> edges = dependencies.get(fromKey);
        if (edges != null) {
            edges.removeIf(e -> e.getTarget().equalsIgnoreCase(toKey));
        }

        List<String> deps = dependents.get(toKey);
        if (deps != null) {
            deps.removeIf(d -> d.equalsIgnoreCase(fromKey));
        }
    }

    public List<DependencyEdge> getDependencies(String node) {
        if (node == null) return List.of();
        return Collections.unmodifiableList(dependencies.getOrDefault(node.toLowerCase(), List.of()));
    }

    public List<String> getDependents(String node) {
        if (node == null) return List.of();
        return Collections.unmodifiableList(dependents.getOrDefault(node.toLowerCase(), List.of()));
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
        nodes.remove(key);
        dependencies.remove(key);
        dependents.remove(key);

        for (List<DependencyEdge> edges : dependencies.values()) {
            edges.removeIf(e -> e.getTarget().equalsIgnoreCase(key));
        }
        for (List<String> depList : dependents.values()) {
            depList.remove(key);
        }
    }

    /**
     * Determines which nodes are currently ready to execute or be constructed:
     * A node is ready if it is not yet satisfied, not invalidated, and ALL its REQUIRES dependencies are satisfied.
     */
    public List<DependencyNode> getReadyNodes() {
        List<DependencyNode> ready = new ArrayList<>();
        for (DependencyNode node : nodes.values()) {
            if (node.isSatisfied() || node.isInvalidated()) {
                continue;
            }

            List<DependencyEdge> edges = dependencies.getOrDefault(node.getNodeId().toLowerCase(), List.of());
            boolean allPrereqsSatisfied = true;
            for (DependencyEdge edge : edges) {
                if (edge.getType() == DependencyType.REQUIRES) {
                    DependencyNode prereqNode = nodes.get(edge.getTarget().toLowerCase());
                    if (prereqNode == null || !prereqNode.isSatisfied() || prereqNode.isInvalidated()) {
                        allPrereqsSatisfied = false;
                        break;
                    }
                }
            }

            if (allPrereqsSatisfied) {
                ready.add(node);
            }
        }
        return ready;
    }

    /**
     * Detects if any cycles exist in the directed dependency graph.
     *
     * @return true if a cycle is detected, false if graph is a valid DAG
     */
    public boolean detectCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : nodes.keySet()) {
            if (detectCycleUtil(node, visited, recursionStack)) {
                log.warn("Cycle detected in DependencyGraph involving node: {}", node);
                return true;
            }
        }
        return false;
    }

    private boolean detectCycleUtil(String node, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }

        visited.add(node);
        recursionStack.add(node);

        List<DependencyEdge> edges = dependencies.getOrDefault(node, List.of());
        for (DependencyEdge edge : edges) {
            if (edge.getType() == DependencyType.REQUIRES) {
                if (detectCycleUtil(edge.getTarget(), visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(node);
        return false;
    }

    /**
     * Cascadingly invalidates a node and all downstream nodes that depend on it.
     */
    public Set<String> invalidateNode(String nodeId) {
        if (nodeId == null) return Set.of();
        Set<String> invalidated = new LinkedHashSet<>();
        invalidateRecursive(nodeId.toLowerCase(), invalidated);
        log.info("Invalidated node '{}' and {} dependent downstream nodes: {}", nodeId, invalidated.size() - 1, invalidated);
        return invalidated;
    }

    private void invalidateRecursive(String current, Set<String> invalidated) {
        if (invalidated.contains(current)) return;
        invalidated.add(current);

        DependencyNode node = nodes.get(current);
        if (node != null) {
            node.setInvalidated(true);
            node.setSatisfied(false);
        }

        List<String> downstreams = dependents.getOrDefault(current, List.of());
        for (String down : downstreams) {
            invalidateRecursive(down, invalidated);
        }
    }

    public Collection<DependencyNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public void clear() {
        nodes.clear();
        dependencies.clear();
        dependents.clear();
    }

    private void ensureNodeExists(String id) {
        nodes.computeIfAbsent(id.toLowerCase(), k -> new DependencyNode(id, id, DependencyNode.NodeCategory.SYSTEM));
    }
}
