package com.unityagent.agent.goal;

import com.unityagent.agent.verification.VerificationEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle and state transitions of GoalRequirements.
 * Computes prerequisite readiness and tracks objective satisfaction.
 */
public class RequirementManager {

    private static final Logger log = LoggerFactory.getLogger(RequirementManager.class);

    private final GameGoal goal;
    private final Map<String, GoalRequirement> requirementMap = new ConcurrentHashMap<>();

    public RequirementManager() {
        this(new GameGoal("default", "Default Goal"));
    }

    public RequirementManager(GameGoal goal) {
        this.goal = Objects.requireNonNull(goal, "GameGoal cannot be null");
        for (GoalRequirement req : goal.getRequirements()) {
            requirementMap.put(req.getRequirementId(), req);
        }
        updateReadiness();
    }

    public synchronized void updateReadiness() {
        for (GoalRequirement req : requirementMap.values()) {
            if (req.getStatus() == RequirementStatus.PENDING) {
                boolean allPrereqsSatisfied = req.getPrerequisiteIds().stream()
                        .map(requirementMap::get)
                        .filter(Objects::nonNull)
                        .allMatch(prereq -> prereq.getStatus() == RequirementStatus.SATISFIED);

                if (allPrereqsSatisfied) {
                    req.setStatus(RequirementStatus.READY);
                    log.debug("Requirement {} is now READY", req.getRequirementId());
                }
            } else if (req.getStatus() == RequirementStatus.READY) {
                // Check if any prerequisite became stale/failed
                boolean allPrereqsSatisfied = req.getPrerequisiteIds().stream()
                        .map(requirementMap::get)
                        .filter(Objects::nonNull)
                        .allMatch(prereq -> prereq.getStatus() == RequirementStatus.SATISFIED);

                if (!allPrereqsSatisfied) {
                    req.setStatus(RequirementStatus.BLOCKED);
                    log.debug("Requirement {} is now BLOCKED due to unsatisfied prerequisites", req.getRequirementId());
                }
            }
        }
    }

    public synchronized boolean markInProgress(String requirementId) {
        GoalRequirement req = requirementMap.get(requirementId);
        if (req != null && (req.getStatus() == RequirementStatus.READY || req.getStatus() == RequirementStatus.PENDING)) {
            req.setStatus(RequirementStatus.IN_PROGRESS);
            return true;
        }
        return false;
    }

    public synchronized boolean markSatisfied(String requirementId, VerificationEvidence evidence) {
        GoalRequirement req = requirementMap.get(requirementId);
        if (req != null) {
            req.setStatus(RequirementStatus.SATISFIED);
            if (evidence != null) {
                req.addEvidence(evidence);
            }
            log.info("Requirement {} marked SATISFIED: {}", requirementId, req.getDescription());
            updateReadiness();
            return true;
        }
        return false;
    }

    public synchronized boolean markFailed(String requirementId, String failureReason) {
        GoalRequirement req = requirementMap.get(requirementId);
        if (req != null) {
            req.setStatus(RequirementStatus.FAILED);
            req.setFailureReason(failureReason);
            log.warn("Requirement {} marked FAILED: {}", requirementId, failureReason);
            updateReadiness();
            return true;
        }
        return false;
    }

    public synchronized boolean markBlocked(String requirementId, String reason) {
        GoalRequirement req = requirementMap.get(requirementId);
        if (req != null) {
            req.setStatus(RequirementStatus.BLOCKED);
            req.setFailureReason(reason);
            return true;
        }
        return false;
    }

    public synchronized boolean markStale(String requirementId) {
        GoalRequirement req = requirementMap.get(requirementId);
        if (req != null) {
            req.setStatus(RequirementStatus.STALE);
            log.info("Requirement {} marked STALE due to scene/script mutation", requirementId);
            updateReadiness();
            return true;
        }
        return false;
    }

    public List<GoalRequirement> getReadyRequirements() {
        return requirementMap.values().stream()
                .filter(r -> r.getStatus() == RequirementStatus.READY)
                .collect(Collectors.toList());
    }

    public List<GoalRequirement> getPendingRequirements() {
        return requirementMap.values().stream()
                .filter(r -> r.getStatus() == RequirementStatus.PENDING)
                .collect(Collectors.toList());
    }

    public List<GoalRequirement> getSatisfiedRequirements() {
        return requirementMap.values().stream()
                .filter(r -> r.getStatus() == RequirementStatus.SATISFIED)
                .collect(Collectors.toList());
    }

    public List<GoalRequirement> getRequirementsByStatus(RequirementStatus status) {
        return requirementMap.values().stream()
                .filter(r -> r.getStatus() == status)
                .collect(Collectors.toList());
    }

    public GoalRequirement getRequirement(String requirementId) {
        return requirementMap.get(requirementId);
    }

    public GameGoal getGoal() {
        return goal;
    }

    public boolean isAllRequiredSatisfied() {
        return goal.allRequiredRequirementsSatisfied();
    }
}
