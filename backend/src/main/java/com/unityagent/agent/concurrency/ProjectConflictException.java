package com.unityagent.agent.concurrency;

/**
 * Exception thrown when a project or session lock cannot be acquired because
 * another run is already actively controlling it.
 */
public class ProjectConflictException extends RuntimeException {

    private final String projectId;
    private final String activeRunId;

    public ProjectConflictException(String projectId, String activeRunId, String message) {
        super(message);
        this.projectId = projectId;
        this.activeRunId = activeRunId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getActiveRunId() {
        return activeRunId;
    }
}
