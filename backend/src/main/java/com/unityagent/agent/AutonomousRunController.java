package com.unityagent.agent;

import com.unityagent.agent.checkpoint.AutonomyCheckpoint;
import com.unityagent.agent.checkpoint.CheckpointService;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.goal.GoalAnalyzer;
import com.unityagent.agent.goal.GoalRequirement;
import com.unityagent.agent.goal.RequirementManager;
import com.unityagent.agent.model.AgentRunResult;
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
                                   @Autowired(required = false) AutonomyLimits limits) {
        this(goalAnalyzer, new RequirementManager(), planner, agentLoop, replanningEngine,
                failureClassifier, recoveryEngine, objectiveValidator, completionGate,
                checkpointService, limits != null ? limits : AutonomyLimits.defaultLimits());
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
                checkpointService, AutonomyLimits.defaultLimits());
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

        // 1. Analyze prompt into structured GameGoal and machine-verifiable requirements
        GameGoal goal = goalAnalyzer.analyzeGoal(userPrompt, projectId);

        // 2. Synthesize multi-step DAG AgentPlan
        AgentPlan plan = planner.createPlan(goal);

        // 3. Initialize state and cancellation token
        AutonomousRunState state = new AutonomousRunState(runId, sessionId, projectId, goal, plan);
        state.setStatus(AutonomousRunState.RunStatus.RUNNING);

        CancellationToken cancellationToken = new CancellationToken();
        activeRuns.put(runId, state);
        runCancellationTokens.put(runId, cancellationToken);

        // Save initial checkpoint
        saveMilestoneCheckpoint(state, "Initial plan created");

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

            // Save milestone checkpoint periodically
            saveMilestoneCheckpoint(state, "Completed " + targetNode.getNodeId());

            // Check if all nodes are now complete
            if (plan.getStatus() == AgentPlan.PlanStatus.COMPLETED ||
                    plan.getPlanNodes().values().stream().allMatch(PlanNode::isCompleted)) {
                return finalizeRun(state);
            }
            return true;
        } else {
            log.warn("Sub-goal [{}] failed: {}", targetNode.getNodeId(), result.getErrorMessage());
            plan.markNodeFailed(targetNode.getNodeId(), result.getErrorMessage());

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
        } else if (strategy.getType() == RecoveryStrategy.StrategyType.REPLAN_SUBGRAPH) {
            state.getExecutionState().recordReplan();
            replanningEngine.replanOnFailure(
                    state.getPlan(),
                    failedNode.getNodeId(),
                    strategy.getActionDescription(),
                    ctx.getTargetFileOrAsset()
            );
            saveMilestoneCheckpoint(state, "Replanned on failure: " + failedNode.getNodeId());
        } else {
            // Reset node to PENDING to retry with adjusted parameters
            failedNode.setStatus(PlanNode.PlanNodeStatus.PENDING);
            failedNode.incrementAttempts();
        }
    }

    /**
     * Authoritative final completion gate verification.
     */
    private boolean finalizeRun(AutonomousRunState state) {
        log.info("All plan nodes completed for run {}. Running authoritative CompletionGate validation...", state.getRunId());

        ValidationReport report = objectiveValidator.validateAll(state.getGoal(), state.getAccumulatedEvidence());
        state.setValidationReport(report);

        boolean canComplete = completionGate.canComplete(report);
        if (canComplete) {
            state.setStatus(AutonomousRunState.RunStatus.COMPLETED);
            state.setCompletedAt(Instant.now());
            saveMilestoneCheckpoint(state, "Goal completed and verified by CompletionGate");
            log.info("Run {} SUCCESSFULLY COMPLETED. All acceptance criteria satisfied.", state.getRunId());
            return false;
        } else {
            log.warn("CompletionGate REJECTED completion for run {}: {}", state.getRunId(), report.getSummary());
            state.setStatus(AutonomousRunState.RunStatus.FAILED);
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
}
