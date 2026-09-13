package com.unityagent.agent.verification;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Concrete, timestamped evidence produced during objective requirement verification.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationEvidence {

    private String evidenceId;
    private VerificationType verificationType;
    private String requirementId;
    private String sourceTool;
    private boolean success;
    private String summary;
    private Map<String, Object> details;
    private Instant timestamp;

    public VerificationEvidence() {
        this.timestamp = Instant.now();
        this.details = new LinkedHashMap<>();
    }

    public VerificationEvidence(String evidenceId, VerificationType verificationType, String requirementId,
                                String sourceTool, boolean success, String summary, Map<String, Object> details) {
        this.evidenceId = evidenceId;
        this.verificationType = verificationType;
        this.requirementId = requirementId;
        this.sourceTool = sourceTool;
        this.success = success;
        this.summary = summary;
        this.details = details != null ? new LinkedHashMap<>(details) : new LinkedHashMap<>();
        this.timestamp = Instant.now();
    }

    public VerificationEvidence(VerificationType verificationType, boolean success, String summary, Map<String, Object> details) {
        this("ev_" + System.currentTimeMillis(), verificationType, null, "BehaviorTestEngine", success, summary, details);
    }

    public static VerificationEvidence pass(String requirementId, VerificationType type, String tool, String summary) {
        return new VerificationEvidence("ev_" + System.currentTimeMillis(), type, requirementId, tool, true, summary, Map.of());
    }

    public static VerificationEvidence fail(String requirementId, VerificationType type, String tool, String summary) {
        return new VerificationEvidence("ev_" + System.currentTimeMillis(), type, requirementId, tool, false, summary, Map.of());
    }

    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String evidenceId) { this.evidenceId = evidenceId; }

    public VerificationType getVerificationType() { return verificationType; }
    public VerificationType getType() { return verificationType; }
    public void setVerificationType(VerificationType verificationType) { this.verificationType = verificationType; }

    public String getRequirementId() { return requirementId; }
    public void setRequirementId(String requirementId) { this.requirementId = requirementId; }

    public String getSourceTool() { return sourceTool; }
    public void setSourceTool(String sourceTool) { this.sourceTool = sourceTool; }

    public boolean isSuccess() { return success; }
    public boolean isVerified() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details != null ? details : new LinkedHashMap<>(); }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "VerificationEvidence{" +
                "type=" + verificationType +
                ", req='" + requirementId + '\'' +
                ", success=" + success +
                ", summary='" + summary + '\'' +
                '}';
    }
}
