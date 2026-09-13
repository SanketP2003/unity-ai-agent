package com.unityagent.agent.plan;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.*;

/**
 * Historical snapshot of a plan revision.
 * Strictly guarantees that completed nodes prior to revision are preserved immutably.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanRevision {

    private int revisionNumber;
    private Instant timestamp;
    private String triggerReason;
    private List<String> completedNodeIds;
    private List<String> addedNodeIds;
    private List<String> modifiedNodeIds;
    private List<String> removedNodeIds;
    private String notes;

    public PlanRevision() {
        this.timestamp = Instant.now();
        this.completedNodeIds = new ArrayList<>();
        this.addedNodeIds = new ArrayList<>();
        this.modifiedNodeIds = new ArrayList<>();
        this.removedNodeIds = new ArrayList<>();
    }

    public PlanRevision(int revisionNumber, String triggerReason,
                        List<String> completedNodeIds, List<String> addedNodeIds,
                        List<String> modifiedNodeIds, List<String> removedNodeIds, String notes) {
        this.revisionNumber = revisionNumber;
        this.triggerReason = triggerReason;
        this.completedNodeIds = completedNodeIds != null ? List.copyOf(completedNodeIds) : List.of();
        this.addedNodeIds = addedNodeIds != null ? List.copyOf(addedNodeIds) : List.of();
        this.modifiedNodeIds = modifiedNodeIds != null ? List.copyOf(modifiedNodeIds) : List.of();
        this.removedNodeIds = removedNodeIds != null ? List.copyOf(removedNodeIds) : List.of();
        this.notes = notes;
        this.timestamp = Instant.now();
    }

    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int revisionNumber) { this.revisionNumber = revisionNumber; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }

    public List<String> getCompletedNodeIds() { return completedNodeIds; }
    public void setCompletedNodeIds(List<String> completedNodeIds) {
        this.completedNodeIds = completedNodeIds != null ? List.copyOf(completedNodeIds) : List.of();
    }

    public List<String> getAddedNodeIds() { return addedNodeIds; }
    public void setAddedNodeIds(List<String> addedNodeIds) {
        this.addedNodeIds = addedNodeIds != null ? List.copyOf(addedNodeIds) : List.of();
    }

    public List<String> getModifiedNodeIds() { return modifiedNodeIds; }
    public void setModifiedNodeIds(List<String> modifiedNodeIds) {
        this.modifiedNodeIds = modifiedNodeIds != null ? List.copyOf(modifiedNodeIds) : List.of();
    }

    public List<String> getRemovedNodeIds() { return removedNodeIds; }
    public void setRemovedNodeIds(List<String> removedNodeIds) {
        this.removedNodeIds = removedNodeIds != null ? List.copyOf(removedNodeIds) : List.of();
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return "PlanRevision{" +
                "rev=" + revisionNumber +
                ", reason='" + triggerReason + '\'' +
                ", completed=" + completedNodeIds.size() +
                ", added=" + addedNodeIds.size() +
                '}';
    }
}
