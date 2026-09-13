package com.unityagent.agent.reliability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolUnknownStateReconciliationTest {

    @Test
    void testUnknownOutcomeReconciledAsSucceededWhenEntityExists() {
        ToolExecutionTracker tracker = new ToolExecutionTracker();

        String opId = "op_ground_1";
        tracker.registerExecution("tc_1", opId, "proj_a", "run_1", "create_primitive",
                Map.of("primitiveType", "Cube", "name", "Ground"), 30);

        // Mark in flight as unknown due to disconnect
        List<ToolExecutionRecord> unknowns = tracker.markInFlightAsUnknown("proj_a", "Unity connection reset");
        assertEquals(0, unknowns.size()); // Was PENDING, not RUNNING

        ToolExecutionRecord record = tracker.getExecution(opId).orElseThrow();
        record.markRunning();
        unknowns = tracker.markInFlightAsUnknown("proj_a", "Unity connection reset");
        assertEquals(1, unknowns.size());
        assertEquals(ToolExecutionStatus.UNKNOWN, record.getResultStatus());

        // Reconcile after reconnect: Unity active scene inspection shows "Ground" was created!
        List<String> liveSceneObjects = List.of("Main Camera", "Directional Light", "Ground");
        ToolExecutionStatus reconciled = tracker.reconcileUnknownOperation(opId, liveSceneObjects);

        assertEquals(ToolExecutionStatus.SUCCEEDED, reconciled);
        assertEquals(ToolExecutionStatus.SUCCEEDED, record.getResultStatus());
        assertNotNull(record.getResultData());
    }

    @Test
    void testUnknownOutcomeReconciledAsFailedWhenEntityMissing() {
        ToolExecutionTracker tracker = new ToolExecutionTracker();

        String opId = "op_player_1";
        ToolExecutionRecord record = tracker.registerExecution("tc_2", opId, "proj_a", "run_1", "create_primitive",
                Map.of("primitiveType", "Capsule", "name", "Player"), 30);
        record.markRunning();

        tracker.markInFlightAsUnknown("proj_a", "Unity connection timeout");
        assertEquals(ToolExecutionStatus.UNKNOWN, record.getResultStatus());

        // Scene inspection shows only Camera and Light, Player was NOT created
        List<String> liveSceneObjects = List.of("Main Camera", "Directional Light");
        ToolExecutionStatus reconciled = tracker.reconcileUnknownOperation(opId, liveSceneObjects);

        assertEquals(ToolExecutionStatus.FAILED, reconciled);
        assertEquals(ToolExecutionStatus.FAILED, record.getResultStatus());
        assertTrue(record.getErrorMessage().contains("not created"));
    }
}
