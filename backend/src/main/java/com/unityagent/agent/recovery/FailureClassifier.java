package com.unityagent.agent.recovery;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent diagnostic classifier that inspects error logs, stack traces,
 * compiler output, and test results to identify granular failure types.
 */
@Service
public class FailureClassifier {

    private static final Pattern CS_ERROR_CODE = Pattern.compile("\\b(CS\\d{4})\\b");
    private static final Pattern CS_SYNTAX = Pattern.compile("\\b(CS1002|CS1513|CS1514|CS1525|CS1003|CS1026)\\b");
    private static final Pattern CS_TYPE = Pattern.compile("\\b(CS0246|CS0103|CS0234)\\b");
    private static final Pattern CS_MEMBER = Pattern.compile("\\b(CS1061|CS0117|CS0120|CS0122)\\b");
    private static final Pattern CS_CONVERSION = Pattern.compile("\\b(CS0029|CS1503|CS0266)\\b");

    private static final Pattern NULL_REF = Pattern.compile("NullReferenceException|Object reference not set", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_COMP = Pattern.compile("MissingComponentException|does not have a .* component|Component .* not found", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_DEP = Pattern.compile("not found at path|Asset not found|PREFAB_NOT_FOUND|MATERIAL_NOT_FOUND|File not found", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCENE_DRIFT = Pattern.compile("GameObject '.*' not found|Object has been destroyed|The object of type GameObject has been destroyed", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSERTION_FAIL = Pattern.compile("AssertionException|Assert\\..* failed|Assertion failed", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEHAVIOR_TIMEOUT = Pattern.compile("did not change during test|distanceMoved = 0|Test timed out|did not respond", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEHAVIOR_PHYSICS = Pattern.compile("fell through|falling through|collider missing|rigidbody velocity 0", Pattern.CASE_INSENSITIVE);

    // Extraction patterns
    private static final Pattern SCRIPT_FILE_PATTERN = Pattern.compile("([A-Za-z0-9_]+\\.cs)");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("type or namespace name '([A-Za-z0-9_]+)'|does not contain a definition for '([A-Za-z0-9_]+)'");

    /**
     * Classifies a failure string into a structured FailureContext.
     */
    public FailureContext classify(String nodeId, String toolName, String rawError) {
        if (rawError == null || rawError.isBlank()) {
            return new FailureContext(nodeId, toolName, "No error details provided", FailureType.UNKNOWN, null);
        }

        String code = extractErrorCode(rawError);
        FailureType type = determineType(rawError, code, toolName);

        FailureContext context = new FailureContext(nodeId, toolName, rawError, type, code);

        // Extract script name if available
        Matcher scriptMatcher = SCRIPT_FILE_PATTERN.matcher(rawError);
        if (scriptMatcher.find()) {
            context.setTargetFileOrAsset(scriptMatcher.group(1));
        }

        // Extract target symbol if available
        Matcher symbolMatcher = SYMBOL_PATTERN.matcher(rawError);
        if (symbolMatcher.find()) {
            String symbol = symbolMatcher.group(1) != null ? symbolMatcher.group(1) : symbolMatcher.group(2);
            context.getDiagnosticDetails().put("missingSymbol", symbol);
        }

        return context;
    }

    private String extractErrorCode(String rawError) {
        Matcher m = CS_ERROR_CODE.matcher(rawError);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private FailureType determineType(String rawError, String code, String toolName) {
        if (code != null) {
            if (CS_SYNTAX.matcher(code).find()) return FailureType.COMPILATION_SYNTAX;
            if (CS_TYPE.matcher(code).find()) return FailureType.COMPILATION_TYPE;
            if (CS_MEMBER.matcher(code).find()) return FailureType.COMPILATION_MEMBER;
            if (CS_CONVERSION.matcher(code).find()) return FailureType.COMPILATION_CONVERSION;
            return FailureType.COMPILATION_TYPE; // Default CS error fallback
        }

        if (NULL_REF.matcher(rawError).find()) {
            return FailureType.RUNTIME_NULL_REF;
        }
        if (MISSING_COMP.matcher(rawError).find()) {
            return FailureType.MISSING_COMPONENT;
        }
        if (SCENE_DRIFT.matcher(rawError).find()) {
            return FailureType.SCENE_DRIFT;
        }
        if (MISSING_DEP.matcher(rawError).find()) {
            return FailureType.MISSING_DEPENDENCY;
        }
        if (ASSERTION_FAIL.matcher(rawError).find()) {
            return FailureType.RUNTIME_ASSERTION;
        }
        if (BEHAVIOR_TIMEOUT.matcher(rawError).find() || "run_game_test".equals(toolName)) {
            return FailureType.BEHAVIOR_TIMEOUT;
        }
        if (BEHAVIOR_PHYSICS.matcher(rawError).find()) {
            return FailureType.BEHAVIOR_PHYSICS;
        }
        if ("validate_game_state".equals(toolName) && rawError.contains("FAILED")) {
            return FailureType.VALIDATION_FAILED;
        }

        return FailureType.UNKNOWN;
    }
}
