package com.unityagent.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configurable safety limits and thresholds for autonomous agent runs.
 */
@Component
public class AgentLimits {

    private final int maxIterations;
    private final int maxToolFailures;
    private final int maxSameToolRetries;
    private final int toolTimeoutSeconds;
    private final int runTimeoutSeconds;
    private final double temperature;
    private final int maxTokens;

    // Phase 6 Recovery Limits
    private final int maxCompileAttempts;
    private final int maxFixAttemptsPerError;
    private final int maxRuntimeValidationAttempts;
    private final int maxPlanSteps;
    private final int maxTotalToolCalls;
    private final int compilationTimeoutSeconds;

    public AgentLimits(int maxIterations, int maxToolFailures, int maxSameToolRetries, int toolTimeoutSeconds, int runTimeoutSeconds) {
        this(maxIterations, maxToolFailures, maxSameToolRetries, toolTimeoutSeconds, runTimeoutSeconds, 0.7, 4096, 5, 3, 3, 30, 100, 120);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AgentLimits(
            @Value("${agent.limits.max-iterations:30}") int maxIterations,
            @Value("${agent.limits.max-tool-failures:5}") int maxToolFailures,
            @Value("${agent.limits.max-same-tool-retries:3}") int maxSameToolRetries,
            @Value("${agent.limits.tool-timeout-seconds:30}") int toolTimeoutSeconds,
            @Value("${agent.limits.run-timeout-seconds:600}") int runTimeoutSeconds,
            @Value("${agent.ai.temperature:0.7}") double temperature,
            @Value("${agent.ai.max-tokens:4096}") int maxTokens,
            @Value("${agent.recovery.max-compile-attempts:5}") int maxCompileAttempts,
            @Value("${agent.recovery.max-fix-attempts-per-error:3}") int maxFixAttemptsPerError,
            @Value("${agent.recovery.max-runtime-validation-attempts:3}") int maxRuntimeValidationAttempts,
            @Value("${agent.recovery.max-plan-steps:30}") int maxPlanSteps,
            @Value("${agent.recovery.max-total-tool-calls:100}") int maxTotalToolCalls,
            @Value("${agent.compilation.timeout-seconds:120}") int compilationTimeoutSeconds) {
        this.maxIterations = maxIterations > 0 ? maxIterations : 30;
        this.maxToolFailures = maxToolFailures > 0 ? maxToolFailures : 5;
        this.maxSameToolRetries = maxSameToolRetries > 0 ? maxSameToolRetries : 3;
        this.toolTimeoutSeconds = toolTimeoutSeconds > 0 ? toolTimeoutSeconds : 30;
        this.runTimeoutSeconds = runTimeoutSeconds > 0 ? runTimeoutSeconds : 600;
        this.temperature = temperature >= 0 ? temperature : 0.7;
        this.maxTokens = maxTokens > 0 ? maxTokens : 4096;
        this.maxCompileAttempts = maxCompileAttempts > 0 ? maxCompileAttempts : 5;
        this.maxFixAttemptsPerError = maxFixAttemptsPerError > 0 ? maxFixAttemptsPerError : 3;
        this.maxRuntimeValidationAttempts = maxRuntimeValidationAttempts > 0 ? maxRuntimeValidationAttempts : 3;
        this.maxPlanSteps = maxPlanSteps > 0 ? maxPlanSteps : 30;
        this.maxTotalToolCalls = maxTotalToolCalls > 0 ? maxTotalToolCalls : 100;
        this.compilationTimeoutSeconds = compilationTimeoutSeconds > 0 ? compilationTimeoutSeconds : 120;
    }

    public static AgentLimits defaultLimits() {
        return new AgentLimits(30, 5, 3, 30, 600, 0.7, 4096, 5, 3, 3, 30, 100, 120);
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public int getMaxToolFailures() {
        return maxToolFailures;
    }

    public int getMaxSameToolRetries() {
        return maxSameToolRetries;
    }

    public int getToolTimeoutSeconds() {
        return toolTimeoutSeconds;
    }

    public int getRunTimeoutSeconds() {
        return runTimeoutSeconds;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public int getMaxCompileAttempts() {
        return maxCompileAttempts;
    }

    public int getMaxFixAttemptsPerError() {
        return maxFixAttemptsPerError;
    }

    public int getMaxRuntimeValidationAttempts() {
        return maxRuntimeValidationAttempts;
    }

    public int getMaxPlanSteps() {
        return maxPlanSteps;
    }

    public int getMaxTotalToolCalls() {
        return maxTotalToolCalls;
    }

    public int getCompilationTimeoutSeconds() {
        return compilationTimeoutSeconds;
    }
}
