package com.unityagent.agent;

import com.unityagent.agent.checkpoint.AutonomyCheckpoint;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalAnalyzer;
import com.unityagent.agent.goal.GoalRequirement;
import com.unityagent.agent.goal.RequirementManager;
import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.persistence.AutonomousRunRecord;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.plan.LongHorizonPlanner;
import com.unityagent.agent.plan.PlanNode;
import com.unityagent.agent.plan.ReplanningEngine;
import com.unityagent.agent.recovery.FailureClassifier;
import com.unityagent.agent.recovery.FailureContext;
import com.unityagent.agent.recovery.RecoveryEngine;
import com.unityagent.agent.recovery.RecoveryStrategy;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.agent.verification.VerificationEvidence;
import com.unityagent.tools.ToolMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Master controller for long-horizon autonomous development runs.
 *
 * <p><b>Architecture & Separation of Concerns:</b>
 * <ul>
 *   <li>The AutonomousRunController orchestrates goals, requirements, DAG plans, checkpoints,
 *       and recovery/replanning strategies.</li>
 *   <li><b>CRITICAL:</b> {@link AgentLoop} remains the SOLE execution engine for all LLM reasoning
 *       and tool calls. The controller does not execute tools directly; it submits granular sub-goals
 *       to AgentLoop.</li>
 *   <li>Completion is strictly enforced by {@link CompletionGate} and cannot be bypassed.</li>
 *   <li>Resumption reconciles against live engine state and never blindly replays previous actions.</li>
 * </ul>
 */
@Service
public class AutonomousRunController {

    private static final Logger log = LoggerFactory.getLogger(AutonomousRunController.class);

    private final GoalAnalyzer goalAnalyzer;
    private final RequirementManager requirementManager;
    private final LongHorizonPlanner planner;
    private final AgentLoop agentLoop;
    private final ReplanningEngine replanningEngine;
    private final FailureClassifier failureClassifier;
    private final RecoveryEngine recoveryEngine;
    private final ObjectiveValidator objectiveValidator;
    private final CompletionGate completionGate;
    private final CheckpointService checkpointService;
    private final AutonomyLimits limits;
    private final com.unityagent.agent.concurrency.ProjectLockService projectLockService;
    private final com.unityagent.agent.budget.ResourceBudget resourceBudget;
    private final com.unityagent.agent.persistence.RunPersistenceService persistenceService;
    private final com.unityagent.agent.events.EventJournalService eventJournalService;
    private final com.unityagent.agent.observability.MetricsService metricsService;

    private final Map<String, AutonomousRunState> activeRuns = new ConcurrentHashMap<>();
    private final Map<String, CancellationToken> runCancellationTokens = new ConcurrentHashMap<>();

    @Autowired
    public AutonomousRunController(GoalAnalyzer goalAnalyzer,
                                   LongHorizonPlanner planner,
                                   AgentLoop agentLoop,
                                   ReplanningEngine replanningEngine,
                                   FailureClassifier failureClassifier,
                                   RecoveryEngine recoveryEngine,
                                   ObjectiveValidator objectiveValidator,
                                   CompletionGate completionGate,
                                   CheckpointService checkpointService,
                                   @Autowired(required = false) AutonomyLimits limits,
                                   @Autowired(required = false) com.unityagent.agent.concurrency.ProjectLockService projectLockService,
                                   @Autowired(required = false) com.unityagent.agent.budget.ResourceBudget resourceBudget,
                                   @Autowired(required = false) com.unityagent.agent.persistence.RunPersistenceService persistenceService,
                                   @Autowired(required = false) com.unityagent.agent.events.EventJournalService eventJournalService,
                                   @Autowired(required = false) com.unityagent.agent.observability.MetricsService metricsService) {
        this(goalAnalyzer, new RequirementManager(), planner, agentLoop, replanningEngine,
                failureClassifier, recoveryEngine, objectiveValidator, completionGate,
                checkpointService, limits != null ? limits : AutonomyLimits.defaultLimits(),
                projectLockService, resourceBudget, persistenceService, eventJournalService, metricsService);
    }

