package com.unityagent.studio.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Activity log event in the Studio project activity stream.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectActivity {

    private String activityId;
    private String projectId;
    private String type;
    private String title;
    private String description;
    private Instant timestamp;
    private String severity; // INFO, WARNING, ERROR, SUCCESS

    public ProjectActivity() {
        this.timestamp = Instant.now();
        this.severity = "INFO";
    }

    public ProjectActivity(String activityId, String projectId, String type, String title, String description, String severity) {
        this.activityId = activityId;
        this.projectId = projectId;
        this.type = type;
        this.title = title;
        this.description = description;
        this.timestamp = Instant.now();
        this.severity = severity != null ? severity : "INFO";
    }

    public String getActivityId() { return activityId; }
    public void setActivityId(String activityId) { this.activityId = activityId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}
