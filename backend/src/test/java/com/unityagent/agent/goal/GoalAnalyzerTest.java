package com.unityagent.agent.goal;

import com.unityagent.agent.verification.RequirementVerification;
import com.unityagent.agent.verification.VerificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("9.1 & 9.2 GoalAnalyzer & Requirement System Tests")
class GoalAnalyzerTest {

    private final GoalAnalyzer analyzer = new GoalAnalyzer();

    @Test
    @DisplayName("Analyze platformer goal extracts machine-verifiable requirements")
    void testPlatformerGoalExtraction() {
        String prompt = "Create a platformer with jumping and collectibles.";
        GameGoal goal = analyzer.analyzeGoal("sess_test_1", prompt);

        assertNotNull(goal);
        assertEquals("sess_test_1", goal.getSessionId());
        assertEquals(GameGoal.GoalStatus.ACTIVE, goal.getStatus());
        assertFalse(goal.getRequirements().isEmpty());

        List<GoalRequirement> reqs = goal.getRequirements();
        // Check for core requirements
        assertTrue(reqs.stream().anyMatch(r -> r.getDescription().contains("Player exists")));
        assertTrue(reqs.stream().anyMatch(r -> r.getDescription().contains("Ground exists") || r.getDescription().contains("Arena exists")));
        assertTrue(reqs.stream().anyMatch(r -> r.getDescription().contains("Platforms exist")));
        assertTrue(reqs.stream().anyMatch(r -> r.getDescription().contains("Player can move")));
        assertTrue(reqs.stream().anyMatch(r -> r.getDescription().contains("Player can jump")));
        assertTrue(reqs.stream().anyMatch(r -> r.getDescription().contains("Collectibles exist")));
        assertTrue(reqs.stream().anyMatch(r -> r.getDescription().contains("collect collectibles")));

        // Verify verification types attached
        GoalRequirement jumpReq = reqs.stream()
                .filter(r -> r.getDescription().contains("jump"))
                .findFirst().orElseThrow();
        assertNotNull(jumpReq.getVerification());
        assertEquals(VerificationType.BEHAVIOR_TEST, jumpReq.getVerification().getType());
    }

    @Test
    @DisplayName("Distinguishes required vs optional requirements")
    void testRequiredVsOptionalRequirements() {
        GameGoal goal = analyzer.analyzeGoal("sess_test_2", "Create an arena with enemies and combat");
        List<GoalRequirement> required = goal.getRequiredRequirements();
        List<GoalRequirement> optional = goal.getOptionalRequirements();

        assertFalse(required.isEmpty());
        assertTrue(required.stream().allMatch(GoalRequirement::isRequired));
        assertTrue(optional.stream().noneMatch(GoalRequirement::isRequired));
    }

    @Test
    @DisplayName("Malformed or empty goal throws IllegalArgumentException")
    void testMalformedGoalHandling() {
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyzeGoal("sess_1", null));
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyzeGoal("sess_1", "   "));
    }
}
