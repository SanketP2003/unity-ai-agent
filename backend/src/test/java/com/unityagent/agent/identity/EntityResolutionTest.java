package com.unityagent.agent.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityResolutionTest {

    private EntityResolutionService service;

    @BeforeEach
    void setUp() {
        service = new EntityResolutionService();
    }

    @Test
    @DisplayName("Exact match reuses existing entity without duplicate creation")
    void testExactMatchReusesExistingObject() {
        EntityCandidate target = new EntityCandidate(
                null,
                "Player",
                "/Player",
                List.of("Transform", "Rigidbody", "PlayerController", "CapsuleCollider")
        );
        target.setScriptName("PlayerController");

        EntityCandidate existing = new EntityCandidate(
                "obj_101",
                "Player",
                "/Player",
                List.of("Transform", "Rigidbody", "PlayerController", "CapsuleCollider")
        );
        existing.setScriptName("PlayerController");

        EntityResolutionResult result = service.resolveEntity(target, List.of(existing), Set.of("Player"));

        assertNotNull(result);
        assertEquals(EntityResolutionResult.MatchType.EXACT_MATCH, result.getMatchType());
        assertTrue(result.shouldReuse());
        assertFalse(result.shouldCreateNew());
        assertEquals("obj_101", result.getResolvedEntityId());
        assertEquals("Player", result.getMatchedName());
        assertTrue(result.getConfidence() >= 0.85);
    }

    @Test
    @DisplayName("Partial match suggests adapting existing entity")
    void testPartialMatchSuggestsAdaptation() {
        EntityCandidate target = new EntityCandidate(
                null,
                "EnemyOrc",
                "/Enemies/EnemyOrc",
                List.of("Transform", "Rigidbody", "EnemyAI", "CapsuleCollider")
        );
        target.setScriptName("EnemyAI");

        // Existing entity has same name and Transform/Rigidbody, but missing EnemyAI script
        EntityCandidate existing = new EntityCandidate(
                "obj_202",
                "EnemyOrc",
                "/Enemies/EnemyOrc",
                List.of("Transform", "Rigidbody")
        );

        EntityResolutionResult result = service.resolveEntity(target, List.of(existing), Set.of("EnemyOrc"));

        assertNotNull(result);
        // Because exact name matches, it's either exact or partial match depending on components
        assertTrue(result.getConfidence() >= 0.50);
        assertTrue(result.shouldReuse() || result.shouldAdapt());
        assertEquals("obj_202", result.getResolvedEntityId());
    }

    @Test
    @DisplayName("No match suggests creating new entity with disambiguated name")
    void testNoMatchSuggestsCreation() {
        EntityCandidate target = new EntityCandidate(
                null,
                "TreasureChest",
                "/Interactables/TreasureChest",
                List.of("Transform", "BoxCollider", "ChestController")
        );

        EntityCandidate existing = new EntityCandidate(
                "obj_303",
                "MainCamera",
                "/MainCamera",
                List.of("Transform", "Camera", "AudioListener")
        );

        EntityResolutionResult result = service.resolveEntity(target, List.of(existing), Set.of("MainCamera"));

        assertNotNull(result);
        assertEquals(EntityResolutionResult.MatchType.NO_MATCH, result.getMatchType());
        assertTrue(result.shouldCreateNew());
        assertFalse(result.shouldReuse());
        assertEquals("TreasureChest", result.getDisambiguatedName());
    }

    @Test
    @DisplayName("Disambiguation prevents name collisions with _02, _03 suffixes")
    void testDisambiguatedNameAvoidsCollisions() {
        Set<String> existingNames = Set.of("Player", "Player_02", "Enemy", "Ground");

        assertEquals("Player_03", service.generateDisambiguatedName("Player", existingNames));
        assertEquals("Enemy_02", service.generateDisambiguatedName("Enemy", existingNames));
        assertEquals("Coin", service.generateDisambiguatedName("Coin", existingNames));
    }
}
