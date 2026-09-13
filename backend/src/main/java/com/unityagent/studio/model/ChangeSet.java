package com.unityagent.studio.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a group of related changes produced by an autonomous plan node or tool execution.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChangeSet {
    private String changeSetId;
    private String projectId;
    private String agentRunId;
    private String planNodeId;
    private String summary;
    private ApprovalState status;
    private List<ChangeEntry> entries = new ArrayList<>();
    private RiskLevel riskLevel;
    private boolean requiresHumanApproval;
    private String createdAt;
    private String reviewedAt;
    private String reviewedBy;
    private String rejectionReason;

    public ChangeSet() {}

    public ChangeSet(String changeSetId, String projectId, String agentRunId, String planNodeId,
                     String summary, ApprovalState status, String createdAt) {
        this.changeSetId = changeSetId;
        this.projectId = projectId;
        this.agentRunId = agentRunId;
        this.planNodeId = planNodeId;
        this.summary = summary;
        this.status = status;
        this.createdAt = createdAt;
        this.riskLevel = RiskLevel.LOW;
    }

    public String getChangeSetId() { return changeSetId; }
    public void setChangeSetId(String changeSetId) { this.changeSetId = changeSetId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }

    public String getPlanNodeId() { return planNodeId; }
    public void setPlanNodeId(String planNodeId) { this.planNodeId = planNodeId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public ApprovalState getStatus() { return status; }
    public void setStatus(ApprovalState status) { this.status = status; }

    public List<ChangeEntry> getEntries() { return entries; }
    public void setEntries(List<ChangeEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
        recomputeRisk();
    }

    public void addEntry(ChangeEntry entry) {
        if (entry != null) {
            this.entries.add(entry);
            recomputeRisk();
        }
    }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public boolean isRequiresHumanApproval() { return requiresHumanApproval; }
    public void setRequiresHumanApproval(boolean requiresHumanApproval) { this.requiresHumanApproval = requiresHumanApproval; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(String reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public void recomputeRisk() {
        RiskLevel max = RiskLevel.LOW;
        for (ChangeEntry e : entries) {
            if (e.getRiskLevel() == RiskLevel.HIGH) {
                max = RiskLevel.HIGH;
                break;
            } else if (e.getRiskLevel() == RiskLevel.MEDIUM && max != RiskLevel.HIGH) {
                max = RiskLevel.MEDIUM;
            }
        }
        this.riskLevel = max;
        this.requiresHumanApproval = (max == RiskLevel.HIGH);
    }
}
