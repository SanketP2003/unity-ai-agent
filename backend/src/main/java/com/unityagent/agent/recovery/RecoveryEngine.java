package com.unityagent.agent.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Autonomous Recovery Engine implementing a multi-stage escalation ladder.
 *
 * <p>Escalation Ladder:
 * <pre>
 *   FAIL -> RETRY (with parameter correction)
 *        -> INSPECT (read state/logs/code)
 *        -> REPAIR (surgical code/component fix)
 *        -> RETEST (compile / run test)
 *        -> REPLAN (dynamic sub-DAG insertion)
 *        -> ASK USER (when limits exceeded)
 * </pre>
 */
@Service
public class RecoveryEngine {

    private static final Logger log = LoggerFactory.getLogger(RecoveryEngine.class);
    private static final int DEFAULT_MAX_RECOVERY_CYCLES = 10;

    private final int maxRecoveryCycles;
    private final AtomicInteger currentCycles = new AtomicInteger(0);

    public RecoveryEngine() {
        this(DEFAULT_MAX_RECOVERY_CYCLES);
    }

    public RecoveryEngine(int maxRecoveryCycles) {
        this.maxRecoveryCycles = maxRecoveryCycles;
    }

    public int getMaxRecoveryCycles() { return maxRecoveryCycles; }
    public int getCurrentCycles() { return currentCycles.get(); }
    public void resetCycles() { currentCycles.set(0); }

    /**
     * Determines the optimal recovery strategy based on diagnostic failure context and past attempts.
     */
    public RecoveryStrategy determineStrategy(FailureContext failure) {
        if (failure == null) {
            return RecoveryStrategy.escalateToHuman("Null failure context encountered");
        }

        int cycle = currentCycles.incrementAndGet();
        if (cycle > maxRecoveryCycles) {
            log.warn("Autonomy limit exceeded: max recovery cycles ({}) reached", maxRecoveryCycles);
            return RecoveryStrategy.escalateToHuman("Autonomy limit exceeded: maximum recovery cycles (" + maxRecoveryCycles + ") reached");
        }

        int attempts = failure.getAttemptCount();
        FailureType type = failure.getFailureType();

        log.info("Evaluating recovery for failure {} (type={}, attempts={}, totalCycles={})",
                failure.getNodeId(), type, attempts, cycle);

        switch (type) {
            case COMPILATION_SYNTAX:
                if (attempts <= 2) {
                    return RecoveryStrategy.inspectAndRepair(
                            "Fix C# syntax error in " + failure.getTargetFileOrAsset(),
                            "update_script",
                            Map.of("scriptName", failure.getTargetFileOrAsset() != null ? failure.getTargetFileOrAsset() : "",
                                   "errorCode", failure.getErrorCode() != null ? failure.getErrorCode() : "")
                    );
                }
                return RecoveryStrategy.replanSubgraph("Syntax errors persisted after " + attempts + " attempts in " + failure.getTargetFileOrAsset());

            case COMPILATION_TYPE:
                if (attempts == 1) {
                    String missingSymbol = (String) failure.getDiagnosticDetails().getOrDefault("missingSymbol", "Component");
                    return RecoveryStrategy.injectMissing(
                            "Create missing type definition for " + missingSymbol,
                            "create_script",
                            Map.of("scriptName", missingSymbol + ".cs")
                    );
                }
                return RecoveryStrategy.replanSubgraph("Missing type could not be resolved automatically");

            case COMPILATION_MEMBER:
            case COMPILATION_CONVERSION:
                if (attempts <= 2) {
                    return RecoveryStrategy.inspectAndRepair(
                            "Fix member access or type conversion in " + failure.getTargetFileOrAsset(),
                            "update_script",
                            Map.of("scriptName", failure.getTargetFileOrAsset() != null ? failure.getTargetFileOrAsset() : "")
                    );
                }
                return RecoveryStrategy.replanSubgraph("Compiler member/conversion errors persisted");

            case MISSING_COMPONENT:
                return RecoveryStrategy.retry(
                        "Attach missing component to target GameObject",
                        "add_component",
                        Map.of("gameObjectName", failure.getTargetEntity() != null ? failure.getTargetEntity() : "")
                );

            case MISSING_DEPENDENCY:
                if (attempts == 1) {
                    return RecoveryStrategy.injectMissing(
                            "Inject missing dependency " + failure.getTargetFileOrAsset(),
                            "create_script",
                            Map.of("targetAsset", failure.getTargetFileOrAsset() != null ? failure.getTargetFileOrAsset() : "")
                    );
                }
                return RecoveryStrategy.replanSubgraph("Missing asset/dependency could not be located");

            case SCENE_DRIFT:
                return RecoveryStrategy.inspectAndRepair(
                        "Re-inspect scene hierarchy to locate or recreate " + failure.getTargetEntity(),
                        "find_game_objects",
                        Map.of("searchPattern", failure.getTargetEntity() != null ? failure.getTargetEntity() : "")
                );

            case RUNTIME_NULL_REF:
                if (attempts <= 2) {
                    return RecoveryStrategy.inspectAndRepair(
                            "Add null guards and verify references in " + failure.getTargetFileOrAsset(),
                            "update_script",
                            Map.of("scriptName", failure.getTargetFileOrAsset() != null ? failure.getTargetFileOrAsset() : "")
                    );
                }
                return RecoveryStrategy.replanSubgraph("NullReferenceException persisted in runtime");

            case BEHAVIOR_TIMEOUT:
            case BEHAVIOR_PHYSICS:
                if (attempts == 1) {
                    return RecoveryStrategy.retry(
                            "Verify Rigidbody/Collider physics settings and input mappings",
                            "get_component_properties",
                            Map.of("gameObjectName", failure.getTargetEntity() != null ? failure.getTargetEntity() : "")
                    );
                }
                return RecoveryStrategy.replanSubgraph("Behavioral test failed after physics adjustments");

            case VALIDATION_FAILED:
                return RecoveryStrategy.replanSubgraph("Objective validation failed: adjusting plan sub-graph");

            case TOOL_EXECUTION_ERROR:
            case UNKNOWN:
            default:
                if (attempts == 1) {
                    return RecoveryStrategy.retry(
                            "Retry tool execution with validated parameters",
                            failure.getToolName(),
                            Map.of()
                    );
                }
                return RecoveryStrategy.replanSubgraph("Unresolved tool failure: " + failure.getRawErrorMessage());
        }
    }
}
