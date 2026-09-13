package com.unityagent.agent.service;

import com.unityagent.agent.AgentLoop;
import com.unityagent.agent.CancellationToken;
import com.unityagent.agent.event.AgentEventListener;
import com.unityagent.agent.memory.ConversationMemory;
import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.tools.ToolMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    private AgentLoop mockAgentLoop;
    private ConversationMemory memory;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        mockAgentLoop = mock(AgentLoop.class);
        memory = new ConversationMemory();
        agentService = new AgentService(mockAgentLoop, memory);
    }

    @Test
    void startRunAsyncStartsRunAndRecordsState() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        when(mockAgentLoop.run(anyString(), anyString(), anyString(), any(ToolMode.class), any(CancellationToken.class), any(AgentEventListener.class)))
                .thenAnswer(inv -> {
                    String sessId = inv.getArgument(0);
                    String runId = inv.getArgument(1);
                    AgentEventListener listener = inv.getArgument(5);
                    listener.onRunStarted(sessId, runId, "Test message");
                    listener.onActivity(runId, "Thinking...");
                    AgentRunResult result = AgentRunResult.success(sessId, runId, "Hello", 1, 0, List.of());
                    listener.onRunCompleted(sessId, runId, result);
                    latch.countDown();
                    return result;
                });

        AgentRunState state = agentService.startRunAsync("sess_1", "run_1", "Test message", ToolMode.BOTH);
        assertNotNull(state);
        assertEquals("run_1", state.getAgentRunId());
        assertEquals("sess_1", state.getSessionId());

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        // Give background worker a moment to update state
        Thread.sleep(100);

        assertEquals(AgentRunState.Status.COMPLETED, state.getStatus());
        assertFalse(state.getEvents().isEmpty());
        assertEquals("evt_1", state.getEvents().get(0).getEventId());
        assertEquals(1, state.getEvents().get(0).getSequence());
    }

    @Test
    void startRunAsyncEnforcesOneRunPerSessionConflict() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        when(mockAgentLoop.run(anyString(), anyString(), anyString(), any(ToolMode.class), any(CancellationToken.class), any(AgentEventListener.class)))
                .thenAnswer(inv -> {
                    blockLatch.await(5, TimeUnit.SECONDS);
                    return AgentRunResult.success("sess_conflict", "run_active", "Done", 1, 0, List.of());
                });

        AgentRunState state1 = agentService.startRunAsync("sess_conflict", "run_active", "First run", ToolMode.BOTH);
        assertNotNull(state1);

        // Attempting second run on same session must throw SessionConflictException
        assertThrows(SessionConflictException.class, () ->
                agentService.startRunAsync("sess_conflict", "run_second", "Second run", ToolMode.BOTH)
        );

        blockLatch.countDown();
    }

    @Test
    void cancelRunCancelsActiveRun() throws Exception {
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch blockLatch = new CountDownLatch(1);

        when(mockAgentLoop.run(anyString(), anyString(), anyString(), any(ToolMode.class), any(CancellationToken.class), any(AgentEventListener.class)))
                .thenAnswer(inv -> {
                    startedLatch.countDown();
                    blockLatch.await(5, TimeUnit.SECONDS);
                    return AgentRunResult.cancelled("sess_cancel", "run_cancel", 1, 0, List.of());
                });

        AgentRunState state = agentService.startRunAsync("sess_cancel", "run_cancel", "Cancel test", ToolMode.BOTH);
        assertTrue(startedLatch.await(2, TimeUnit.SECONDS));

        boolean cancelled = agentService.cancelRun("run_cancel");
        assertTrue(cancelled);
        assertEquals(AgentRunState.Status.CANCELLED, state.getStatus());
        assertTrue(state.getCancellationToken().isCancelled());

        blockLatch.countDown();
    }

    @Test
    void subscribeToEventsReturnsValidSseEmitter() {
        SseEmitter emitter = agentService.subscribeToEvents("sess_sse");
        assertNotNull(emitter);
    }

    @Test
    void getSessionDataReturnsCorrectStructure() {
        var data = agentService.getSessionData("sess_data");
        assertNotNull(data);
        assertEquals("sess_data", data.get("sessionId"));
        assertEquals(false, data.get("active"));
        assertNotNull(data.get("messages"));
    }

    @Test
    void testRunSyncReturnsDirectResult() {
        when(mockAgentLoop.run(eq("sess_sync"), eq("run_sync"), eq("Sync message"), eq(ToolMode.BOTH), any(CancellationToken.class), any(AgentEventListener.class)))
                .thenReturn(AgentRunResult.success("sess_sync", "run_sync", "Direct sync response", 2, 3, List.of()));

        AgentRunResult result = agentService.runSync("sess_sync", "run_sync", "Sync message", ToolMode.BOTH);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("sess_sync", result.getSessionId());
        assertEquals("run_sync", result.getAgentRunId());
        assertEquals("Direct sync response", result.getResponse());
        assertEquals(2, result.getIterations());
        assertEquals(3, result.getTotalToolCalls());
    }

    @Test
    void testGetRunStateReturnsCorrectStateAfterCompletion() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        when(mockAgentLoop.run(eq("sess_state"), eq("run_state"), eq("State test"), eq(ToolMode.BOTH), any(CancellationToken.class), any(AgentEventListener.class)))
                .thenAnswer(inv -> {
                    AgentEventListener listener = inv.getArgument(5);
                    listener.onRunStarted("sess_state", "run_state", "State test");
                    AgentRunResult res = AgentRunResult.success("sess_state", "run_state", "Done state", 1, 1, List.of());
                    listener.onRunCompleted("sess_state", "run_state", res);
                    latch.countDown();
                    return res;
                });

        AgentRunState state = agentService.startRunAsync("sess_state", "run_state", "State test", ToolMode.BOTH);
        assertNotNull(state);

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        Thread.sleep(100);

        var retrievedOpt = agentService.getRunState("run_state");
        assertTrue(retrievedOpt.isPresent());
        AgentRunState retrieved = retrievedOpt.get();
        assertEquals(AgentRunState.Status.COMPLETED, retrieved.getStatus());
        assertNotNull(retrieved.getResult());
        assertTrue(retrieved.getResult().isSuccess());
        assertEquals("Done state", retrieved.getResult().getResponse());
    }
}
