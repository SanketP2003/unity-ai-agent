package com.unityagent.agent.reliability;

import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.checkpoint.AutonomyCheckpoint;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.events.EventJournalService;
import com.unityagent.agent.events.RunEventType;
import com.unityagent.agent.persistence.AutonomousRunRecord;
import com.unityagent.agent.persistence.RunPersistenceService;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.unity.UnityConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing crash recovery of autonomous runs across backend and Unity restarts.
 * Adheres strictly to Invariant 6: Resumption reconciles against live engine reality
 * and never blindly replays previous tool calls.
 */
@Service
public class AutonomousRunRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousRunRecoveryService.class);

    private final RunPersistenceService persistenceService;
    private final EventJournalService eventJournalService;
    private final CheckpointService checkpointService;
    private final AutonomousRunController runController;
    private final UnityConnection unityConnection;
    private final AIProvider aiProvider;

    @Autowired
    public AutonomousRunRecoveryService(RunPersistenceService persistenceService,
                                        EventJournalService eventJournalService,
                                        CheckpointService checkpointService,
                                        AutonomousRunController runController,
                                        @Autowired(required = false) UnityConnection unityConnection,
                                        @Autowired(required = false) AIProvider aiProvider) {
        this.persistenceService = persistenceService;
        this.eventJournalService = eventJournalService;
        this.checkpointService = checkpointService;
        this.runController = runController;
        this.unityConnection = unityConnection;
        this.aiProvider = aiProvider;
    }

    /**
     * Called on system boot or manual trigger to recover any interrupted runs.
     *
     * @return list of recovered run records with updated statuses
     */
    public List<AutonomousRunRecord> recoverOnStartup() {
        List<AutonomousRunRecord> unfinished = persistenceService.getUnfinishedRuns();
        log.info("Found {} unfinished autonomous run(s) requiring recovery check", unfinished.size());

        List<AutonomousRunRecord> processed = new ArrayList<>();
        for (AutonomousRunRecord record : unfinished) {
            try {
                processRunRecovery(record);
                processed.add(record);
            } catch (Exception e) {
                log.error("Error attempting recovery for run {}: {}", record.getRunId(), e.getMessage(), e);
            }
        }
        return processed;
    }

    private void processRunRecovery(AutonomousRunRecord record) {
        String runId = record.getRunId();
        String projectId = record.getProjectId();

        // 1. Check AI Provider availability
        if (aiProvider != null && !aiProvider.isConfigured()) {
            log.warn("Run {} paused: AI Provider is not configured (WAITING_FOR_PROVIDER)", runId);
            record.setStatus("WAITING_FOR_PROVIDER");
            persistenceService.saveRun(record);
            eventJournalService.recordEvent(projectId, record.getSessionId(), runId,
                    RunEventType.RUN_PAUSED, "Paused: AI Provider is unavailable");
            return;
        }

        // 2. Check Unity bridge connection for this project
        boolean unityReady = unityConnection != null && unityConnection.isReady();
        if (!unityReady) {
            log.info("Run {} paused: Unity Editor is not connected for project {} (WAITING_FOR_UNITY)", runId, projectId);
            record.setStatus("WAITING_FOR_UNITY");
            persistenceService.saveRun(record);
            eventJournalService.recordEvent(projectId, record.getSessionId(), runId,
                    RunEventType.RUN_PAUSED, "Paused: Waiting for Unity Editor connection");
            return;
        }

        // 3. Unity is connected: load latest checkpoint and reconcile against live state
        reconcileAndResumeRun(runId, List.of());
    }

    /**
     * Idempotently reconciles a run against live Unity scene state and resumes execution.
     *
     * @param runId             the run ID to reconcile
     * @param liveSceneObjects  verified scene objects from Unity inspection
     * @return updated AutonomousRunState, or null if run cannot be loaded
     */
    public AutonomousRunState reconcileAndResumeRun(String runId, List<String> liveSceneObjects) {
        Optional<AutonomousRunRecord> opt = persistenceService.getRun(runId);
        if (opt.isEmpty()) {
            log.warn("Cannot reconcile unknown run: {}", runId);
            return null;
        }

        AutonomousRunRecord record = opt.get();
        String projectId = record.getProjectId();

        AutonomyCheckpoint checkpoint = null;
        if (record.getCheckpointRef() != null) {
            checkpoint = checkpointService.loadCheckpoint(projectId, record.getCheckpointRef());
        }
        if (checkpoint == null) {
            checkpoint = checkpointService.getLatestCheckpoint(projectId);
        }

        if (checkpoint == null) {
            log.warn("No checkpoint found for run {} (project {}). Marking AWAITING_INTERVENTION.", runId, projectId);
            record.setStatus("AWAITING_INTERVENTION");
            persistenceService.saveRun(record);
            eventJournalService.recordEvent(projectId, record.getSessionId(), runId,
                    RunEventType.HUMAN_INTERVENTION_REQUIRED, "Cannot resume: No checkpoint available for state reconciliation");
            return null;
        }

        // Reconcile against live Unity reality (never blindly replay tools)
        AgentPlan reconciledPlan = checkpointService.reconcileAndResume(checkpoint, liveSceneObjects);
        log.info("Successfully reconciled plan for run {} against live Unity state (planStatus={})",
                runId, reconciledPlan.getStatus());

        AutonomousRunState runState = runController.resumeRun(runId, checkpoint.getCheckpointId(), liveSceneObjects);

        record.setStatus("RUNNING");
        record.setCurrentPlanRevision(reconciledPlan.getRevisions().size());
        persistenceService.saveRun(record);

        eventJournalService.recordEvent(projectId, record.getSessionId(), runId,
                RunEventType.RUN_RESUMED, "Resumed after live state reconciliation with " + liveSceneObjects.size() + " scene objects");

        return runState;
    }
}
