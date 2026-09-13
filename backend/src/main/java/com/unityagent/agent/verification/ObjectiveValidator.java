package com.unityagent.agent.verification;

import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalRequirement;
import com.unityagent.agent.goal.RequirementManager;
import com.unityagent.agent.goal.RequirementStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Validates requirements and overall goal completion against objective evidence,
 * compilation state, and runtime metrics.
 */
@Service
public class ObjectiveValidator {

    private static final Logger log = LoggerFactory.getLogger(ObjectiveValidator.class);

    public ValidationReport validate(GameGoal goal,
                                     RequirementManager requirementManager,
                                     boolean compilationSuccess,
                                     boolean runtimeErrorsClean,
                                     boolean behaviorTestsPassed) {
        if (goal == null) {
            throw new IllegalArgumentException("GameGoal cannot be null");
        }

        ValidationReport report = new ValidationReport(goal.getGoalId());
        List<GoalRequirement> allReqs = goal.getRequirements();
        report.setTotalRequirements(allReqs.size());

        int requiredCount = 0;
        int satisfiedRequired = 0;
        int satisfiedOptional = 0;
        int failedCount = 0;

        for (GoalRequirement req : allReqs) {
            if (req.isRequired()) {
                requiredCount++;
                if (req.getStatus() == RequirementStatus.SATISFIED) {
                    satisfiedRequired++;
                } else if (req.getStatus() == RequirementStatus.FAILED) {
                    failedCount++;
                }
            } else {
                if (req.getStatus() == RequirementStatus.SATISFIED) {
                    satisfiedOptional++;
                }
            }

            for (VerificationEvidence ev : req.getEvidenceList()) {
                report.addEvidence(ev);
            }
        }

        report.setRequiredCount(requiredCount);
        report.setSatisfiedRequiredCount(satisfiedRequired);
        report.setSatisfiedOptionalCount(satisfiedOptional);
        report.setFailedCount(failedCount);
        report.setCompilationSuccess(compilationSuccess);
        report.setRuntimeErrorsClean(runtimeErrorsClean);
        report.setBehaviorTestsPassed(behaviorTestsPassed);

        boolean passedGate = CompletionGate.evaluate(report);
        report.setGoalCompleted(passedGate);

        StringBuilder sb = new StringBuilder();
        sb.append("Requirements: ").append(satisfiedRequired).append("/").append(requiredCount).append(" required satisfied.");
        if (!compilationSuccess) sb.append(" [Compilation failed]");
        if (!runtimeErrorsClean) sb.append(" [Runtime console errors present]");
        if (!behaviorTestsPassed) sb.append(" [Behavioral tests not passed]");
        if (passedGate) sb.append(" Goal fully verified and complete.");
        report.setSummary(sb.toString());

        log.info("Validation for goal {}: {}", goal.getGoalId(), report.getSummary());
        return report;
    }

    /**
     * Validates goal against accumulated verification evidence.
     */
    public ValidationReport validateAll(GameGoal goal, List<VerificationEvidence> evidenceList) {
        if (goal == null) {
            throw new IllegalArgumentException("GameGoal cannot be null");
        }

        RequirementManager reqMgr = new RequirementManager();
        boolean compilationSuccess = true;
        boolean runtimeErrorsClean = true;
        boolean behaviorTestsPassed = true;

        if (evidenceList != null) {
            for (VerificationEvidence ev : evidenceList) {
                if (ev.getVerificationType() == VerificationType.COMPILE_SUCCESS && !ev.isSuccess()) {
                    compilationSuccess = false;
                }
                if (ev.getVerificationType() == VerificationType.NO_RUNTIME_ERRORS && !ev.isSuccess()) {
                    runtimeErrorsClean = false;
                }
                if (ev.getVerificationType() == VerificationType.BEHAVIOR_TEST && !ev.isSuccess()) {
                    behaviorTestsPassed = false;
                }
                if (ev.getRequirementId() != null && ev.isSuccess()) {
                    reqMgr.markSatisfied(ev.getRequirementId(), ev);
                }
            }
        }

        return validate(goal, reqMgr, compilationSuccess, runtimeErrorsClean, behaviorTestsPassed);
    }
}
