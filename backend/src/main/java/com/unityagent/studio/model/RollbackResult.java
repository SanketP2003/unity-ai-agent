package com.unityagent.studio.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of executing a safe reconciled checkpoint rollback.
 * Strictly captures the mandatory post-rollback verification pipeline:
 * rollback -> compile -> inspect scene -> behavioral validation -> CompletionGate / validation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RollbackResult {

    public enum RollbackStatus {
        SUCCESS,
        ROLLBACK_REQUIRES_REVIEW,
        FAILED
    }

    private String checkpointId;
    private String projectId;
    private String runId;

    // File reconciliation
    private int filesRestored;
    private int filesRemoved;
    private int filesModified;

    // Pipeline Step 1: Compile
    private boolean compilePassed;
    private List<String> compileErrors = new ArrayList<>();

    // Pipeline Step 2: Inspect Scene
    private boolean sceneInspectionPassed;
    private List<String> sceneErrors = new ArrayList<>();

    // Pipeline Step 3: Behavioral Validation
    private boolean behavioralValidationPassed;
    private List<String> behavioralErrors = new ArrayList<>();

    // Pipeline Step 4: CompletionGate / Validation
    private boolean completionGatePassed;
    private List<String> validationErrors = new ArrayList<>();

    private RollbackStatus status;
    private String summary;
    private long timestamp;

    public RollbackResult() {
        this.timestamp = System.currentTimeMillis();
    }

    public RollbackResult(String checkpointId, String projectId, String runId) {
        this.checkpointId = checkpointId;
        this.projectId = projectId;
        this.runId = runId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getCheckpointId() { return checkpointId; }
    public void setCheckpointId(String checkpointId) { this.checkpointId = checkpointId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public int getFilesRestored() { return filesRestored; }
    public void setFilesRestored(int filesRestored) { this.filesRestored = filesRestored; }

    public int getFilesRemoved() { return filesRemoved; }
    public void setFilesRemoved(int filesRemoved) { this.filesRemoved = filesRemoved; }

    public int getFilesModified() { return filesModified; }
    public void setFilesModified(int filesModified) { this.filesModified = filesModified; }

    public boolean isCompilePassed() { return compilePassed; }
    public void setCompilePassed(boolean compilePassed) { this.compilePassed = compilePassed; }

    public List<String> getCompileErrors() { return compileErrors; }
    public void setCompileErrors(List<String> compileErrors) { this.compileErrors = compileErrors != null ? compileErrors : new ArrayList<>(); }

    public boolean isSceneInspectionPassed() { return sceneInspectionPassed; }
    public void setSceneInspectionPassed(boolean sceneInspectionPassed) { this.sceneInspectionPassed = sceneInspectionPassed; }

    public List<String> getSceneErrors() { return sceneErrors; }
    public void setSceneErrors(List<String> sceneErrors) { this.sceneErrors = sceneErrors != null ? sceneErrors : new ArrayList<>(); }

    public boolean isBehavioralValidationPassed() { return behavioralValidationPassed; }
    public void setBehavioralValidationPassed(boolean behavioralValidationPassed) { this.behavioralValidationPassed = behavioralValidationPassed; }

    public List<String> getBehavioralErrors() { return behavioralErrors; }
    public void setBehavioralErrors(List<String> behavioralErrors) { this.behavioralErrors = behavioralErrors != null ? behavioralErrors : new ArrayList<>(); }

    public boolean isCompletionGatePassed() { return completionGatePassed; }
    public void setCompletionGatePassed(boolean completionGatePassed) { this.completionGatePassed = completionGatePassed; }

    public List<String> getValidationErrors() { return validationErrors; }
    public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>(); }

    public RollbackStatus getStatus() { return status; }
    public void setStatus(RollbackStatus status) { this.status = status; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
