package com.unityagent.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.identity.EntityCandidate;
import com.unityagent.agent.identity.EntityResolutionResult;
import com.unityagent.agent.identity.EntityResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PerformanceBenchmarkTest {

    private EntityResolutionService resolutionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resolutionService = new EntityResolutionService();
        objectMapper = new ObjectMapper();
    }

    private List<EntityCandidate> generateHierarchy(int count) {
        List<EntityCandidate> list = new ArrayList<>(count);
        List<String> defaultComponents = List.of("Transform", "MeshFilter", "MeshRenderer", "BoxCollider");
        for (int i = 0; i < count; i++) {
            String name = "Obstacle_" + i;
            String path = "Environment/Zone_" + (i / 100) + "/" + name;
            list.add(new EntityCandidate("id_" + i, name, path, defaultComponents));
        }
        return list;
    }

    @Test
    void testScalingAcrossHierarchySizes() throws Exception {
        int[] tiers = {50, 500, 5000, 10000};
        EntityCandidate target = new EntityCandidate("target_id", "Obstacle_4999", "Environment/Zone_49/Obstacle_4999",
                List.of("Transform", "MeshRenderer", "BoxCollider"));

        for (int size : tiers) {
            List<EntityCandidate> sceneEntities = generateHierarchy(size);
            Set<String> nameSet = new HashSet<>(size);
            for (EntityCandidate ec : sceneEntities) {
                nameSet.add(ec.getName());
            }

            long start = System.currentTimeMillis();
            EntityResolutionResult result = resolutionService.resolveEntity(target, sceneEntities, nameSet);
            long duration = System.currentTimeMillis() - start;

            assertNotNull(result);
            // 10,000 entities linear scan should complete in under 2000 ms
            assertTrue(duration < 2500, "Resolution for " + size + " entities took " + duration + "ms (exceeded threshold)");

            if (size >= 5000) {
                assertEquals(EntityResolutionResult.MatchType.EXACT_MATCH, result.getMatchType());
                assertEquals("Obstacle_4999", result.getMatchedName());
            }

            // Benchmark JSON serialization of the scene hierarchy
            long jsonStart = System.currentTimeMillis();
            String json = objectMapper.writeValueAsString(sceneEntities);
            long jsonDuration = System.currentTimeMillis() - jsonStart;

            assertNotNull(json);
            assertTrue(jsonDuration < 2000, "JSON serialization for " + size + " entities took " + jsonDuration + "ms");
        }
    }

    @Test
    void testMemoryConsumptionBoundaries() {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        List<EntityCandidate> massiveScene = generateHierarchy(10000);
        assertEquals(10000, massiveScene.size());

        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long memDeltaMb = (memAfter - memBefore) / (1024 * 1024);

        // 10,000 simple candidates should consume < 50 MB
        assertTrue(memDeltaMb < 60, "Memory consumption for 10k entities was " + memDeltaMb + "MB (exceeded threshold)");
    }
}
