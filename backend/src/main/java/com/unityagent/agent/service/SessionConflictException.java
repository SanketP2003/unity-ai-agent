package com.unityagent.agent.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a concurrent agent run is attempted on an active session.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SessionConflictException extends RuntimeException {
    public SessionConflictException(String message) {
        super(message);
    }
}
