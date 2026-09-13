package com.unityagent.agent.goal;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Objective requirement for a GameGoal.
 * Every requirement must have an objectively testable verification method.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoalRequirement {

    public enum RequirementType {
        GAME_OBJECT_EXISTS,
        COMPONENT_EXISTS,
        COMPONENT_PROPERTY,
        SCRIPT_EXISTS,
        SCRIPT_ATTACHED,
        PREFAB_EXISTS,
        PREFAB_INSTANCE_EXISTS,
        MATERIAL_ASSIGNED,
        UI_ELEMENT_EXISTS,
        ANIMATOR_CONFIGURED,
        AUDIO_SOURCE_CONFIGURED,
        INPUT_ACTION_EXISTS,
        NAVIGATION_CONFIGURED,
        CAMERA_CONFIGURED,
        LIGHTING_CONFIGURED,
        BUILD_SUCCESS,
        BEHAVIOR_TEST,
        NO_RUNTIME_ERRORS,
        COMPILE_SUCCESS,
        PLAY_MODE,
        SCENE_PROPERTY
    }

    public enum RequirementStatus {
        PENDING,
        IN_PROGRESS,
        SATISFIED,
        FAILED,
        BLOCKED,
        UNSUPPORTED,
        NOT_CHECKED
    }

    private String requirementId;
    private String description;
    private RequirementType type;
    private String target;
    private Map<String, Object> criteria;
    private String verificationMethod;
    private RequirementStatus status;
    private String failureReason;

    public GoalRequirement() {
        this.status = RequirementStatus.PENDING;
    }

    public GoalRequirement(String requirementId, String description, RequirementType type, String target,
                           Map<String, Object> criteria, String verificationMethod) {
        this.requirementId = requirementId;
        this.description = description;
        this.type = type;
        this.target = target;
        this.criteria = criteria;
        this.verificationMethod = verificationMethod;
        this.status = RequirementStatus.PENDING;
    }

    public String getRequirementId() { return requirementId; }
    public void setRequirementId(String requirementId) { this.requirementId = requirementId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public RequirementType getType() { return type; }
    public void setType(RequirementType type) { this.type = type; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public Map<String, Object> getCriteria() { return criteria; }
    public void setCriteria(Map<String, Object> criteria) { this.criteria = criteria; }

    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }

    public RequirementStatus getStatus() { return status; }
    public void setStatus(RequirementStatus status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public boolean isSatisfied() {
        return this.status == RequirementStatus.SATISFIED;
    }
}
