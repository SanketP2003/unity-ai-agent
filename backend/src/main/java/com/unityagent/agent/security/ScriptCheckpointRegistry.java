package com.unityagent.agent.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory lightweight checkpoint registry for scripts modified during an autonomous run.
 * Tracks known-good initial content, original hash, and current hash.
 */
@Component
public class ScriptCheckpointRegistry {

    public static class ScriptCheckpoint {
        private final String path;
        private final String originalContent;
        private final String originalHash;
        private volatile String currentHash;
        private volatile String currentContent;

        public ScriptCheckpoint(String path, String content, String hash) {
            this.path = path;
            this.originalContent = content;
            this.originalHash = hash;
            this.currentContent = content;
            this.currentHash = hash;
        }

        public String getPath() { return path; }
        public String getOriginalContent() { return originalContent; }
        public String getOriginalHash() { return originalHash; }
        public String getCurrentHash() { return currentHash; }
        public String getCurrentContent() { return currentContent; }

        public void updateCurrent(String newContent, String newHash) {
            this.currentContent = newContent;
            this.currentHash = newHash;
        }
    }

    // Keyed by agentRunId -> (path -> ScriptCheckpoint)
    private final Map<String, Map<String, ScriptCheckpoint>> checkpointsByRun = new ConcurrentHashMap<>();

    public void recordInitial(String agentRunId, String path, String content, String hash) {
        checkpointsByRun.computeIfAbsent(agentRunId, k -> new ConcurrentHashMap<>())
                .putIfAbsent(path, new ScriptCheckpoint(path, content, hash));
    }

    public void recordUpdate(String agentRunId, String path, String newContent, String newHash) {
        Map<String, ScriptCheckpoint> runMap = checkpointsByRun.get(agentRunId);
        if (runMap != null) {
            ScriptCheckpoint cp = runMap.get(path);
            if (cp != null) {
                cp.updateCurrent(newContent, newHash);
            }
        }
    }

    public ScriptCheckpoint getCheckpoint(String agentRunId, String path) {
        Map<String, ScriptCheckpoint> runMap = checkpointsByRun.get(agentRunId);
        return runMap != null ? runMap.get(path) : null;
    }

    public void clearRun(String agentRunId) {
        checkpointsByRun.remove(agentRunId);
    }
}
