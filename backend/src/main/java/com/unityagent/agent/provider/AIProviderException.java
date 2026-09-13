package com.unityagent.agent.provider;

import com.unityagent.agent.model.ErrorType;

/**
 * Exception thrown when an AI provider fails to generate a response,
 * is unconfigured, or encounters network/protocol errors.
 */
public class AIProviderException extends RuntimeException {

    private final ErrorType errorType;
    private final Integer statusCode;

    public AIProviderException(String message) {
        super(message);
        this.errorType = ErrorType.PROVIDER_ERROR;
        this.statusCode = null;
    }

    public AIProviderException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = ErrorType.PROVIDER_ERROR;
        this.statusCode = null;
    }

    public AIProviderException(String message, ErrorType errorType, Integer statusCode) {
        super(message);
        this.errorType = errorType != null ? errorType : ErrorType.PROVIDER_ERROR;
        this.statusCode = statusCode;
    }

    public AIProviderException(String message, Throwable cause, ErrorType errorType, Integer statusCode) {
        super(message, cause);
        this.errorType = errorType != null ? errorType : ErrorType.PROVIDER_ERROR;
        this.statusCode = statusCode;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Integer getHttpStatus() {
        return statusCode;
    }
}
