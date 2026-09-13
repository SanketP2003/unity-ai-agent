package com.unityagent.agent.goal;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Machine-verifiable acceptance criterion for a GoalRequirement.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AcceptanceCriterion {

    public enum CriterionType {
        EXISTS,
        NOT_EXISTS,
        EQUALS,
        NOT_EQUALS,
        GREATER_THAN,
        LESS_THAN,
        CONTAINS,
        MATCHES_REGEX
    }

    private String criterionId;
    private String description;
    private CriterionType type;
    private String property;
    private Object expectedValue;
    private boolean satisfied;

    public AcceptanceCriterion() {
        this.satisfied = false;
    }

    public AcceptanceCriterion(String criterionId, String description, CriterionType type, String property, Object expectedValue) {
        this.criterionId = criterionId;
        this.description = description;
        this.type = type;
        this.property = property;
        this.expectedValue = expectedValue;
        this.satisfied = false;
    }

    public static AcceptanceCriterion exists(String criterionId, String description, String property) {
        return new AcceptanceCriterion(criterionId, description, CriterionType.EXISTS, property, true);
    }

    public static AcceptanceCriterion equalsVal(String criterionId, String description, String property, Object expectedValue) {
        return new AcceptanceCriterion(criterionId, description, CriterionType.EQUALS, property, expectedValue);
    }

    public String getCriterionId() { return criterionId; }
    public void setCriterionId(String criterionId) { this.criterionId = criterionId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public CriterionType getType() { return type; }
    public void setType(CriterionType type) { this.type = type; }

    public String getProperty() { return property; }
    public void setProperty(String property) { this.property = property; }

    public Object getExpectedValue() { return expectedValue; }
    public void setExpectedValue(Object expectedValue) { this.expectedValue = expectedValue; }

    public boolean isSatisfied() { return satisfied; }
    public void setSatisfied(boolean satisfied) { this.satisfied = satisfied; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AcceptanceCriterion that = (AcceptanceCriterion) o;
        return Objects.equals(criterionId, that.criterionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(criterionId);
    }

    @Override
    public String toString() {
        return "AcceptanceCriterion{" +
                "criterionId='" + criterionId + '\'' +
                ", description='" + description + '\'' +
                ", type=" + type +
                ", property='" + property + '\'' +
                ", satisfied=" + satisfied +
                '}';
    }
}
