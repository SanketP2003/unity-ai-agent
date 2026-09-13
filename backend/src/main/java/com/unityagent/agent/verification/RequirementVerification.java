package com.unityagent.agent.verification;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Declares the verification strategy and parameters required to objectively satisfy a GoalRequirement.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequirementVerification {

    private VerificationType type;
    private String target;
    private Map<String, Object> parameters;
    private String candidateTool;
    private String expectedOutcome;

    public RequirementVerification() {
        this.parameters = new LinkedHashMap<>();
    }

    public RequirementVerification(VerificationType type, String target, String candidateTool, String expectedOutcome) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.target = target;
        this.candidateTool = candidateTool;
        this.expectedOutcome = expectedOutcome;
        this.parameters = new LinkedHashMap<>();
    }

    public RequirementVerification(VerificationType type, String target, String candidateTool, String expectedOutcome, Map<String, Object> parameters) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.target = target;
        this.candidateTool = candidateTool;
        this.expectedOutcome = expectedOutcome;
        this.parameters = parameters != null ? new LinkedHashMap<>(parameters) : new LinkedHashMap<>();
    }

    public static RequirementVerification gameObjectExists(String objectName) {
        return new RequirementVerification(VerificationType.GAME_OBJECT_EXISTS, objectName, "get_scene_hierarchy", "GameObject '" + objectName + "' exists in active scene");
    }

    public static RequirementVerification compileSuccess() {
        return new RequirementVerification(VerificationType.COMPILE_SUCCESS, "ProjectScripts", "compile_project", "Compilation finishes with 0 errors");
    }

    public static RequirementVerification behaviorTest(String target, String testDescription, Map<String, Object> testParams) {
        return new RequirementVerification(VerificationType.BEHAVIOR_TEST, target, "run_game_test", testDescription, testParams);
    }

    public static RequirementVerification scriptExists(String scriptName) {
        return new RequirementVerification(VerificationType.SCRIPT_EXISTS, scriptName, "list_scripts", "Script '" + scriptName + "' exists in Assets/Scripts");
    }

    public static RequirementVerification componentExists(String objectName, String componentType) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("component", componentType);
        return new RequirementVerification(VerificationType.COMPONENT_EXISTS, objectName, "get_object_components", "Component '" + componentType + "' attached to '" + objectName + "'", p);
    }

    public VerificationType getType() { return type; }
    public void setType(VerificationType type) { this.type = type; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters != null ? parameters : new LinkedHashMap<>(); }

    public String getCandidateTool() { return candidateTool; }
    public void setCandidateTool(String candidateTool) { this.candidateTool = candidateTool; }

    public String getExpectedOutcome() { return expectedOutcome; }
    public void setExpectedOutcome(String expectedOutcome) { this.expectedOutcome = expectedOutcome; }

    @Override
    public String toString() {
        return "RequirementVerification{" +
                "type=" + type +
                ", target='" + target + '\'' +
                ", candidateTool='" + candidateTool + '\'' +
                '}';
    }
}
