package com.unityagent.agent.observability;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production metrics collection service tracking operational performance,
 * tool reliability, provider health, and autonomy progression.
 */
@Service
public class MetricsService {

    private final AtomicLong runCount = new AtomicLong(0);
    private final AtomicLong successfulRuns = new AtomicLong(0);
    private final AtomicLong failedRuns = new AtomicLong(0);
    private final AtomicLong totalRunDurationMs = new AtomicLong(0);

    private final AtomicLong totalToolCalls = new AtomicLong(0);
    private final AtomicLong successfulToolCalls = new AtomicLong(0);
    private final AtomicLong failedToolCalls = new AtomicLong(0);

    private final AtomicLong providerRequests = new AtomicLong(0);
    private final AtomicLong providerErrors = new AtomicLong(0);
    private final AtomicLong totalProviderLatencyMs = new AtomicLong(0);

    private final AtomicLong unityDisconnectCount = new AtomicLong(0);
    private final AtomicLong recoveryCount = new AtomicLong(0);
    private final AtomicLong replanCount = new AtomicLong(0);
    private final AtomicLong checkpointCount = new AtomicLong(0);
    private final AtomicLong validationFailureCount = new AtomicLong(0);

    public void recordRunStarted() {
        runCount.incrementAndGet();
    }

    public void recordRunCompleted(boolean success, long durationMs) {
        if (success) {
            successfulRuns.incrementAndGet();
        } else {
            failedRuns.incrementAndGet();
        }
        totalRunDurationMs.addAndGet(Math.max(0, durationMs));
    }

    public void recordToolExecution(boolean success) {
        totalToolCalls.incrementAndGet();
        if (success) {
            successfulToolCalls.incrementAndGet();
        } else {
            failedToolCalls.incrementAndGet();
        }
    }

    public void recordProviderCall(boolean success, long latencyMs) {
        providerRequests.incrementAndGet();
        if (!success) {
            providerErrors.incrementAndGet();
        }
        totalProviderLatencyMs.addAndGet(Math.max(0, latencyMs));
    }

    public void recordUnityDisconnect() {
        unityDisconnectCount.incrementAndGet();
    }

    public void recordRecoveryCycle() {
        recoveryCount.incrementAndGet();
    }

    public void recordReplan() {
        replanCount.incrementAndGet();
    }

    public void recordCheckpoint() {
        checkpointCount.incrementAndGet();
    }

    public void recordValidationFailure() {
        validationFailureCount.incrementAndGet();
    }

    /**
     * Generates a snapshot of all system operational metrics.
     */
    public Map<String, Object> getMetricsSnapshot() {
        Map<String, Object> m = new LinkedHashMap<>();

        long runs = runCount.get();
        long successR = successfulRuns.get();
        long failedR = failedRuns.get();
        long completedR = successR + failedR;

        m.put("run_count", runs);
        m.put("successful_runs", successR);
        m.put("failed_runs", failedR);
        m.put("completion_rate", completedR > 0 ? (double) successR / completedR : 0.0);
        m.put("average_run_duration_ms", completedR > 0 ? totalRunDurationMs.get() / completedR : 0L);

        long tools = totalToolCalls.get();
        long successT = successfulToolCalls.get();
        long failedT = failedToolCalls.get();
        m.put("tool_calls_total", tools);
        m.put("tool_success_rate", tools > 0 ? (double) successT / tools : 0.0);
        m.put("tool_failure_rate", tools > 0 ? (double) failedT / tools : 0.0);

        long pReqs = providerRequests.get();
        long pErrs = providerErrors.get();
        m.put("provider_requests_total", pReqs);
        m.put("provider_error_rate", pReqs > 0 ? (double) pErrs / pReqs : 0.0);
        m.put("average_provider_latency_ms", pReqs > 0 ? totalProviderLatencyMs.get() / pReqs : 0L);

        m.put("unity_disconnect_count", unityDisconnectCount.get());
        m.put("recovery_count", recoveryCount.get());
        m.put("replan_count", replanCount.get());
        m.put("checkpoint_count", checkpointCount.get());
        m.put("validation_failure_count", validationFailureCount.get());

        return m;
    }
}
