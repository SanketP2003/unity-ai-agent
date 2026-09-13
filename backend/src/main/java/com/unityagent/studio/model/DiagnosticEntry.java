package com.unityagent.studio.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured diagnostic event spanning the 9 studio categories.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiagnosticEntry {

    public enum Category {
        COMPILATION,
        RUNTIME,
        UNITY,
        PROVIDER,
        TOOLS,
        RECOVERY,
        VALIDATION,
        SECURITY,
        BUILD
    }

    public enum Severity {
        INFO,
        WARN,
        ERROR,
        CRITICAL
    }

    private String id;
    private String timestamp;
    private String projectId;
    private Category category;
    private Severity severity;
    private String source;
    private String message;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public DiagnosticEntry() {}

    public DiagnosticEntry(String id, String timestamp, String projectId, Category category,
                           Severity severity, String source, String message, Map<String, Object> metadata) {
        this.id = id;
        this.timestamp = timestamp;
        this.projectId = projectId;
        this.category = category;
        this.severity = severity;
        this.source = source;
        this.message = message;
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }
}
