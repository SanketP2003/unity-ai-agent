package com.unityagent.agent.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ensures strict concurrency isolation across Unity projects.
 * Enforces Invariant 1 and Invariant 2:
 * - One project -> one authoritative Unity connection
 * - One active autonomous controller per project
 */
@Service
public class ProjectLockService {

    private static final Logger log = LoggerFactory.getLogger(ProjectLockService.class);

    /** Maps projectId -> active agentRunId */
    private final Map<String, String> projectLocks = new ConcurrentHashMap<>();

    /** Maps sessionId -> active agentRunId */
    private final Map<String, String> sessionLocks = new ConcurrentHashMap<>();

    /** Fine-grained lock per project for thread-safe lock acquisition */
    private final Map<String, ReentrantLock> projectMutexes = new ConcurrentHashMap<>();

    /**
     * Attempts to acquire the exclusive execution lock for a project.
     *
     * @param projectId the project identifier
     * @param runId     the autonomous run trying to acquire the lock
     * @throws ProjectConflictException if the project is already controlled by another active run
     */
    public void acquireProjectLock(String projectId, String runId) throws ProjectConflictException {
        if (projectId == null || projectId.isBlank()) {
            return; // Default or unassociated runs do not acquire project-specific lock
        }

        ReentrantLock mutex = projectMutexes.computeIfAbsent(projectId, k -> new ReentrantLock());
        mutex.lock();
        try {
            String existingRun = projectLocks.get(projectId);
            if (existingRun != null && !existingRun.equals(runId)) {
                log.warn("Project lock conflict: project {} is already locked by run {}, rejecting run {}",
                        projectId, existingRun, runId);
                throw new ProjectConflictException(projectId, existingRun,
                        String.format("Project '%s' is already being controlled by active run '%s'. Only one autonomous run may control a project at a time.",
                                projectId, existingRun));
            }
            projectLocks.put(projectId, runId);
            log.info("Acquired project lock on '{}' for run '{}'", projectId, runId);
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Releases the project lock if held by the specified run.
     */
    public void releaseProjectLock(String projectId, String runId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }

        ReentrantLock mutex = projectMutexes.computeIfAbsent(projectId, k -> new ReentrantLock());
        mutex.lock();
        try {
            String currentRun = projectLocks.get(projectId);
            if (runId.equals(currentRun)) {
                projectLocks.remove(projectId);
                log.info("Released project lock on '{}' held by run '{}'", projectId, runId);
            }
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Acquires session lock for a run.
     */
    public void acquireSessionLock(String sessionId, String runId) throws ProjectConflictException {
        if (sessionId == null || sessionId.isBlank()) return;

        synchronized (sessionLocks) {
            String existing = sessionLocks.get(sessionId);
            if (existing != null && !existing.equals(runId)) {
                throw new ProjectConflictException(sessionId, existing,
                        String.format("Session '%s' is already in use by run '%s'", sessionId, existing));
            }
            sessionLocks.put(sessionId, runId);
        }
    }

    /**
     * Releases session lock.
     */
    public void releaseSessionLock(String sessionId, String runId) {
        if (sessionId == null || sessionId.isBlank()) return;

        synchronized (sessionLocks) {
            if (runId.equals(sessionLocks.get(sessionId))) {
                sessionLocks.remove(sessionId);
            }
        }
    }

    /**
     * Checks whether a project is currently locked by an active run.
     */
    public boolean isProjectLocked(String projectId) {
        if (projectId == null) return false;
        return projectLocks.containsKey(projectId);
    }

    /**
     * Returns the active run controlling a project, or null if unlocked.
     */
    public String getActiveRunForProject(String projectId) {
        if (projectId == null) return null;
        return projectLocks.get(projectId);
    }

    /**
     * Clears all locks (primarily for test cleanup or emergency reset).
     */
    public void clearAllLocks() {
        projectLocks.clear();
        sessionLocks.clear();
        log.warn("All project and session locks cleared.");
    }
}
