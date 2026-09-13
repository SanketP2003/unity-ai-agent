package com.unityagent.agent.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates tool arguments against path traversal, command injection,
 * UNC escapes, and oversized payloads across all tool categories.
 */
@Component
public class ToolArgumentValidator {

    public static final int MAX_STRING_ARG_LENGTH = 65536;

    private static final Pattern DRIVE_LETTER = Pattern.compile("^[a-zA-Z]:");
    private static final Pattern ENV_VAR = Pattern.compile("(%[a-zA-Z0-9_]+%|\\$[a-zA-Z0-9_]+|\\$\\{[a-zA-Z0-9_]+\\})");
    private static final Pattern SUSPICIOUS_SHELL = Pattern.compile("(\\|\\||&&|;|`|\\$\\()");

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Inspects tool parameters and enforces security constraints.
     */
    public ValidationResult validate(String toolName, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return ValidationResult.ok();
        }

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            if (val instanceof String s) {
                // Check maximum length
                if (s.length() > MAX_STRING_ARG_LENGTH) {
                    return ValidationResult.reject(String.format("Argument '%s' exceeds maximum allowed length (%d > %d)",
                            key, s.length(), MAX_STRING_ARG_LENGTH));
                }

                // Check for null bytes
                if (s.contains("\0")) {
                    return ValidationResult.reject(String.format("Argument '%s' contains prohibited null byte", key));
                }

                // Check path arguments
                if (isPathKey(key)) {
                    ValidationResult pathCheck = validatePath(key, s);
                    if (!pathCheck.isValid()) {
                        return pathCheck;
                    }
                }

                // Check for shell command injection in non-script text fields
                if (!key.equalsIgnoreCase("scriptContent") && !key.equalsIgnoreCase("code")) {
                    if (SUSPICIOUS_SHELL.matcher(s).find()) {
                        return ValidationResult.reject(String.format("Argument '%s' contains prohibited command chaining characters", key));
                    }
                }
            }
        }

        return ValidationResult.ok();
    }

    private boolean isPathKey(String key) {
        String lower = key.toLowerCase();
        return lower.contains("path") || lower.contains("file") || lower.contains("scene") || lower.contains("folder");
    }

    private ValidationResult validatePath(String key, String path) {
        String normalized = path.replace('\\', '/');

        // Path traversal check
        if (normalized.contains("..") || normalized.startsWith("../") || normalized.contains("/../")) {
            return ValidationResult.reject(String.format("Argument '%s' contains prohibited path traversal sequence '..'", key));
        }

        // Drive letter check (e.g. C:/)
        if (DRIVE_LETTER.matcher(normalized).find()) {
            return ValidationResult.reject(String.format("Argument '%s' contains prohibited absolute drive letter", key));
        }

        // UNC path check (// or \\)
        if (normalized.startsWith("//") || path.startsWith("\\\\")) {
            return ValidationResult.reject(String.format("Argument '%s' contains prohibited UNC network root", key));
        }

        // Environment variable escape check
        if (ENV_VAR.matcher(path).find()) {
            return ValidationResult.reject(String.format("Argument '%s' contains prohibited environment variable escape", key));
        }

        return ValidationResult.ok();
    }
}
