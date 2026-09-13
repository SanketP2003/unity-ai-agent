package com.unityagent.agent.recovery;

import java.util.regex.Pattern;

/**
 * Classifies failure states across multiple domain categories beyond compiler errors,
 * providing the autonomous recovery loop with diagnostic root causes and recommended repair actions.
 */
public class RecoveryClassifier {

    public enum FailureCategory {
        COMPILATION_ERROR,
        MISSING_REFERENCE,
        INVALID_COMPONENT_PROPERTY,
        MISSING_ASSET,
        MISSING_COMPONENT,
        INVALID_PREFAB,
        RUNTIME_EXCEPTION,
        BEHAVIOR_FAILURE,
        VALIDATION_FAILURE,
        UNKNOWN
    }

    public static class DiagnosticClassification {
        private final FailureCategory category;
        private final String rootCause;
        private final String recommendedAction;

        public DiagnosticClassification(FailureCategory category, String rootCause, String recommendedAction) {
            this.category = category;
            this.rootCause = rootCause;
            this.recommendedAction = recommendedAction;
        }

        public FailureCategory getCategory() { return category; }
        public String getRootCause() { return rootCause; }
        public String getRecommendedAction() { return recommendedAction; }
    }

    private static final Pattern CS_ERROR = Pattern.compile("CS\\d{4}");
    private static final Pattern NULL_REF = Pattern.compile("NullReferenceException|Object reference not set", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_COMP = Pattern.compile("MissingComponentException|does not have a .* component", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_ASSET = Pattern.compile("not found at path|Asset not found|MATERIAL_NOT_FOUND|PREFAB_NOT_FOUND", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVALID_PROP = Pattern.compile("Property or field .* not found|INVALID_PROPERTY", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEHAVIOR_FAIL = Pattern.compile("did not change during test|distanceMoved = 0", Pattern.CASE_INSENSITIVE);

    public static DiagnosticClassification classify(String errorMessage, String toolName) {
        if (errorMessage == null) {
            return new DiagnosticClassification(FailureCategory.UNKNOWN, "No error details available", "Inspect recent logs");
        }

        if (CS_ERROR.matcher(errorMessage).find() || "compile_project".equals(toolName)) {
            return new DiagnosticClassification(
                    FailureCategory.COMPILATION_ERROR,
                    "C# compiler diagnostics reported syntax or type errors",
                    "Read script, fix compilation error, and recompile"
            );
        }

        if (NULL_REF.matcher(errorMessage).find()) {
            return new DiagnosticClassification(
                    FailureCategory.RUNTIME_EXCEPTION,
                    "NullReferenceException encountered at runtime",
                    "Verify variable initialization and GameObject/component references"
            );
        }

        if (MISSING_COMP.matcher(errorMessage).find()) {
            return new DiagnosticClassification(
                    FailureCategory.MISSING_COMPONENT,
                    "Required component missing on target GameObject",
                    "Add required component via add_component before accessing properties"
            );
        }

        if (MISSING_ASSET.matcher(errorMessage).find()) {
            return new DiagnosticClassification(
                    FailureCategory.MISSING_ASSET,
                    "Referenced asset file missing or path incorrect",
                    "Create missing asset or verify asset path with list_assets"
            );
        }

        if (INVALID_PROP.matcher(errorMessage).find()) {
            return new DiagnosticClassification(
                    FailureCategory.INVALID_COMPONENT_PROPERTY,
                    "Property name does not exist on component",
                    "Use get_component_properties to inspect valid property names and types"
            );
        }

        if (BEHAVIOR_FAIL.matcher(errorMessage).find() || "run_game_test".equals(toolName)) {
            return new DiagnosticClassification(
                    FailureCategory.BEHAVIOR_FAILURE,
                    "Object failed behavioral test (e.g. did not move on stimulus)",
                    "Inspect Rigidbody/CharacterController settings and movement script logic"
            );
        }

        if (errorMessage.contains("FAILED") && "validate_game_state".equals(toolName)) {
            return new DiagnosticClassification(
                    FailureCategory.VALIDATION_FAILURE,
                    "Goal requirements failed objective validation in scene",
                    "Address the specific failing requirement reported in validate_game_state"
            );
        }

        return new DiagnosticClassification(
                FailureCategory.UNKNOWN,
                errorMessage,
                "Inspect object state and retry with corrected parameters"
        );
    }
}
