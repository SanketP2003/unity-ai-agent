package com.unityagent.agent.concurrency;

import com.unityagent.agent.budget.BudgetExceededException;
import com.unityagent.agent.budget.ResourceBudget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectIsolationConcurrencyTest {

    private ProjectLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new ProjectLockService();
    }

    @Test
    void testSingleActiveRunPerProjectEnforced() {
        String projA = "project-alpha";
        String run1 = "run-101";
        String run2 = "run-102";

        // Run 1 acquires lock on Project A
        lockService.acquireProjectLock(projA, run1);
        assertTrue(lockService.isProjectLocked(projA));
        assertEquals(run1, lockService.getActiveRunForProject(projA));

        // Run 2 attempts to acquire lock on same Project A -> rejected with ProjectConflictException
        ProjectConflictException ex = assertThrows(ProjectConflictException.class, () ->
                lockService.acquireProjectLock(projA, run2));
        assertEquals(projA, ex.getProjectId());
        assertEquals(run1, ex.getActiveRunId());
    }

    @Test
    void testMultiProjectIsolationAllowsParallelRuns() {
        String projA = "project-alpha";
        String projB = "project-beta";
        String runA = "run-alpha-1";
        String runB = "run-beta-1";

        // Parallel runs in separate projects are allowed
        assertDoesNotThrow(() -> lockService.acquireProjectLock(projA, runA));
        assertDoesNotThrow(() -> lockService.acquireProjectLock(projB, runB));

        assertTrue(lockService.isProjectLocked(projA));
        assertTrue(lockService.isProjectLocked(projB));
        assertEquals(runA, lockService.getActiveRunForProject(projA));
        assertEquals(runB, lockService.getActiveRunForProject(projB));

        // Release Project A
        lockService.releaseProjectLock(projA, runA);
        assertFalse(lockService.isProjectLocked(projA));
        assertTrue(lockService.isProjectLocked(projB)); // Project B remains locked
    }

    @Test
    void testResourceBudgetGuardrails() {
        ResourceBudget budget = new ResourceBudget(1800, 50, 20, 5, 5, 2, 16000);

        // Within limits -> no exception
        assertDoesNotThrow(() -> budget.validateBudget("run_1", 100, 10, 5, 1, 1));

        // Tool limit exceeded
        BudgetExceededException toolEx = assertThrows(BudgetExceededException.class, () ->
                budget.validateBudget("run_1", 100, 55, 5, 1, 1));
        assertEquals(BudgetExceededException.ExhaustionReason.TOOL_LIMIT_REACHED, toolEx.getReason());

        // Timeout exceeded
        BudgetExceededException timeEx = assertThrows(BudgetExceededException.class, () ->
                budget.validateBudget("run_1", 1900, 10, 5, 1, 1));
        assertEquals(BudgetExceededException.ExhaustionReason.TIMEOUT, timeEx.getReason());

        // Concurrent runs limit
        BudgetExceededException concEx = assertThrows(BudgetExceededException.class, () ->
                budget.validateConcurrentRuns(2));
        assertEquals(BudgetExceededException.ExhaustionReason.CONCURRENT_RUN_LIMIT_REACHED, concEx.getReason());
    }
}
