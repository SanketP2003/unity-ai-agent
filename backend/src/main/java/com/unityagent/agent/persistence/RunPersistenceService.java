package com.unityagent.agent.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unityagent.memory.MemoryDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service managing durable SQLite persistence of autonomous runs.
 * All mutations use explicit database transactions to prevent partial writes.
 */
@Service
public class RunPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(RunPersistenceService.class);

    private final MemoryDatabase db;
    private final ObjectMapper mapper;

    @Autowired
    public RunPersistenceService(MemoryDatabase db, @Autowired(required = false) ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper != null ? mapper.copy() : new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /**
     * Atomically saves or updates an autonomous run record.
     */
    public void saveRun(AutonomousRunRecord record) {
        if (record == null || record.getRunId() == null) {
            throw new IllegalArgumentException("Cannot save null run or run without ID");
        }

        String ensureProjectSql = "INSERT OR IGNORE INTO projects (project_id, created_at) VALUES (?, datetime('now'))";

        String upsertSql = """
            INSERT INTO autonomous_runs (
                run_id, session_id, project_id, goal_text, status, start_time, last_update,
                completed_at, current_plan_revision, active_node_id, completed_nodes_json,
                failed_nodes_json, recovery_count, replan_count, tool_call_count,
                requirement_states_json, completion_state, checkpoint_ref,
                final_validation_result_json, plan_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(run_id) DO UPDATE SET
                session_id = excluded.session_id,
                project_id = excluded.project_id,
                goal_text = excluded.goal_text,
                status = excluded.status,
                last_update = excluded.last_update,
                completed_at = excluded.completed_at,
                current_plan_revision = excluded.current_plan_revision,
                active_node_id = excluded.active_node_id,
                completed_nodes_json = excluded.completed_nodes_json,
                failed_nodes_json = excluded.failed_nodes_json,
                recovery_count = excluded.recovery_count,
                replan_count = excluded.replan_count,
                tool_call_count = excluded.tool_call_count,
                requirement_states_json = excluded.requirement_states_json,
                completion_state = excluded.completion_state,
                checkpoint_ref = excluded.checkpoint_ref,
                final_validation_result_json = excluded.final_validation_result_json,
                plan_json = excluded.plan_json
            """;

        try (Connection conn = db.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // 1. Ensure project exists
                String projId = record.getProjectId() != null ? record.getProjectId() : "default";
                try (PreparedStatement psProject = conn.prepareStatement(ensureProjectSql)) {
                    psProject.setString(1, projId);
                    psProject.executeUpdate();
                }

                // 2. Upsert run record
                try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                    ps.setString(1, record.getRunId());
                    ps.setString(2, record.getSessionId() != null ? record.getSessionId() : "");
                    ps.setString(3, projId);
                    ps.setString(4, record.getGoalText());
                    ps.setString(5, record.getStatus() != null ? record.getStatus() : "RUNNING");
                    ps.setString(6, record.getStartTime() != null ? record.getStartTime().toString() : Instant.now().toString());
                    ps.setString(7, record.getLastUpdate() != null ? record.getLastUpdate().toString() : Instant.now().toString());
                    ps.setString(8, record.getCompletedAt() != null ? record.getCompletedAt().toString() : null);
                    ps.setInt(9, record.getCurrentPlanRevision());
                    ps.setString(10, record.getActiveNodeId());
                    ps.setString(11, toJson(record.getCompletedNodes()));
                    ps.setString(12, toJson(record.getFailedNodes()));
                    ps.setInt(13, record.getRecoveryCount());
                    ps.setInt(14, record.getReplanCount());
                    ps.setInt(15, record.getToolCallCount());
                    ps.setString(16, toJson(record.getRequirementStates()));
                    ps.setString(17, record.getCompletionState());
                    ps.setString(18, record.getCheckpointRef());
                    ps.setString(19, record.getFinalValidationResult());
                    ps.setString(20, record.getPlanJson());
                    ps.executeUpdate();
                }

                conn.commit();
                log.debug("Persisted autonomous run {} (status={})", record.getRunId(), record.getStatus());
            } catch (Exception e) {
                conn.rollback();
                log.error("Failed to persist autonomous run {}: rolling back transaction", record.getRunId(), e);
                throw new RuntimeException("Database error saving autonomous run " + record.getRunId(), e);
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            log.error("SQL error while saving autonomous run {}: {}", record.getRunId(), e.getMessage());
            throw new RuntimeException("Database connection failure saving run", e);
        }
    }

    /**
     * Loads a single autonomous run by ID.
     */
    public Optional<AutonomousRunRecord> getRun(String runId) {
        if (runId == null) return Optional.empty();

        String sql = "SELECT * FROM autonomous_runs WHERE run_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load autonomous run {}: {}", runId, e.getMessage());
            throw new RuntimeException("Error loading run " + runId, e);
        }
        return Optional.empty();
    }

    /**
     * Retrieves all runs for a specific project.
     */
    public List<AutonomousRunRecord> getRunsByProject(String projectId) {
        List<AutonomousRunRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM autonomous_runs WHERE project_id = ? ORDER BY start_time DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId != null ? projectId : "default");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query runs for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Error querying runs for project " + projectId, e);
        }
        return list;
    }

    /**
     * Retrieves all unfinished runs (status in RUNNING, PAUSED, RECOVERING, WAITING_FOR_UNITY, WAITING_FOR_PROVIDER, AWAITING_INTERVENTION).
     */
    public List<AutonomousRunRecord> getUnfinishedRuns() {
        List<AutonomousRunRecord> list = new ArrayList<>();
        String sql = """
            SELECT * FROM autonomous_runs
            WHERE status IN ('RUNNING', 'PAUSED', 'RECOVERING', 'WAITING_FOR_UNITY', 'WAITING_FOR_PROVIDER', 'AWAITING_INTERVENTION')
            ORDER BY start_time ASC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to query unfinished runs: {}", e.getMessage());
            throw new RuntimeException("Error querying unfinished runs", e);
        }
        return list;
    }

    /**
     * Updates only status and last_update timestamp.
     */
    public void updateRunStatus(String runId, String status) {
        String sql = "UPDATE autonomous_runs SET status = ?, last_update = ? WHERE run_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, Instant.now().toString());
            ps.setString(3, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update status for run {}: {}", runId, e.getMessage());
            throw new RuntimeException("Error updating run status", e);
        }
    }

    private AutonomousRunRecord mapRow(ResultSet rs) throws SQLException {
        AutonomousRunRecord r = new AutonomousRunRecord();
        r.setRunId(rs.getString("run_id"));
        r.setSessionId(rs.getString("session_id"));
        r.setProjectId(rs.getString("project_id"));
        r.setGoalText(rs.getString("goal_text"));
        r.setStatus(rs.getString("status"));

        String st = rs.getString("start_time");
        if (st != null) r.setStartTime(Instant.parse(st));

        String lu = rs.getString("last_update");
        if (lu != null) r.setLastUpdate(Instant.parse(lu));

        String ca = rs.getString("completed_at");
        if (ca != null) r.setCompletedAt(Instant.parse(ca));

        r.setCurrentPlanRevision(rs.getInt("current_plan_revision"));
        r.setActiveNodeId(rs.getString("active_node_id"));
        r.setCompletedNodes(fromJsonList(rs.getString("completed_nodes_json")));
        r.setFailedNodes(fromJsonList(rs.getString("failed_nodes_json")));
        r.setRecoveryCount(rs.getInt("recovery_count"));
        r.setReplanCount(rs.getInt("replan_count"));
        r.setToolCallCount(rs.getInt("tool_call_count"));
        r.setRequirementStates(fromJsonMap(rs.getString("requirement_states_json")));
        r.setCompletionState(rs.getString("completion_state"));
        r.setCheckpointRef(rs.getString("checkpoint_ref"));
        r.setFinalValidationResult(rs.getString("final_validation_result_json"));
        r.setPlanJson(rs.getString("plan_json"));
        return r;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Map<String, String> fromJsonMap(String json) {
        if (json == null || json.isBlank()) return new java.util.HashMap<>();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }
}
