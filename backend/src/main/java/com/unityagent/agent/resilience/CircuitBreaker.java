package com.unityagent.agent.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe Circuit Breaker pattern implementation for AI Provider resilience.
 * State transitions:
 * CLOSED → (failures >= threshold) → OPEN → (resetTimeout expired) → HALF_OPEN → (successes >= threshold) → CLOSED
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final int halfOpenSuccessThreshold;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTimestamp = new AtomicLong(0);

    public CircuitBreaker(String name) {
        this(name, 5, 30_000, 2);
    }

    public CircuitBreaker(String name, int failureThreshold, long resetTimeoutMs, int halfOpenSuccessThreshold) {
        this.name = name;
        this.failureThreshold = failureThreshold > 0 ? failureThreshold : 5;
        this.resetTimeoutMs = resetTimeoutMs > 0 ? resetTimeoutMs : 30_000;
        this.halfOpenSuccessThreshold = halfOpenSuccessThreshold > 0 ? halfOpenSuccessThreshold : 2;
    }

    /**
     * Checks if a request is permitted.
     * Transitions OPEN to HALF_OPEN if reset timeout has elapsed.
     */
    public boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }

        if (current == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTimestamp.get();
            if (elapsed >= resetTimeoutMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("Circuit breaker '{}': Reset timeout expired ({}ms). State transition: OPEN -> HALF_OPEN",
                            name, elapsed);
                    halfOpenSuccessCount.set(0);
                    return true;
                }
            }
            return false;
        }

        // HALF_OPEN permits test traffic
        return true;
    }

    /**
     * Records a successful execution.
     */
    public void recordSuccess() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            int successes = halfOpenSuccessCount.incrementAndGet();
            if (successes >= halfOpenSuccessThreshold) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    log.info("Circuit breaker '{}': {} successful probe requests. State transition: HALF_OPEN -> CLOSED",
                            name, successes);
                    failureCount.set(0);
                    halfOpenSuccessCount.set(0);
                }
            }
        } else if (current == State.CLOSED) {
            failureCount.set(0);
        }
    }

    /**
     * Records a failed execution.
     *
     * @param isRetryable true if failure was a transient error (e.g. 429, 5xx, timeout)
     */
    public void recordFailure(boolean isRetryable) {
        if (!isRetryable) {
            // Non-retryable errors (e.g. 401, bad model) do not trip the circuit breaker
            return;
        }

        lastFailureTimestamp.set(System.currentTimeMillis());
        State current = state.get();

        if (current == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                log.warn("Circuit breaker '{}': Probe failed during HALF_OPEN. State transition: HALF_OPEN -> OPEN", name);
            }
        } else if (current == State.CLOSED) {
            int count = failureCount.incrementAndGet();
            if (count >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    log.warn("Circuit breaker '{}': Failure threshold reached ({}/{}). State transition: CLOSED -> OPEN",
                            name, count, failureThreshold);
                }
            }
        }
    }

    public State getState() {
        // Automatically check if OPEN has timed out
        if (state.get() == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTimestamp.get();
            if (elapsed >= resetTimeoutMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenSuccessCount.set(0);
                }
            }
        }
        return state.get();
    }

    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        halfOpenSuccessCount.set(0);
    }

    public String getName() {
        return name;
    }
}
