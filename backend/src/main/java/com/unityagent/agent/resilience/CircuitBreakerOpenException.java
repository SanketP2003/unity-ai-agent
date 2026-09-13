package com.unityagent.agent.resilience;

import com.unityagent.agent.model.ErrorType;
import com.unityagent.agent.provider.AIProviderException;

/**
 * Thrown when an AIProvider call is attempted while its CircuitBreaker is in OPEN state.
 */
public class CircuitBreakerOpenException extends AIProviderException {

    public CircuitBreakerOpenException(String message) {
        super(message, ErrorType.PROVIDER_ERROR, 503);
    }
}
