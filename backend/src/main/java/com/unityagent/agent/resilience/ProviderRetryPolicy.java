package com.unityagent.agent.resilience;

import com.unityagent.agent.provider.AIProviderException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

/**
 * Classifies AI Provider errors into retryable vs non-retryable categories
 * and computes bounded exponential backoff with jitter.
 */
public class ProviderRetryPolicy {

    private final int maxRetries;
    private final long initialBackoffMs;
    private final long maxBackoffMs;

    public ProviderRetryPolicy() {
        this(3, 1000, 30_000);
    }

    public ProviderRetryPolicy(int maxRetries, long initialBackoffMs, long maxBackoffMs) {
        this.maxRetries = maxRetries > 0 ? maxRetries : 3;
        this.initialBackoffMs = initialBackoffMs > 0 ? initialBackoffMs : 1000;
        this.maxBackoffMs = maxBackoffMs > 0 ? maxBackoffMs : 30_000;
    }

    /**
     * Determines whether an exception represents a retryable transient condition.
     */
    public boolean isRetryable(Throwable t) {
        if (t == null) return false;

        if (t instanceof AIProviderException pe) {
            Integer status = pe.getHttpStatus();
            if (status != null) {
                // Non-retryable HTTP status codes
                if (status == 401 || status == 403) return false;
                if (status == 400) {
                    // Bad requests, malformed schemas, or unsupported models are permanent failures
                    return false;
                }
                // Retryable HTTP status codes: 429 (rate limit) or 5xx (server error)
                if (status == 429 || (status >= 500 && status < 600)) {
                    return true;
                }
            }
            if (pe.getMessage() != null && pe.getMessage().contains("not configured")) {
                return false;
            }
        }

        // Network and timeout errors are retryable
        if (t instanceof HttpTimeoutException || t instanceof TimeoutException ||
            t instanceof ConnectException || t instanceof IOException) {
            return true;
        }

        if (t.getCause() != null && t.getCause() != t) {
            return isRetryable(t.getCause());
        }

        return false;
    }

    /**
     * Computes backoff duration with exponential progression and random jitter.
     *
     * @param attempt 1-indexed attempt number
     * @return sleep duration in milliseconds
     */
    public long calculateBackoffMs(int attempt) {
        if (attempt <= 0) return initialBackoffMs;

        // Exponential backoff: initialBackoffMs * 2^(attempt - 1)
        long expBackoff = initialBackoffMs * (1L << Math.min(attempt - 1, 10));
        long bounded = Math.min(expBackoff, maxBackoffMs);

        // Add 0-25% random jitter to avoid thundering herd
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, bounded / 4));
        return Math.min(bounded + jitter, maxBackoffMs);
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}
