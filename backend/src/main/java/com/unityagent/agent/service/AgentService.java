package com.unityagent.agent.service;

import com.unityagent.agent.AgentLoop;
import com.unityagent.agent.CancellationToken;
import com.unityagent.agent.event.AgentEvent;
import com.unityagent.agent.event.AgentEventListener;
import com.unityagent.agent.memory.ConversationMemory;
import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.model.ChatMessage;
import com.unityagent.agent.model.ToolExecutionResult;
import com.unityagent.tools.ToolMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * High-level orchestration service managing autonomous agent runs,
 * session concurrency, lifecycle events, and real-time SSE subscriptions.
 *
 * <p>Session Concurrency Rule:
 * Strictly enforces that only one active AgentLoop run is permitted per session.
 * Attempting to start a concurrent run on an active session yields a conflict.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentLoop agentLoop;
    private final ConversationMemory memory;
    private final ExecutorService executor;
    private final com.unityagent.memory.listener.MemoryEventListener memoryEventListener;
    private final com.unityagent.memory.service.MemoryRetriever memoryRetriever;
    private final com.unityagent.memory.service.ProjectMemoryService projectMemoryService;

    // In-memory run state tracking (Authoritative source of truth)
    private final Map<String, AgentRunState> runs = new ConcurrentHashMap<>();

    // Active run mapping per session to enforce the one-run-per-session rule
    private final Map<String, String> activeSessionRuns = new ConcurrentHashMap<>();

    // Real-time SSE subscribers per session
    private final Map<String, List<SseEmitter>> sseEmitters = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AgentService(AgentLoop agentLoop,
                        ConversationMemory memory,
                        @org.springframework.beans.factory.annotation.Autowired(required = false)
                        com.unityagent.memory.listener.MemoryEventListener memoryEventListener,
                        @org.springframework.beans.factory.annotation.Autowired(required = false)
                        com.unityagent.memory.service.MemoryRetriever memoryRetriever,
                        @org.springframework.beans.factory.annotation.Autowired(required = false)
                        com.unityagent.memory.service.ProjectMemoryService projectMemoryService) {
        this.agentLoop = agentLoop;
        this.memory = memory;
        this.memoryEventListener = memoryEventListener;
        this.memoryRetriever = memoryRetriever;
        this.projectMemoryService = projectMemoryService;
        // Managed thread pool for agent executions
        this.executor = Executors.newFixedThreadPool(8, new ThreadFactory() {
            private int counter = 1;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "agent-worker-" + counter++);
                t.setDaemon(true);
                return t;
            }
        });
    }

    public AgentService(AgentLoop agentLoop, ConversationMemory memory) {
        this(agentLoop, memory, null, null, null);
    }

    /**
     * Starts an autonomous run asynchronously.
     * Enforces that only one active run may exist for a session.
     *
     * @return the created AgentRunState
     * @throws SessionConflictException if a run is already active for this session
     */
    public AgentRunState startRunAsync(String sessionId, String agentRunId, String message, ToolMode mode) {
        return startRunAsync(sessionId, agentRunId, message, mode, null);
    }

    public AgentRunState startRunAsync(String sessionId, String agentRunId, String message, ToolMode mode, String projectId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(agentRunId, "agentRunId cannot be null");
        Objects.requireNonNull(message, "message cannot be null");

        // Enforce one-active-run-per-session rule
        synchronized (this) {
            String existingRunId = activeSessionRuns.get(sessionId);
            if (existingRunId != null) {
                AgentRunState existingState = runs.get(existingRunId);
                if (existingState != null && existingState.isActive()) {
                    log.warn("Session '{}' already has active run '{}'. Rejecting new run '{}'",
                            sessionId, existingRunId, agentRunId);
                    throw new SessionConflictException(
                            "Another agent run is already active for this session (" + existingRunId + ").");
                }
            }
            activeSessionRuns.put(sessionId, agentRunId);
        }

        CancellationToken token = new CancellationToken();
        AgentRunState state = new AgentRunState(agentRunId, sessionId, message, token);
        state.setProjectId(projectId);
        runs.put(agentRunId, state);

        log.info("Queued asynchronous agent run: sessionId={}, agentRunId={}, projectId={}", sessionId, agentRunId, projectId);

        // Submit to managed executor
        executor.submit(() -> executeRun(state, message, mode));

        return state;
    }

    /**
     * Executes the run synchronously (for backward compatibility).
     */
    public AgentRunResult runSync(String sessionId, String agentRunId, String message, ToolMode mode) {
        return runSync(sessionId, agentRunId, message, mode, null);
    }

    public AgentRunResult runSync(String sessionId, String agentRunId, String message, ToolMode mode, String projectId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(agentRunId, "agentRunId cannot be null");

        synchronized (this) {
            String existingRunId = activeSessionRuns.get(sessionId);
            if (existingRunId != null) {
                AgentRunState existingState = runs.get(existingRunId);
                if (existingState != null && existingState.isActive()) {
                    throw new SessionConflictException("Another agent run is already active for this session.");
                }
            }
            activeSessionRuns.put(sessionId, agentRunId);
        }

        CancellationToken token = new CancellationToken();
        AgentRunState state = new AgentRunState(agentRunId, sessionId, message, token);
        state.setProjectId(projectId);
        runs.put(agentRunId, state);

        return executeRun(state, message, mode);
    }

    private AgentRunResult executeRun(AgentRunState state, String message, ToolMode mode) {
        String sessionId = state.getSessionId();
        String agentRunId = state.getAgentRunId();
        state.markRunning();

        AgentEventListener listener = new AgentEventListener() {
            @Override
            public void onRunStarted(String sessId, String runId, String userMsg) {
                AgentEvent evt = AgentEvent.runStarted(state.nextSequence(), sessId, runId, userMsg);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onActivity(String runId, String activity) {
                state.setCurrentActivity(activity);
                AgentEvent evt = AgentEvent.activity(state.nextSequence(), sessionId, runId, activity);
                state.addEvent(evt);
                broadcastEvent(sessionId, evt);
            }

            @Override
            public void onToolStarted(String runId, String toolCallId, String toolName, Map<String, Object> arguments) {
                state.setCurrentActivity("Executing " + toolName + "...");
                AgentEvent evt = AgentEvent.toolStarted(state.nextSequence(), sessionId, runId, toolCallId, toolName, arguments);
                state.addEvent(evt);
                broadcastEvent(sessionId, evt);
            }

            @Override
            public void onToolCompleted(String runId, String toolCallId, String toolName, ToolExecutionResult result) {
                AgentEvent evt = AgentEvent.toolCompleted(state.nextSequence(), sessionId, runId, toolCallId, toolName, result.getResultData());
                state.addEvent(evt);
                broadcastEvent(sessionId, evt);
            }

            @Override
            public void onToolFailed(String runId, String toolCallId, String toolName, ToolExecutionResult result) {
                AgentEvent evt = AgentEvent.toolFailed(state.nextSequence(), sessionId, runId, toolCallId, toolName, result.getErrorMessage());
                state.addEvent(evt);
                broadcastEvent(sessionId, evt);
            }

            @Override
            public void onRunCompleted(String sessId, String runId, AgentRunResult result) {
                state.markCompleted(result);
                AgentEvent evt = AgentEvent.runCompleted(state.nextSequence(), sessId, runId, result.getResponse());
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
                activeSessionRuns.remove(sessId, runId);
            }

            @Override
            public void onRunFailed(String sessId, String runId, String errorMessage) {
                state.markFailed(errorMessage);
                AgentEvent evt = AgentEvent.runFailed(state.nextSequence(), sessId, runId, errorMessage);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
                activeSessionRuns.remove(sessId, runId);
            }

            @Override
            public void onRunCancelled(String sessId, String runId) {
                state.markCancelled();
                AgentEvent evt = AgentEvent.runCancelled(state.nextSequence(), sessId, runId);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
                activeSessionRuns.remove(sessId, runId);
            }

            @Override
            public void onEvent(AgentEvent evt) {
                if (evt != null) {
                    state.addEvent(evt);
                    broadcastEvent(sessionId, evt);
                }
            }

            @Override
            public void onGoalCreated(String sessId, String runId, String description) {
                AgentEvent evt = AgentEvent.goalCreated(state.nextSequence(), sessId, runId, description);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onGoalCompleted(String sessId, String runId, Object summary) {
                AgentEvent evt = AgentEvent.goalCompleted(state.nextSequence(), sessId, runId, summary);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onGoalFailed(String sessId, String runId, String reason) {
                AgentEvent evt = AgentEvent.goalFailed(state.nextSequence(), sessId, runId, reason);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onPlanCreated(String sessId, String runId, Object plan) {
                AgentEvent evt = AgentEvent.planCreated(state.nextSequence(), sessId, runId, plan);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onPlanStepStarted(String sessId, String runId, String stepId, String description) {
                state.setCurrentActivity(description);
                AgentEvent evt = AgentEvent.planStepStarted(state.nextSequence(), sessId, runId, stepId, description);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onPlanStepCompleted(String sessId, String runId, String stepId, String description) {
                AgentEvent evt = AgentEvent.planStepCompleted(state.nextSequence(), sessId, runId, stepId, description);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onCompilationStarted(String sessId, String runId) {
                state.setCurrentActivity("Compiling project...");
                AgentEvent evt = AgentEvent.compilationStarted(state.nextSequence(), sessId, runId);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onCompilationCompleted(String sessId, String runId, boolean success, Object diagnostics) {
                state.setCurrentActivity(success ? "Compilation succeeded." : "Compilation failed.");
                AgentEvent evt = AgentEvent.compilationCompleted(state.nextSequence(), sessId, runId, success, diagnostics);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onDiagnosisStarted(String sessId, String runId, String diagnosticSummary) {
                state.setCurrentActivity("Diagnosing: " + diagnosticSummary);
                AgentEvent evt = AgentEvent.diagnosisStarted(state.nextSequence(), sessId, runId, diagnosticSummary);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onRepairStarted(String sessId, String runId, String targetFile) {
                state.setCurrentActivity("Repairing " + targetFile + "...");
                AgentEvent evt = AgentEvent.repairStarted(state.nextSequence(), sessId, runId, targetFile);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onRecoveryLimitReached(String sessId, String runId, String limitDetails) {
                state.setCurrentActivity("Recovery limit reached: " + limitDetails);
                AgentEvent evt = AgentEvent.recoveryLimitReached(state.nextSequence(), sessId, runId, limitDetails);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onRuntimeTestStarted(String sessId, String runId, String testId) {
                state.setCurrentActivity("Starting runtime test...");
                AgentEvent evt = AgentEvent.runtimeTestStarted(state.nextSequence(), sessId, runId, testId);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onRuntimeTestCompleted(String sessId, String runId, boolean success, Object details) {
                state.setCurrentActivity(success ? "Runtime test passed." : "Runtime test failed.");
                AgentEvent evt = AgentEvent.runtimeTestCompleted(state.nextSequence(), sessId, runId, success, details);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onValidationStarted(String sessId, String runId, String validationType) {
                state.setCurrentActivity("Validating goal requirements...");
                AgentEvent evt = AgentEvent.validationStarted(state.nextSequence(), sessId, runId, validationType);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }

            @Override
            public void onValidationCompleted(String sessId, String runId, boolean success, Object results) {
                state.setCurrentActivity(success ? "Goal verified." : "Validation failed.");
                AgentEvent evt = AgentEvent.validationCompleted(state.nextSequence(), sessId, runId, success, results);
                state.addEvent(evt);
                broadcastEvent(sessId, evt);
            }
        };

        if (memoryEventListener != null) {
            memoryEventListener.setRunContext(state.getProjectId(), sessionId, agentRunId, message);
        }

        String memoryContextText = null;
        if (memoryRetriever != null && state.getProjectId() != null && !state.getProjectId().isBlank()) {
            try {
                com.unityagent.memory.service.MemoryContext ctx = memoryRetriever.getRelevantContext(state.getProjectId(), message);
                if (ctx != null && ctx.hasContent()) {
                    memoryContextText = ctx.format();
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve persistent memory context (non-fatal): {}", e.getMessage());
            }
        }

        AgentEventListener combinedListener = (memoryEventListener != null)
                ? new com.unityagent.agent.event.CompositeEventListener(listener, memoryEventListener)
                : listener;

        try {
            if ((state.getProjectId() == null || state.getProjectId().isBlank()) && memoryContextText == null) {
                return agentLoop.run(sessionId, agentRunId, message, mode, state.getCancellationToken(), combinedListener);
            } else {
                return agentLoop.run(sessionId, agentRunId, message, mode, state.getCancellationToken(), combinedListener, state.getProjectId(), memoryContextText);
            }
        } catch (Exception e) {
            log.error("Unexpected failure executing agent run: {}", e.getMessage(), e);
            state.markFailed(e.getMessage());
            AgentEvent evt = AgentEvent.runFailed(state.nextSequence(), sessionId, agentRunId, e.getMessage());
            state.addEvent(evt);
            broadcastEvent(sessionId, evt);
            activeSessionRuns.remove(sessionId, agentRunId);
            return AgentRunResult.failure(sessionId, agentRunId, com.unityagent.agent.model.ErrorType.PROVIDER_ERROR, e.getMessage(), 0, 0, List.of(), message);
        }
    }

    public Optional<AgentRunState> getRunState(String agentRunId) {
        return Optional.ofNullable(runs.get(agentRunId));
    }

    public boolean cancelRun(String agentRunId) {
        AgentRunState state = runs.get(agentRunId);
        if (state != null && state.isActive()) {
            log.info("Cancelling agent run: {}", agentRunId);
            state.markCancelled();
            activeSessionRuns.remove(state.getSessionId(), agentRunId);
            AgentEvent evt = AgentEvent.runCancelled(state.nextSequence(), state.getSessionId(), agentRunId);
            state.addEvent(evt);
            broadcastEvent(state.getSessionId(), evt);
            return true;
        }
        return false;
    }

    public Map<String, Object> getSessionData(String sessionId) {
        List<ChatMessage> history = memory.getHistory(sessionId);
        String activeRunId = activeSessionRuns.get(sessionId);
        AgentRunState activeState = activeRunId != null ? runs.get(activeRunId) : null;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("active", activeState != null && activeState.isActive());
        data.put("activeRunId", activeState != null && activeState.isActive() ? activeRunId : null);
        data.put("messages", history);
        return data;
    }

    public SseEmitter subscribeToEvents(String sessionId) {
        // 5-minute timeout for SSE emitter
        SseEmitter emitter = new SseEmitter(300_000L);

        List<SseEmitter> list = sseEmitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of("sessionId", sessionId, "status", "CONNECTED", "timestamp", System.currentTimeMillis())));
        } catch (IOException e) {
            list.remove(emitter);
        }

        return emitter;
    }

    private void broadcastEvent(String sessionId, AgentEvent event) {
        List<SseEmitter> list = sseEmitters.get(sessionId);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(event.getSequence()))
                        .name(event.getType())
                        .data(event));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        list.removeAll(deadEmitters);
    }

    public void cleanup() {
        executor.shutdown();
    }
}