    public AutonomousRunController(GoalAnalyzer goalAnalyzer,
                                   RequirementManager requirementManager,
                                   LongHorizonPlanner planner,
                                   AgentLoop agentLoop,
                                   ReplanningEngine replanningEngine,
                                   FailureClassifier failureClassifier,
                                   RecoveryEngine recoveryEngine,
                                   ObjectiveValidator objectiveValidator,
                                   CompletionGate completionGate,
                                   CheckpointService checkpointService,
                                   AutonomyLimits limits,
                                   com.unityagent.agent.concurrency.ProjectLockService projectLockService,
                                   com.unityagent.agent.budget.ResourceBudget resourceBudget,
                                   com.unityagent.agent.persistence.RunPersistenceService persistenceService,
                                   com.unityagent.agent.events.EventJournalService eventJournalService,
                                   com.unityagent.agent.observability.MetricsService metricsService) {
        this.goalAnalyzer = goalAnalyzer;
        this.requirementManager = requirementManager;
        this.planner = planner;
        this.agentLoop = agentLoop;
        this.replanningEngine = replanningEngine;
        this.failureClassifier = failureClassifier;
        this.recoveryEngine = recoveryEngine;
        this.objectiveValidator = objectiveValidator;
        this.completionGate = completionGate;
        this.checkpointService = checkpointService;
        this.limits = limits != null ? limits : AutonomyLimits.defaultLimits();
        this.projectLockService = projectLockService;
        this.resourceBudget = resourceBudget;
        this.persistenceService = persistenceService;
        this.eventJournalService = eventJournalService;
        this.metricsService = metricsService;
    }

    public AutonomousRunController(GoalAnalyzer goalAnalyzer,
                                   RequirementManager requirementManager,
                                   LongHorizonPlanner planner,
                                   AgentLoop agentLoop,
                                   ReplanningEngine replanningEngine,
                                   FailureClassifier failureClassifier,
                                   RecoveryEngine recoveryEngine,
                                   ObjectiveValidator objectiveValidator,
                                   CompletionGate completionGate,
                                   CheckpointService checkpointService,
                                   AutonomyLimits limits) {
        this(goalAnalyzer, requirementManager, planner, agentLoop, replanningEngine,
                failureClassifier, recoveryEngine, objectiveValidator, completionGate,
                checkpointService, limits, null, null, null, null, null);
    }

    public AutonomousRunController(GoalAnalyzer goalAnalyzer,
                                   RequirementManager requirementManager,
                                   LongHorizonPlanner planner,
                                   AgentLoop agentLoop,
                                   ReplanningEngine replanningEngine,
                                   FailureClassifier failureClassifier,
                                   RecoveryEngine recoveryEngine,
                                   ObjectiveValidator objectiveValidator,
                                   CompletionGate completionGate,
                                   CheckpointService checkpointService) {
        this(goalAnalyzer, requirementManager, planner, agentLoop, replanningEngine,
                failureClassifier, recoveryEngine, objectiveValidator, completionGate,
                checkpointService, AutonomyLimits.defaultLimits(), null, null, null, null, null);
    }

    /**
     * Initializes and starts a new long-horizon autonomous development run.
     */
    public synchronized AutonomousRunState startRun(String userPrompt, String projectId, String sessionId) {
        String runId = "autorun_" + UUID.randomUUID().toString().substring(0, 8);
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "session_" + runId;
        }

        log.info("Starting autonomous run {} for project {} with prompt: {}", runId, projectId, userPrompt);

        // 0. Concurrency & Isolation limits
        if (resourceBudget != null) {
            resourceBudget.validateConcurrentRuns(activeRuns.size());
        }
        if (projectLockService != null) {
            projectLockService.acquireProjectLock(projectId, runId);
            projectLockService.acquireSessionLock(sessionId, runId);
        }
        if (metricsService != null) {
            metricsService.recordRunStarted();
        }

        if (eventJournalService != null) {
            eventJournalService.recordEvent(projectId, sessionId, runId,
                    com.unityagent.agent.events.RunEventType.RUN_CREATED, "Created run with prompt: " + userPrompt);
            eventJournalService.recordEvent(projectId, sessionId, runId,
                    com.unityagent.agent.events.RunEventType.RUN_STARTED, "Started autonomous run");
        }

        // 1. Analyze prompt into structured GameGoal and machine-verifiable requirements
        GameGoal goal = goalAnalyzer.analyzeGoal(userPrompt, projectId);

        // 2. Synthesize multi-step DAG AgentPlan
        AgentPlan plan = planner.createPlan(goal);

