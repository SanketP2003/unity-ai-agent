package com.unityagent.agent.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("9.3 DependencyGraph Operations & Cycle Detection Tests")
class DependencyGraphTest {

    @Test
    @DisplayName("Nodes and dependencies tracking with ready node progression")
    void testNodeReadinessProgression() {
        DependencyGraph graph = new DependencyGraph();

        DependencyNode player = new DependencyNode("Player", "Player Character", DependencyNode.NodeCategory.GAMEOBJECT);
        DependencyNode playerController = new DependencyNode("PlayerController", "Movement Script", DependencyNode.NodeCategory.SCRIPT);
        DependencyNode input = new DependencyNode("Input", "Input System", DependencyNode.NodeCategory.SYSTEM);

        graph.addNode(player);
        graph.addNode(playerController);
        graph.addNode(input);

        // PlayerController REQUIRES Input
        graph.addDependency("PlayerController", "Input", DependencyType.REQUIRES);
        // Player REQUIRES PlayerController
        graph.addDependency("Player", "PlayerController", DependencyType.REQUIRES);

        // Initially: only Input is ready because it has no dependencies
        List<DependencyNode> ready = graph.getReadyNodes();
        assertEquals(1, ready.size());
        assertEquals("input", ready.get(0).getNodeId().toLowerCase());

        // Mark Input satisfied
        input.setSatisfied(true);

        // Now PlayerController should be ready
        ready = graph.getReadyNodes();
        assertEquals(1, ready.size());
        assertEquals("playercontroller", ready.get(0).getNodeId().toLowerCase());

        // Mark PlayerController satisfied
        playerController.setSatisfied(true);

        // Now Player should be ready
        ready = graph.getReadyNodes();
        assertEquals(1, ready.size());
        assertEquals("player", ready.get(0).getNodeId().toLowerCase());
    }

    @Test
    @DisplayName("Cycle detection catches circular dependencies")
    void testCycleDetection() {
        DependencyGraph graph = new DependencyGraph();

        graph.addDependency("NodeA", "NodeB", DependencyType.REQUIRES);
        graph.addDependency("NodeB", "NodeC", DependencyType.REQUIRES);
        assertFalse(graph.detectCycles(), "A -> B -> C should be a valid DAG without cycles");

        // Add cycle: C -> A
        graph.addDependency("NodeC", "NodeA", DependencyType.REQUIRES);
        assertTrue(graph.detectCycles(), "A -> B -> C -> A contains a cycle");
    }

    @Test
    @DisplayName("Cascading node invalidation invalidates all downstream dependents")
    void testCascadingInvalidation() {
        DependencyGraph graph = new DependencyGraph();

        DependencyNode ground = new DependencyNode("Ground", "Ground", DependencyNode.NodeCategory.GAMEOBJECT);
        DependencyNode player = new DependencyNode("Player", "Player", DependencyNode.NodeCategory.GAMEOBJECT);
        DependencyNode enemy = new DependencyNode("Enemy", "Enemy", DependencyNode.NodeCategory.GAMEOBJECT);

        graph.addNode(ground);
        graph.addNode(player);
        graph.addNode(enemy);

        ground.setSatisfied(true);
        player.setSatisfied(true);
        enemy.setSatisfied(true);

        // Both player and enemy depend on ground
        graph.addDependency("Player", "Ground", DependencyType.REQUIRES);
        graph.addDependency("Enemy", "Ground", DependencyType.REQUIRES);

        // Invalidate ground
        Set<String> invalidated = graph.invalidateNode("Ground");
        assertTrue(invalidated.contains("ground"));
        assertTrue(invalidated.contains("player"));
        assertTrue(invalidated.contains("enemy"));

        assertFalse(ground.isSatisfied());
        assertTrue(ground.isInvalidated());
        assertFalse(player.isSatisfied());
        assertTrue(player.isInvalidated());
        assertFalse(enemy.isSatisfied());
        assertTrue(enemy.isInvalidated());
    }

    @Test
    @DisplayName("Safety checks and dependency explanation work correctly")
    void testSafetyAndExplanation() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency("HealthUI", "HealthSystem", "REQUIRES");

        assertFalse(graph.isSafeToDelete("HealthSystem"));
        assertTrue(graph.isSafeToDelete("HealthUI"));

        String explanation = graph.explainDependencies("HealthSystem");
        assertTrue(explanation.contains("HealthUI") || explanation.contains("healthui"));

        graph.removeNode("HealthUI");
        assertTrue(graph.isSafeToDelete("HealthSystem"));
    }
}
