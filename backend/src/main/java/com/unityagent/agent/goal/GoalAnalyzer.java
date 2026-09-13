package com.unityagent.agent.goal;

import com.unityagent.agent.verification.RequirementVerification;
import com.unityagent.agent.verification.VerificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Analyzes natural language user goals and extracts structured, machine-verifiable requirements.
 */
@Service
public class GoalAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(GoalAnalyzer.class);

    /**
     * Decomposes a user goal description into a structured GameGoal with machine-verifiable requirements.
     */
    public GameGoal analyzeGoal(String sessionId, String goalPrompt) {
        if (goalPrompt == null || goalPrompt.isBlank()) {
            throw new IllegalArgumentException("Goal prompt cannot be null or blank");
        }

        String goalId = "goal_" + UUID.randomUUID().toString().substring(0, 8);
        GameGoal goal = new GameGoal(goalId, sessionId, goalPrompt.trim());

        List<GoalRequirement> requirements = extractRequirements(goalPrompt);
        for (GoalRequirement req : requirements) {
            goal.addRequirement(req);
        }

        // Add standard baseline invariants
        GoalConstraint noInfiniteLoops = GoalConstraint.of("NoInfiniteLoops", "Agent execution must not loop indefinitely", "BUDGET", 250);
        goal.addConstraint(noInfiniteLoops);

        log.info("Analyzed goal '{}': extracted {} requirements", goalPrompt, requirements.size());
        return goal;
    }

    private List<GoalRequirement> extractRequirements(String prompt) {
        List<GoalRequirement> reqs = new ArrayList<>();
        String lower = prompt.toLowerCase();
        int reqIndex = 1;

        boolean isPlatformer = lower.contains("platformer") || lower.contains("platform");
        boolean hasJump = lower.contains("jump") || lower.contains("jumping");
        boolean hasCollectibles = lower.contains("collectible") || lower.contains("coin") || lower.contains("collect");
        boolean hasEnemy = lower.contains("enemy") || lower.contains("enemies");
        boolean hasBoss = lower.contains("boss");
        boolean hasCombat = lower.contains("combat") || lower.contains("attack") || lower.contains("damage") || lower.contains("fight");
        boolean hasUI = lower.contains("ui") || lower.contains("health feedback") || lower.contains("hud") || lower.contains("score");
        boolean hasVictory = lower.contains("victory") || lower.contains("win") || lower.contains("goal");
        boolean isTopDown = lower.contains("top-down") || lower.contains("topdown");
        boolean isThirdPerson = lower.contains("third-person") || lower.contains("third person");

        // 1. Player exists
        String playerReqId = String.format("REQ-%03d", reqIndex++);
        GoalRequirement reqPlayer = new GoalRequirement(
                playerReqId,
                "Player exists",
                GoalRequirement.RequirementType.GAME_OBJECT_EXISTS,
                "Player",
                true,
                RequirementVerification.gameObjectExists("Player")
        );
        reqPlayer.addCriterion(AcceptanceCriterion.exists("crit_player_obj", "Player GameObject exists in scene", "name"));
        reqs.add(reqPlayer);

        // 2. Ground / Arena exists
        String groundName = (isTopDown || isThirdPerson || lower.contains("arena")) ? "Arena" : "Ground";
        String groundReqId = String.format("REQ-%03d", reqIndex++);
        GoalRequirement reqGround = new GoalRequirement(
                groundReqId,
                groundName + " exists",
                GoalRequirement.RequirementType.GAME_OBJECT_EXISTS,
                groundName,
                true,
                RequirementVerification.gameObjectExists(groundName)
        );
        reqGround.addCriterion(AcceptanceCriterion.exists("crit_ground_obj", groundName + " GameObject exists in scene", "name"));
        reqs.add(reqGround);

        // 3. Platforms exist (if platformer or platforms mentioned)
        String platformReqId = null;
        if (isPlatformer || lower.contains("platform")) {
            platformReqId = String.format("REQ-%03d", reqIndex++);
            GoalRequirement reqPlatforms = new GoalRequirement(
                    platformReqId,
                    "Platforms exist",
                    GoalRequirement.RequirementType.GAME_OBJECT_EXISTS,
                    "Platform",
                    true,
                    RequirementVerification.gameObjectExists("Platform")
            );
            reqPlatforms.addPrerequisite(groundReqId);
            reqs.add(reqPlatforms);
        }

        // 4. Player can move
        String moveReqId = String.format("REQ-%03d", reqIndex++);
        GoalRequirement reqMove = new GoalRequirement(
                moveReqId,
                "Player can move",
                GoalRequirement.RequirementType.BEHAVIOR_TEST,
                "Player",
                true,
                RequirementVerification.behaviorTest("Player", "Player position changes along horizontal axes when input is applied", Map.of("key", "W", "duration", 0.5))
        );
        reqMove.addPrerequisite(playerReqId);
        reqMove.addPrerequisite(groundReqId);
        reqs.add(reqMove);

        // 5. Player can jump (if jump requested)
        String jumpReqId = null;
        if (hasJump) {
            jumpReqId = String.format("REQ-%03d", reqIndex++);
            GoalRequirement reqJump = new GoalRequirement(
                    jumpReqId,
                    "Player can jump",
                    GoalRequirement.RequirementType.BEHAVIOR_TEST,
                    "Player",
                    true,
                    RequirementVerification.behaviorTest("Player", "Player vertical position (Y) increases during jump action", Map.of("key", "Space", "duration", 0.5))
            );
            reqJump.addPrerequisite(playerReqId);
            reqJump.addPrerequisite(moveReqId);
            reqs.add(reqJump);
        }

        // 6. Collectibles exist & collection behavior
        if (hasCollectibles) {
            String colReqId = String.format("REQ-%03d", reqIndex++);
            GoalRequirement reqCol = new GoalRequirement(
                    colReqId,
                    "Collectibles exist",
                    GoalRequirement.RequirementType.GAME_OBJECT_EXISTS,
                    "Coin",
                    true,
                    RequirementVerification.gameObjectExists("Coin")
            );
            reqCol.addPrerequisite(groundReqId);
            reqs.add(reqCol);

            String collectActionId = String.format("REQ-%03d", reqIndex++);
            GoalRequirement reqCollectAction = new GoalRequirement(
                    collectActionId,
                    "Player can collect collectibles",
                    GoalRequirement.RequirementType.BEHAVIOR_TEST,
                    "Player",
                    true,
                    RequirementVerification.behaviorTest("Player", "Collectible count decreases or player score increments upon contact", Map.of("target", "Coin"))
            );
            reqCollectAction.addPrerequisite(colReqId);
            reqCollectAction.addPrerequisite(moveReqId);
            reqs.add(reqCollectAction);
        }

        // 7. Enemies exist
        String enemyReqId = null;
        if (hasEnemy) {
            enemyReqId = String.format("REQ-%03d", reqIndex++);
            GoalRequirement reqEnemy = new GoalRequirement(
                    enemyReqId,
                    "Enemy exists",
                    GoalRequirement.RequirementType.GAME_OBJECT_EXISTS,
                    "Enemy",
                    true,
                    RequirementVerification.gameObjectExists("Enemy")
            );
            reqEnemy.addPrerequisite(groundReqId);
            reqs.add(reqEnemy);
        }

        // 8. Combat / Attack / Damage
        if (hasCombat) {
            String combatReqId = String.format("REQ-%03d", reqIndex++);
            GoalRequirement reqCombat = new GoalRequirement(
                    combatReqId,
                    "Combat mechanics functional",
                    GoalRequirement.RequirementType.BEHAVIOR_TEST,
                    "Player",
                    true,
                    RequirementVerification.behaviorTest("Player", "Player attack deals damage to target enemy or reduces health", Map.of("action", "ATTACK"))
            );
            reqCombat.addPrerequisite(playerReqId);
            if (enemyReqId != null) reqCombat.addPrerequisite(enemyReqId);
            reqs.add(reqCombat);
        }

        // 9. Boss exists
        String bossReqId = null;
        if (hasBoss) {
            bossReqId = String.format("REQ-%03d", reqIndex++);
            GoalRequirement reqBoss = new GoalRequirement(
                    bossReqId,
                    "Boss exists",
                    GoalRequirement.RequirementType.GAME_OBJECT_EXISTS,
                    "Boss",
                    true,
                    RequirementVerification.gameObjectExists("Boss")
            );
            reqBoss.addPrerequisite(groundReqId);
            reqs.add(reqBoss);
        }

        // 10. UI feedback
        if (hasUI) {
            String uiReqId = String.format("REQ-%03d", reqIndex++);
            GoalRequirement reqUI = new GoalRequirement(
                    uiReqId,
                    "Health feedback and UI displayed",
                    GoalRequirement.RequirementType.UI_ELEMENT_EXISTS,
                    "HealthUI",
                    true,
                    new RequirementVerification(VerificationType.COMPONENT_EXISTS, "Canvas", "get_object_components", "UI Canvas displays health indicators")
            );
            reqUI.addPrerequisite(playerReqId);
            reqs.add(reqUI);
        }

        // 11. Victory state
        if (hasVictory) {
            String vicReqId = String.format("REQ-%03d", reqIndex++);
            GoalRequirement reqVictory = new GoalRequirement(
                    vicReqId,
                    "Victory state achievable",
                    GoalRequirement.RequirementType.GAMEPLAY_STATE,
                    "GameManager",
                    true,
                    new RequirementVerification(VerificationType.GAMEPLAY_STATE, "GameManager", "validate_game_state", "Game reaches victory state upon goal condition")
            );
            if (bossReqId != null) reqVictory.addPrerequisite(bossReqId);
            reqs.add(reqVictory);
        }

        // Optional non-blocking polish requirement
        GoalRequirement polishReq = new GoalRequirement(
                String.format("REQ-%03d", reqIndex++),
                "Lighting and visual polish applied",
                GoalRequirement.RequirementType.LIGHTING_CONFIGURED,
                "Directional Light",
                false, // Optional
                new RequirementVerification(VerificationType.COMPONENT_EXISTS, "Directional Light", "get_object_components", "Scene has active light source")
        );
        reqs.add(polishReq);

        return reqs;
    }
}