        if (eventJournalService != null) {
            eventJournalService.recordEvent(projectId, sessionId, runId,
                    com.unityagent.agent.events.RunEventType.PLAN_CREATED, "Created plan with " + plan.getPlanNodes().size() + " nodes");
        }

        // 3. Initialize state and cancellation token
        AutonomousRunState state = new AutonomousRunState(runId, sessionId, projectId, goal, plan);
        state.setStatus(AutonomousRunState.RunStatus.RUNNING);

        CancellationToken cancellationToken = new CancellationToken();
        activeRuns.put(runId, state);
        runCancellationTokens.put(runId, cancellationToken);

        // Save initial checkpoint and persistent run record
        saveMilestoneCheckpoint(state, "Initial plan created");
        persistRunSnapshot(state, "Initial plan created");

        return state;
    }

    /**
     * Executes the next ready sub-goal in the DAG plan via AgentLoop.
     *
     * @param runId active run ID
     * @return true if more sub-goals remain to be executed; false if complete, paused, or failed
     */
    public synchronized boolean executeNextSubGoal(String runId) {
        AutonomousRunState state = activeRuns.get(runId);
        if (state == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }

        if (state.getStatus() != AutonomousRunState.RunStatus.RUNNING) {
            log.info("Run {} is not in RUNNING state (status={}), halting step execution", runId, state.getStatus());
            return false;
        }

        // Check production resource budgets
        if (resourceBudget != null) {
            resourceBudget.validateBudget(
                    runId,
                    state.getExecutionState().getElapsedSeconds(),
                    state.getExecutionState().getTotalToolCalls(),
                    state.getExecutionState().getTotalNodes(),
                    state.getPlan().getRevisions().size(),
                    recoveryEngine.getCurrentCycles()
            );
        }

        // Check autonomy limits
        String limitViolation = limits.checkLimits(
                state.getExecutionState(),
                state.getPlan().getRevisions().size(),
                recoveryEngine.getCurrentCycles()
        );
        if (limitViolation != null) {
            log.warn("Autonomy limit exceeded for run {}: {}", runId, limitViolation);
            HumanInterventionBoundary boundary = new HumanInterventionBoundary(
                    "int_" + UUID.randomUUID().toString().substring(0, 8),
                    runId,
                    HumanInterventionBoundary.InterventionReason.LIMIT_EXCEEDED,
                    limitViolation,
                    "Approve budget increase or finalize run"
            );
            state.setCurrentIntervention(boundary);
            state.setStatus(AutonomousRunState.RunStatus.AWAITING_INTERVENTION);
            if (eventJournalService != null) {
                eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), runId,
                        com.unityagent.agent.events.RunEventType.HUMAN_INTERVENTION_REQUIRED, "Limit exceeded: " + limitViolation);
            }
            persistRunSnapshot(state, "Limit exceeded: " + limitViolation);
            return false;
        }

        AgentPlan plan = state.getPlan();
        List<PlanNode> readyNodes = plan.getReadyPlanNodes();

        if (readyNodes.isEmpty()) {
            // Check if all nodes are completed
            if (plan.getStatus() == AgentPlan.PlanStatus.COMPLETED ||
                    plan.getPlanNodes().values().stream().allMatch(PlanNode::isCompleted)) {
                return finalizeRun(state);
            }

            log.info("No ready nodes found for run {} (status={})", runId, plan.getStatus());
            return false;
        }

        // Select the next ready node
        PlanNode targetNode = readyNodes.get(0);
        plan.markNodeInProgress(targetNode.getNodeId());

        String subGoalPrompt = String.format("Execute sub-goal: %s\nAction: %s\nCandidate Tools: %s",
                targetNode.getDescription(), targetNode.getActionType(), targetNode.getCandidateTools());

        CancellationToken cancellationToken = runCancellationTokens.get(runId);
        String subRunId = runId + "_" + targetNode.getNodeId();

        log.info("Submitting sub-goal [{}] to AgentLoop: {}", targetNode.getNodeId(), targetNode.getDescription());
        if (eventJournalService != null) {
            eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), runId,
                    com.unityagent.agent.events.RunEventType.NODE_STARTED, "Executing " + targetNode.getNodeId() + ": " + targetNode.getDescription());
        }
        persistRunSnapshot(state, "Started node " + targetNode.getNodeId());

        // AgentLoop is the SOLE LLM/tool execution engine
        AgentRunResult result = agentLoop.run(
                state.getSessionId(),
                subRunId,
                subGoalPrompt,
                ToolMode.BOTH,
                cancellationToken,
                null,
                state.getProjectId(),
                null
        );

        state.getExecutionState().recordToolCalls(result.getToolCallsExecuted());

        if (result.isSuccess()) {
            log.info("Sub-goal [{}] completed successfully", targetNode.getNodeId());
            plan.markNodeCompleted(targetNode.getNodeId(), result.getResponse());
            state.getExecutionState().recordNodeCompleted(targetNode.getNodeId());

            if (eventJournalService != null) {
                eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), runId,
                        com.unityagent.agent.events.RunEventType.NODE_COMPLETED, "Completed " + targetNode.getNodeId());
            }
            if (metricsService != null) {
                metricsService.recordToolExecution(true);
            }

            // Save milestone checkpoint periodically
            saveMilestoneCheckpoint(state, "Completed " + targetNode.getNodeId());
            persistRunSnapshot(state, "Completed " + targetNode.getNodeId());

            // Check if all nodes are now complete
            if (plan.getStatus() == AgentPlan.PlanStatus.COMPLETED ||
                    plan.getPlanNodes().values().stream().allMatch(PlanNode::isCompleted)) {
                return finalizeRun(state);
            }
            return true;
        } else {
            log.warn("Sub-goal [{}] failed: {}", targetNode.getNodeId(), result.getErrorMessage());
            plan.markNodeFailed(targetNode.getNodeId(), result.getErrorMessage());

            if (eventJournalService != null) {
                eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), runId,
                        com.unityagent.agent.events.RunEventType.NODE_FAILED, "Failed " + targetNode.getNodeId() + ": " + result.getErrorMessage());
            }
            if (metricsService != null) {
                metricsService.recordToolExecution(false);
            }
            persistRunSnapshot(state, "Failed " + targetNode.getNodeId());

            handleFailure(state, targetNode, result);
            return false;
        }
    }

    private void handleFailure(AutonomousRunState state, PlanNode failedNode, AgentRunResult result) {
        state.getExecutionState().recordRecoveryCycle();

        FailureContext ctx = failureClassifier.classify(
                failedNode.getNodeId(),
                failedNode.getCandidateTools().isEmpty() ? "unknown" : failedNode.getCandidateTools().get(0),
                result.getErrorMessage()
        );

        RecoveryStrategy strategy = recoveryEngine.determineStrategy(ctx);
        log.info("Determined recovery strategy: {}", strategy);

        if (eventJournalService != null) {
            eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), state.getRunId(),
                    com.unityagent.agent.events.RunEventType.RECOVERY_STARTED, "Strategy: " + strategy.getType() + " - " + strategy.getActionDescription());
        }
        if (metricsService != null) {
            metricsService.recordRecoveryCycle();
        }

        if (strategy.requiresHuman()) {
            HumanInterventionBoundary boundary = new HumanInterventionBoundary(
                    "int_" + UUID.randomUUID().toString().substring(0, 8),
                    state.getRunId(),
                    HumanInterventionBoundary.InterventionReason.UNRESOLVABLE_FAILURE,
                    strategy.getActionDescription(),
                    "Inspect failed step and manual intervention required"
            );
            state.setCurrentIntervention(boundary);
            state.setStatus(AutonomousRunState.RunStatus.AWAITING_INTERVENTION);
            if (eventJournalService != null) {
                eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), state.getRunId(),
                        com.unityagent.agent.events.RunEventType.HUMAN_INTERVENTION_REQUIRED, strategy.getActionDescription());
            }
            persistRunSnapshot(state, "Awaiting intervention: " + strategy.getActionDescription());
        } else if (strategy.getType() == RecoveryStrategy.StrategyType.REPLAN_SUBGRAPH) {
            state.getExecutionState().recordReplan();
            if (metricsService != null) {
                metricsService.recordReplan();
            }
            replanningEngine.replanOnFailure(
                    state.getPlan(),
                    failedNode.getNodeId(),
                    strategy.getActionDescription(),
                    ctx.getTargetFileOrAsset()
            );
            if (eventJournalService != null) {
                eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), state.getRunId(),
                        com.unityagent.agent.events.RunEventType.PLAN_REVISED, "Replanned sub-graph after failure on " + failedNode.getNodeId());
            }
            saveMilestoneCheckpoint(state, "Replanned on failure: " + failedNode.getNodeId());
            persistRunSnapshot(state, "Replanned " + failedNode.getNodeId());
        } else {
            // Reset node to PENDING to retry with adjusted parameters
            failedNode.setStatus(PlanNode.PlanNodeStatus.PENDING);
            failedNode.incrementAttempts();
            persistRunSnapshot(state, "Retrying " + failedNode.getNodeId());
        }
    }

    /**
     * Authoritative final completion gate verification.
     */
    private boolean finalizeRun(AutonomousRunState state) {
        log.info("All plan nodes completed for run {}. Running authoritative CompletionGate validation...", state.getRunId());

        if (eventJournalService != null) {
            eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), state.getRunId(),
                    com.unityagent.agent.events.RunEventType.VALIDATION_STARTED, "Beginning CompletionGate validation");
        }

        ValidationReport report = objectiveValidator.validateAll(state.getGoal(), state.getAccumulatedEvidence());
        state.setValidationReport(report);

        if (eventJournalService != null) {
            eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), state.getRunId(),
                    com.unityagent.agent.events.RunEventType.VALIDATION_COMPLETED, "Validation report: " + report.getSummary());
        }

        long durationMs = state.getExecutionState().getElapsedSeconds() * 1000L;
        boolean canComplete = completionGate.canComplete(report);
        if (canComplete) {
            state.setStatus(AutonomousRunState.RunStatus.COMPLETED);
            state.setCompletedAt(Instant.now());
            saveMilestoneCheckpoint(state, "Goal completed and verified by CompletionGate");
            persistRunSnapshot(state, "Completed");

            if (eventJournalService != null) {
                eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), state.getRunId(),
                        com.unityagent.agent.events.RunEventType.RUN_COMPLETED, "Run completed successfully");
            }
            if (metricsService != null) {
                metricsService.recordRunCompleted(true, durationMs);
            }
            if (projectLockService != null) {
                projectLockService.releaseProjectLock(state.getProjectId(), state.getRunId());
                projectLockService.releaseSessionLock(state.getSessionId(), state.getRunId());
            }
            log.info("Run {} SUCCESSFULLY COMPLETED. All acceptance criteria satisfied.", state.getRunId());
            return false;
        } else {
            log.warn("CompletionGate REJECTED completion for run {}: {}", state.getRunId(), report.getSummary());
            state.setStatus(AutonomousRunState.RunStatus.FAILED);
            persistRunSnapshot(state, "Failed CompletionGate");

            if (eventJournalService != null) {
                eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), state.getRunId(),
                        com.unityagent.agent.events.RunEventType.RUN_FAILED, "CompletionGate rejected: " + report.getSummary());
            }
            if (metricsService != null) {
                metricsService.recordValidationFailure();
                metricsService.recordRunCompleted(false, durationMs);
            }
            if (projectLockService != null) {
                projectLockService.releaseProjectLock(state.getProjectId(), state.getRunId());
                projectLockService.releaseSessionLock(state.getSessionId(), state.getRunId());
            }
            return false;
        }
    }

    private void saveMilestoneCheckpoint(AutonomousRunState state, String milestone) {
        try {
            AutonomyCheckpoint cp = checkpointService.saveCheckpoint(
                    state.getRunId(),
                    state.getProjectId(),
                    state.getGoal(),
                    state.getPlan(),
                    state.getExecutionState(),
                    milestone
            );
            state.setLastCheckpointId(cp.getCheckpointId());
        } catch (Exception e) {
            log.warn("Failed to persist milestone checkpoint: {}", e.getMessage());
        }
    }

    private void persistRunSnapshot(AutonomousRunState state, String milestone) {
        if (persistenceService == null || state == null) return;
        try {
            AutonomousRunRecord rec = new AutonomousRunRecord(
                    state.getRunId(), state.getSessionId(), state.getProjectId(),
                    state.getGoal() != null ? state.getGoal().getDescription() : "",
                    state.getStatus() != null ? state.getStatus().name() : "RUNNING"
            );
            rec.setCurrentPlanRevision(state.getPlan() != null ? state.getPlan().getRevisions().size() : 0);
            rec.setCompletedNodes(new ArrayList<>(state.getExecutionState().getCompletedNodes()));
            rec.setToolCallCount(state.getExecutionState().getTotalToolCalls());
            rec.setRecoveryCount(state.getExecutionState().getRecoveryCycles());
            rec.setReplanCount(state.getExecutionState().getReplans());
            rec.setCheckpointRef(state.getLastCheckpointId());
            if (state.getValidationReport() != null) {
                rec.setFinalValidationResult(state.getValidationReport().getSummary());
            }
            if (state.getCompletedAt() != null) {
                rec.setCompletedAt(state.getCompletedAt());
            }
            persistenceService.saveRun(rec);
        } catch (Exception e) {
            log.warn("Failed to persist run snapshot for {}: {}", state.getRunId(), e.getMessage());
        }
    }

    public synchronized AutonomousRunState pauseRun(String runId, String reason) {
        AutonomousRunState state = activeRuns.get(runId);
        if (state != null) {
            state.setStatus(AutonomousRunState.RunStatus.PAUSED);
            HumanInterventionBoundary boundary = new HumanInterventionBoundary(
                    "int_" + UUID.randomUUID().toString().substring(0, 8),
                    runId,
                    HumanInterventionBoundary.InterventionReason.MANUAL_PAUSE,
                    reason != null ? reason : "Paused by user",
                    "Resume when ready"
            );
            state.setCurrentIntervention(boundary);
            saveMilestoneCheckpoint(state, "Paused: " + reason);
            if (eventJournalService != null) {
                eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), runId,
                        com.unityagent.agent.events.RunEventType.RUN_PAUSED, "Run paused: " + reason);
            }
            persistRunSnapshot(state, "Paused");
        }
        return state;
    }

    public synchronized AutonomousRunState resumeRun(String runId, String checkpointId, List<String> currentSceneObjects) {
        AutonomousRunState state = activeRuns.get(runId);
        if (state == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }

        AutonomyCheckpoint checkpoint = checkpointService.loadCheckpoint(state.getProjectId(), checkpointId);
        if (checkpoint != null) {
            AgentPlan reconciledPlan = checkpointService.reconcileAndResume(checkpoint, currentSceneObjects);
            state.setPlan(reconciledPlan);
            state.setStatus(AutonomousRunState.RunStatus.RUNNING);
            state.setCurrentIntervention(null);
            log.info("Reconciled and resumed run {} from checkpoint {}", runId, checkpointId);
            if (eventJournalService != null) {
                eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), runId,
                        com.unityagent.agent.events.RunEventType.RUN_RESUMED, "Resumed from checkpoint " + checkpointId);
            }
            persistRunSnapshot(state, "Resumed");
        }
        return state;
    }

    public synchronized AutonomousRunState cancelRun(String runId) {
        AutonomousRunState state = activeRuns.get(runId);
        if (state != null) {
            state.setStatus(AutonomousRunState.RunStatus.CANCELLED);
            CancellationToken token = runCancellationTokens.get(runId);
            if (token != null) {
                token.cancel();
            }
            saveMilestoneCheckpoint(state, "Cancelled by user");
            if (projectLockService != null) {
                projectLockService.releaseProjectLock(state.getProjectId(), state.getRunId());
                projectLockService.releaseSessionLock(state.getSessionId(), state.getRunId());
            }
            if (eventJournalService != null) {
                eventJournalService.recordEvent(state.getProjectId(), state.getSessionId(), runId,
                        com.unityagent.agent.events.RunEventType.RUN_CANCELLED, "Run cancelled by user");
            }
            persistRunSnapshot(state, "Cancelled");
        }
        return state;
    }

    public synchronized AutonomousRunState approveIntervention(String runId, boolean approve, String notes) {
        AutonomousRunState state = activeRuns.get(runId);
        if (state != null && state.getCurrentIntervention() != null) {
            state.getCurrentIntervention().resolve(approve, notes);
            if (approve) {
                state.setStatus(AutonomousRunState.RunStatus.RUNNING);
            } else {
                state.setStatus(AutonomousRunState.RunStatus.CANCELLED);
            }
        }
        return state;
    }

    public AutonomousRunState getRunState(String runId) {
        return activeRuns.get(runId);
    }

    public Map<String, AutonomousRunState> getActiveRuns() {
        return Collections.unmodifiableMap(activeRuns);
    }

    public Optional<AutonomousRunState> getActiveRunForProject(String projectId) {
        if (projectId == null) return Optional.empty();
        return activeRuns.values().stream()
                .filter(r -> projectId.equals(r.getProjectId()))
                .findFirst();
    }
}
