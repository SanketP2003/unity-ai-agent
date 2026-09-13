package com.unityagent.agent.verification;

import com.unityagent.agent.goal.GoalRequirement;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Automatically synthesizes machine-executable behavioral test scenarios
 * from high-level goal requirements.
 */
@Service
public class BehaviorTestGenerator {

    /**
     * Generates a suite of behavioral test scenarios for a given requirement.
     */
    public List<BehaviorTestScenario> generateScenarios(GoalRequirement req) {
        List<BehaviorTestScenario> scenarios = new ArrayList<>();
        if (req == null) return scenarios;

        String text = (req.getDescription() != null ? req.getDescription() : "").toLowerCase();
        String reqId = req.getRequirementId();

        if (text.contains("mov") || text.contains("walk") || text.contains("controller")) {
            BehaviorTestScenario moveScenario = new BehaviorTestScenario(
                    "scenario_move_" + reqId,
                    "Player Movement Verification",
                    "Verifies that player translates horizontally upon receiving movement input"
            );
            moveScenario.addTestAction(BehaviorAction.simulateInput("Player", "D", 1.0));
            moveScenario.addAssertion(BehaviorAction.assertPositionDelta("Player", "X", 0.5));
            moveScenario.addAssertion(BehaviorAction.assertNoErrors());
            scenarios.add(moveScenario);
        }

        if (text.contains("jump")) {
            BehaviorTestScenario jumpScenario = new BehaviorTestScenario(
                    "scenario_jump_" + reqId,
                    "Player Jump Verification",
                    "Verifies that player elevates along the Y-axis upon spacebar press"
            );
            jumpScenario.addTestAction(BehaviorAction.simulateInput("Player", "Space", 0.2));
            jumpScenario.addAssertion(BehaviorAction.assertPositionDelta("Player", "Y", 0.5));
            jumpScenario.addAssertion(BehaviorAction.assertNoErrors());
            scenarios.add(jumpScenario);
        }

        if (text.contains("combat") || text.contains("attack") || text.contains("enemy")) {
            BehaviorTestScenario combatScenario = new BehaviorTestScenario(
                    "scenario_combat_" + reqId,
                    "Combat Interaction Verification",
                    "Verifies attack triggers impact or damage state on enemy"
            );
            combatScenario.addTestAction(BehaviorAction.simulateInput("Player", "J", 0.2));
            combatScenario.addAssertion(new BehaviorAction(
                    BehaviorActionType.OBSERVE_COMPONENT_FIELD,
                    "Enemy",
                    Map.of("component", "EnemyHealth", "field", "currentHealth", "comparison", "LESS_THAN"),
                    "Enemy health must decrease after player attack"
            ));
            combatScenario.addAssertion(BehaviorAction.assertNoErrors());
            scenarios.add(combatScenario);
        }

        // If no specific keyword triggered, produce a general stability/no-crash scenario
        if (scenarios.isEmpty()) {
            BehaviorTestScenario generalScenario = new BehaviorTestScenario(
                    "scenario_stable_" + reqId,
                    "General Stability Verification",
                    "Verifies runtime executes without console exceptions"
            );
            generalScenario.addTestAction(new BehaviorAction(
                    BehaviorActionType.WAIT_SECONDS,
                    "Scene",
                    Map.of("duration", 2.0),
                    "Wait 2 seconds in play mode"
            ));
            generalScenario.addAssertion(BehaviorAction.assertNoErrors());
            scenarios.add(generalScenario);
        }

        return scenarios;
    }
}
