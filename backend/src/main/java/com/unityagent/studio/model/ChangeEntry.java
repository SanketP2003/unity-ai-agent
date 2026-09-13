package com.unityagent.studio.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a single atomic change within a ChangeSet.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChangeEntry {
    private String entryId;
    private String changeSetId;
    private ChangeType changeType;
    private String targetPath;
    private String beforeHash;
    private String afterHash;
    private String diffContent;
    private RiskLevel riskLevel;
    private ApprovalState approvalState;
    private String rationale;

    public ChangeEntry() {}

    public ChangeEntry(String entryId, String changeSetId, ChangeType changeType, String targetPath,
                       String beforeHash, String afterHash, String diffContent,
                       RiskLevel riskLevel, ApprovalState approvalState, String rationale) {
        this.entryId = entryId;
        this.changeSetId = changeSetId;
        this.changeType = changeType;
        this.targetPath = targetPath;
        this.beforeHash = beforeHash;
        this.afterHash = afterHash;
        this.diffContent = diffContent;
        this.riskLevel = riskLevel;
        this.approvalState = approvalState;
        this.rationale = rationale;
    }

    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    public String getChangeSetId() { return changeSetId; }
    public void setChangeSetId(String changeSetId) { this.changeSetId = changeSetId; }

    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }

    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }

    public String getBeforeHash() { return beforeHash; }
    public void setBeforeHash(String beforeHash) { this.beforeHash = beforeHash; }

    public String getAfterHash() { return afterHash; }
    public void setAfterHash(String afterHash) { this.afterHash = afterHash; }

    public String getDiffContent() { return diffContent; }
    public void setDiffContent(String diffContent) { this.diffContent = diffContent; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public ApprovalState getApprovalState() { return approvalState; }
    public void setApprovalState(ApprovalState approvalState) { this.approvalState = approvalState; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
}
