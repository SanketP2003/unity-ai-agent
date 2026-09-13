package com.unityagent.agent.goal;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Constraint or invariant bound to a GameGoal (e.g., budget limits, performance targets, disallowed APIs).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoalConstraint {

    private String constraintId;
    private String name;
    private String description;
    private String constraintType;
    private Object targetValue;
    private boolean hardConstraint;

    public GoalConstraint() {
        this.hardConstraint = true;
    }

    public GoalConstraint(String constraintId, String name, String description, String constraintType, Object targetValue, boolean hardConstraint) {
        this.constraintId = constraintId;
        this.name = name;
        this.description = description;
        this.constraintType = constraintType;
        this.targetValue = targetValue;
        this.hardConstraint = hardConstraint;
    }

    public static GoalConstraint of(String name, String description, String constraintType, Object targetValue) {
        return new GoalConstraint("cst_" + name.toLowerCase().replaceAll("[^a-z0-9]", "_"), name, description, constraintType, targetValue, true);
    }

    public String getConstraintId() { return constraintId; }
    public void setConstraintId(String constraintId) { this.constraintId = constraintId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConstraintType() { return constraintType; }
    public void setConstraintType(String constraintType) { this.constraintType = constraintType; }

    public Object getTargetValue() { return targetValue; }
    public void setTargetValue(Object targetValue) { this.targetValue = targetValue; }

    public boolean isHardConstraint() { return hardConstraint; }
    public void setHardConstraint(boolean hardConstraint) { this.hardConstraint = hardConstraint; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GoalConstraint that = (GoalConstraint) o;
        return Objects.equals(constraintId, that.constraintId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(constraintId);
    }

    @Override
    public String toString() {
        return "GoalConstraint{" +
                "constraintId='" + constraintId + '\'' +
                ", name='" + name + '\'' +
                ", constraintType='" + constraintType + '\'' +
                ", hardConstraint=" + hardConstraint +
                '}';
    }
}
