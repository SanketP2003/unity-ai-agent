package com.unityagent.agent.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Authoritative completion gate.
 * Strictly enforces that GOAL_COMPLETED can ONLY be produced by objective verification,
 * and CANNOT be declared or bypassed by the LLM.
 */
@Component
public class CompletionGate {

    private static final Logger log = LoggerFactory.getLogger(CompletionGate.class);

    public boolean canComplete(ValidationReport report) {
        return evaluate(report);
    }

    /**
     * Evaluates whether a goal has met all completion criteria.
     *
     * @param report the objective validation report
     * @return true if and only if all mandatory criteria are objectively satisfied
     */
    public static boolean evaluate(ValidationReport report) {
        if (report == null) {
            log.warn("CompletionGate evaluated with null report -> REJECTED");
            return false;
        }

        // 1. All REQUIRED requirements must be SATISFIED
        boolean allRequiredSatisfied = report.getRequiredCount() > 0 &&
                report.getSatisfiedRequiredCount() == report.getRequiredCount();
        if (!allRequiredSatisfied) {
            log.info("CompletionGate REJECTED: Only {} of {} required requirements satisfied",
                    report.getSatisfiedRequiredCount(), report.getRequiredCount());
            return false;
        }

        // 2. Compilation must be successful
        if (!report.isCompilationSuccess()) {
            log.info("CompletionGate REJECTED: Project compilation was not successful");
            return false;
        }

        // 3. Runtime errors must be clean (no critical exceptions / errors)
        if (!report.isRuntimeErrorsClean()) {
            log.info("CompletionGate REJECTED: Critical runtime console errors detected");
            return false;
        }

        // 4. Behavioral tests must pass
        if (!report.isBehaviorTestsPassed()) {
            log.info("CompletionGate REJECTED: Behavioral runtime tests did not pass");
            return false;
        }

        log.info("CompletionGate PASSED: All {} required requirements satisfied, compilation clean, behavior tests passed, 0 runtime errors",
                report.getRequiredCount());
        return true;
    }
}
