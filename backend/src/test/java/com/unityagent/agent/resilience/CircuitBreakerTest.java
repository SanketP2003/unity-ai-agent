package com.unityagent.agent.resilience;

import com.unityagent.agent.model.ErrorType;
import com.unityagent.agent.provider.AIProviderException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    @Test
    void testCircuitBreakerTripsAfterFailures() {
        CircuitBreaker cb = new CircuitBreaker("test-breaker", 3, 1000, 2);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());

        // 2 failures: still closed
        cb.recordFailure(true);
        cb.recordFailure(true);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());

        // 3rd failure: trips to OPEN
        cb.recordFailure(true);
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.allowRequest());
    }

    @Test
    void testNonRetryableErrorsDoNotTripBreaker() {
        CircuitBreaker cb = new CircuitBreaker("test-breaker", 2, 1000, 1);
        cb.recordFailure(false);
        cb.recordFailure(false);
        cb.recordFailure(false);

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());
    }

    @Test
    void testHalfOpenAndRecovery() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker("test-breaker", 2, 100, 2);
        cb.recordFailure(true);
        cb.recordFailure(true);
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        // Wait for reset timeout
        Thread.sleep(120);

        // Should transition to HALF_OPEN on allowRequest or getState
        assertTrue(cb.allowRequest());
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());

        // 1st success in HALF_OPEN: still HALF_OPEN
        cb.recordSuccess();
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());

        // 2nd success in HALF_OPEN: recovers to CLOSED
        cb.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void testProviderRetryPolicyClassification() {
        ProviderRetryPolicy policy = new ProviderRetryPolicy();

        // 429 rate limit is retryable
        assertTrue(policy.isRetryable(new AIProviderException("rate limit", ErrorType.PROVIDER_ERROR, 429)));

        // 503 server error is retryable
        assertTrue(policy.isRetryable(new AIProviderException("unavailable", ErrorType.PROVIDER_ERROR, 503)));

        // Timeouts and network disconnects are retryable
        assertTrue(policy.isRetryable(new HttpTimeoutException("timeout")));
        assertTrue(policy.isRetryable(new ConnectException("connection refused")));

        // 401 auth error is NOT retryable
        assertFalse(policy.isRetryable(new AIProviderException("unauthorized", ErrorType.PROVIDER_ERROR, 401)));

        // 400 bad request is NOT retryable
        assertFalse(policy.isRetryable(new AIProviderException("bad schema", ErrorType.PROVIDER_ERROR, 400)));
    }
}
