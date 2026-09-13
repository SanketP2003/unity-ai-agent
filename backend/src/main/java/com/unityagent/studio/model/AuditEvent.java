package com.unityagent.studio.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Immutable audit event recorded in the system audit trail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditEvent {
    private String eventId;
    private String timestamp;
    private String userId;
    private String projectId;
    private String runId;
    private String action;
    private String target;
    private String result;
    private String details;

    public AuditEvent() {}

    public AuditEvent(String eventId, String timestamp, String userId, String projectId,
                      String runId, String action, String target, String result, String details) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.userId = userId;
        this.projectId = projectId;
        this.runId = runId;
        this.action = action;
        this.target = target;
        this.result = result;
        this.details = details;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
