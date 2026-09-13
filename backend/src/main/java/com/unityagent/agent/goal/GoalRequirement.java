package com.unityagent.agent.goal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unityagent.agent.verification.RequirementVerification;
import com.unityagent.agent.verification.VerificationEvidence;

import java.util.*;

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
        SCENE_PROPERTY,
        SCENE_STATE,
        GAMEPLAY_STATE
    }

    /**
     * Backward-compatibility holder mapping static GoalRequirement.RequirementStatus references
     * to canonical com.unityagent.agent.goal.RequirementStatus.
     */
    public static final class RequirementStatus {
        public static final com.unityagent.agent.goal.RequirementStatus PENDING = com.unityagent.agent.goal.RequirementStatus.PENDING;
        public static final com.unityagent.agent.goal.RequirementStatus READY = com.unityagent.agent.goal.RequirementStatus.READY;
        public static final com.unityagent.agent.goal.RequirementStatus IN_PROGRESS = com.unityagent.agent.goal.RequirementStatus.IN_PROGRESS;
        public static final com.unityagent.agent.goal.RequirementStatus SATISFIED = com.unityagent.agent.goal.RequirementStatus.SATISFIED;
        public static final com.unityagent.agent.goal.RequirementStatus FAILED = com.unityagent.agent.goal.RequirementStatus.FAILED;
        public static final com.unityagent.agent.goal.RequirementStatus BLOCKED = com.unityagent.agent.goal.RequirementStatus.BLOCKED;
        public static final com.unityagent.agent.goal.RequirementStatus STALE = com.unityagent.agent.goal.RequirementStatus.STALE;
    }

    private String requirementId;
    private String description;
    private RequirementType type;
    private String target;
    private Map<String, Object> criteria;
    private List<AcceptanceCriterion> acceptanceCriteria;
    private String verificationMethod;
    private RequirementVerification verification;
    private com.unityagent.agent.goal.RequirementStatus status;
    private String failureReason;
    private boolean required;
    private List<String> prerequisiteIds;
    private List<VerificationEvidence> evidenceList;

    public GoalRequirement() {
        this.status = com.unityagent.agent.goal.RequirementStatus.PENDING;
        this.required = true;
        this.acceptanceCriteria = new ArrayList<>();
        this.prerequisiteIds = new ArrayList<>();
        this.evidenceList = new ArrayList<>();
    }

    public GoalRequirement(String requirementId, String description, RequirementType type, String target,
                           Map<String, Object> criteria, String verificationMethod) {
        this.requirementId = requirementId;
        this.description = description;
        this.type = type;
        this.target = target;
        this.criteria = criteria != null ? criteria : new LinkedHashMap<>();
        this.verificationMethod = verificationMethod;
        this.status = com.unityagent.agent.goal.RequirementStatus.PENDING;
        this.required = true;
        this.acceptanceCriteria = new ArrayList<>();
        this.prerequisiteIds = new ArrayList<>();
        this.evidenceList = new ArrayList<>();
    }

    public GoalRequirement(String requirementId, String description, RequirementType type, String target,
                           boolean required, RequirementVerification verification) {
        this.requirementId = requirementId;
        this.description = description;
        this.type = type;
        this.target = target;
        this.required = required;
        this.verification = verification;
        this.verificationMethod = verification != null ? verification.getCandidateTool() : null;
        this.status = com.unityagent.agent.goal.RequirementStatus.PENDING;
        this.criteria = new LinkedHashMap<>();
        this.acceptanceCriteria = new ArrayList<>();
        this.prerequisiteIds = new ArrayList<>();
        this.evidenceList = new ArrayList<>();
    }

    public GoalRequirement(String requirementId, String goalId, String description) {
        this(requirementId, description, RequirementType.BEHAVIOR_TEST, null, new LinkedHashMap<>(), null);
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

    public List<AcceptanceCriterion> getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(List<AcceptanceCriterion> acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria != null ? acceptanceCriteria : new ArrayList<>();
    }
    public void addCriterion(AcceptanceCriterion criterion) {
        if (criterion != null) this.acceptanceCriteria.add(criterion);
    }

    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }

    public RequirementVerification getVerification() { return verification; }
    public void setVerification(RequirementVerification verification) {
        this.verification = verification;
        if (verification != null && this.verificationMethod == null) {
            this.verificationMethod = verification.getCandidateTool();
        }
    }

    public com.unityagent.agent.goal.RequirementStatus getStatus() { return status; }
    public void setStatus(com.unityagent.agent.goal.RequirementStatus status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public List<String> getPrerequisiteIds() { return prerequisiteIds; }
    public void setPrerequisiteIds(List<String> prerequisiteIds) {
        this.prerequisiteIds = prerequisiteIds != null ? prerequisiteIds : new ArrayList<>();
    }
    public void addPrerequisite(String prerequisiteId) {
        if (prerequisiteId != null && !this.prerequisiteIds.contains(prerequisiteId)) {
            this.prerequisiteIds.add(prerequisiteId);
        }
    }

    public List<VerificationEvidence> getEvidenceList() { return evidenceList; }
    public void setEvidenceList(List<VerificationEvidence> evidenceList) {
        this.evidenceList = evidenceList != null ? evidenceList : new ArrayList<>();
    }
    public void addEvidence(VerificationEvidence evidence) {
        if (evidence != null) this.evidenceList.add(evidence);
    }

    public boolean isSatisfied() {
        return this.status == com.unityagent.agent.goal.RequirementStatus.SATISFIED;
    }

    @Override
    public String toString() {
        return "GoalRequirement{" +
                "id='" + requirementId + '\'' +
                ", desc='" + description + '\'' +
                ", required=" + required +
                ", status=" + status +
                '}';
    }
}
