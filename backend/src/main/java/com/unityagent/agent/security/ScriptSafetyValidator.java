package com.unityagent.agent.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates script paths and inspects generated C# code for prohibited or dangerous APIs.
 * Mandatory first-line defense before any script is written or modified in the Unity project.
 */
@Component
public class ScriptSafetyValidator {

    public static class ValidationResult {
        private final boolean valid;
        private final String normalizedPath;
        private final String violationMessage;

        private ValidationResult(boolean valid, String normalizedPath, String violationMessage) {
            this.valid = valid;
            this.normalizedPath = normalizedPath;
            this.violationMessage = violationMessage;
        }

        public static ValidationResult success(String normalizedPath) {
            return new ValidationResult(true, normalizedPath, null);
        }

        public static ValidationResult violation(String violationMessage) {
            return new ValidationResult(false, null, violationMessage);
        }

        public boolean isValid() { return valid; }
        public String getNormalizedPath() { return normalizedPath; }
        public String getViolationMessage() { return violationMessage; }
    }

    private static final List<Pattern> PROHIBITED_CODE_PATTERNS = List.of(
            // Process & OS command execution
            Pattern.compile("\\bSystem\\.Diagnostics\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bProcessStartInfo\\b"),
            Pattern.compile("\\bProcess\\.Start\\b"),
            Pattern.compile("\\bcmd\\.exe\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpowershell(\\.exe)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/bin/(sh|bash)"),

            // Native interop & dynamic code execution
            Pattern.compile("\\[\\s*DllImport\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bextern\\s+.*\\("),
            Pattern.compile("\\bAssembly\\.Load(From|File)?\\b"),
            Pattern.compile("\\bAppDomain\\.CurrentDomain\\.Load\\b"),

            // Environment & registry manipulation
            Pattern.compile("\\bSystem\\.Environment\\b"),
            Pattern.compile("\\bEnvironment\\.(Exit|FailFast|SetEnvironmentVariable|GetEnvironmentVariable)\\b"),
            Pattern.compile("\\bMicrosoft\\.Win32\\b"),
            Pattern.compile("\\bRegistry(Key)?\\b"),

            // Raw sockets & unrestricted low-level networking
            Pattern.compile("\\bSystem\\.Net\\.Sockets\\b"),
            Pattern.compile("\\b(Socket|TcpClient|TcpListener|UdpClient)\\b")
    );

    /**
     * Validates that the path is strictly sandboxed within Assets/ and has a .cs extension.
     */
    public ValidationResult validatePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return ValidationResult.violation("Script path cannot be null or empty.");
        }

        // Normalize slashes
        String normalized = rawPath.trim().replace('\\', '/');

        // Check for directory traversal
        if (normalized.contains("..") || normalized.contains("/../") || normalized.startsWith("../")) {
            return ValidationResult.violation("Path traversal sequence '..' is prohibited: " + rawPath);
        }

        // Reject absolute Windows drive letters (e.g. C:/, D:/)
        if (Pattern.compile("^[a-zA-Z]:").matcher(normalized).find()) {
            return ValidationResult.violation("Absolute drive paths are prohibited: " + rawPath);
        }

        // Reject absolute Unix roots or Windows UNC paths
        if (normalized.startsWith("/") || normalized.startsWith("//")) {
            return ValidationResult.violation("Absolute root paths are prohibited: " + rawPath);
        }

        // Reject system directories
        if (normalized.toLowerCase().contains("windows") || normalized.toLowerCase().contains("program files")) {
            return ValidationResult.violation("Access to system directories is prohibited: " + rawPath);
        }

        // Ensure starts with Assets/
        if (!normalized.startsWith("Assets/") && !normalized.equals("Assets")) {
            // If user supplied e.g. "Scripts/Player.cs", prefix with "Assets/"
            if (normalized.startsWith("Scripts/")) {
                normalized = "Assets/" + normalized;
            } else {
                return ValidationResult.violation("Script path must be within 'Assets/': " + rawPath);
            }
        }

        // Must end with .cs extension
        if (!normalized.toLowerCase().endsWith(".cs")) {
            return ValidationResult.violation("Script file must have a '.cs' extension: " + rawPath);
        }

        return ValidationResult.success(normalized);
    }

    /**
     * Inspects C# code content for prohibited dangerous APIs or constructs.
     */
    public ValidationResult validateContent(String content) {
        if (content == null || content.isBlank()) {
            return ValidationResult.violation("Script content cannot be empty.");
        }

        for (Pattern pattern : PROHIBITED_CODE_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return ValidationResult.violation("Generated script contains prohibited API or construct matching pattern: " + pattern.pattern());
            }
        }

        return ValidationResult.success(null);
    }

    /**
     * Combined validation of path and content.
     */
    public ValidationResult validate(String path, String content) {
        ValidationResult pathResult = validatePath(path);
        if (!pathResult.isValid()) {
            return pathResult;
        }

        ValidationResult contentResult = validateContent(content);
        if (!contentResult.isValid()) {
            return contentResult;
        }

        return pathResult;
    }

    /**
     * Computes deterministic SHA-256 hash for optimistic concurrency.
     */
    public static String computeHash(String content) {
        if (content == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Normalize CRLF to LF for consistent hashing across platforms
            String normalizedContent = content.replace("\r\n", "\n");
            byte[] hash = digest.digest(normalizedContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
