package com.unityagent.agent.events;

import com.unityagent.memory.MemoryDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service managing the durable append-only event journal.
 * Tracks monotonic lifecycle events per autonomous run for UI replay, crash diagnostics,
 * and recovery.
 */
@Service
public class EventJournalService {

    private static final Logger log = LoggerFactory.getLogger(EventJournalService.class);

    private final MemoryDatabase db;
    private final Map<String, AtomicLong> runSequenceCounters = new ConcurrentHashMap<>();

    @Autowired
    public EventJournalService(MemoryDatabase db) {
        this.db = db;
    }

    /**
     * Appends an event record to the journal with an automatically incremented sequence.
     */
    public synchronized RunEventRecord recordEvent(String projectId, String sessionId, String agentRunId,
                                                   RunEventType eventType, String payload) {
        return recordEvent(projectId, sessionId, agentRunId, eventType.name(), payload);
    }

    /**
     * Appends an event record with a custom event type.
     */
    public synchronized RunEventRecord recordEvent(String projectId, String sessionId, String agentRunId,
                                                   String eventType, String payload) {
        String pId = projectId != null ? projectId : "default";
        String sId = sessionId != null ? sessionId : "";
        String rId = agentRunId != null ? agentRunId : "unknown";

        long seq = getNextSequence(rId);
        String eventId = "evt_" + UUID.randomUUID().toString().substring(0, 12);
        Instant now = Instant.now();
        String sanitizedPayload = RunEventRecord.sanitizePayload(payload);

        RunEventRecord record = new RunEventRecord(eventId, seq, now, pId, sId, rId, eventType, sanitizedPayload);

        String ensureProjectSql = "INSERT OR IGNORE INTO projects (project_id, created_at) VALUES (?, datetime('now'))";
        String insertSql = """
            INSERT INTO run_events (event_id, sequence, timestamp, project_id, session_id, agent_run_id, event_type, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = db.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psProj = conn.prepareStatement(ensureProjectSql)) {
                    psProj.setString(1, pId);
                    psProj.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, eventId);
                    ps.setLong(2, seq);
                    ps.setString(3, now.toString());
                    ps.setString(4, pId);
                    ps.setString(5, sId);
                    ps.setString(6, rId);
                    ps.setString(7, eventType);
                    ps.setString(8, sanitizedPayload);
                    ps.executeUpdate();
                }

                conn.commit();
                log.debug("Journaled event {} for run {}: {} (seq={})", eventId, rId, eventType, seq);
            } catch (Exception e) {
                conn.rollback();
                log.error("Failed to journal event for run {}: {}", rId, e.getMessage(), e);
                throw new RuntimeException("Error persisting journal event", e);
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            log.error("Database connection failure recording event for run {}: {}", rId, e.getMessage());
            throw new RuntimeException("Database error recording journal event", e);
        }

        return record;
    }

    /**
     * Retrieves all events recorded for a given agent run in sequence order.
     */
    public List<RunEventRecord> getEventsForRun(String agentRunId) {
        return getEventsSince(agentRunId, 0);
    }

    /**
     * Retrieves events recorded for an agent run with sequence > sinceSequence.
     */
    public List<RunEventRecord> getEventsSince(String agentRunId, long sinceSequence) {
        List<RunEventRecord> list = new ArrayList<>();
        String sql = """
            SELECT * FROM run_events
            WHERE agent_run_id = ? AND sequence > ?
            ORDER BY sequence ASC
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agentRunId);
            ps.setLong(2, sinceSequence);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query events for run {}: {}", agentRunId, e.getMessage());
            throw new RuntimeException("Error reading event journal for run " + agentRunId, e);
        }
        return list;
    }

    /**
     * Retrieves the latest N events for a project across runs.
     */
    public List<RunEventRecord> getEventsByProject(String projectId, int limit) {
        List<RunEventRecord> list = new ArrayList<>();
        String sql = """
            SELECT * FROM run_events
            WHERE project_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId != null ? projectId : "default");
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query events for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Error reading events for project " + projectId, e);
        }
        return list;
    }

    private long getNextSequence(String agentRunId) {
        AtomicLong counter = runSequenceCounters.computeIfAbsent(agentRunId, id -> {
            long maxSeq = queryMaxSequence(id);
            return new AtomicLong(maxSeq);
        });
        return counter.incrementAndGet();
    }

    private long queryMaxSequence(String agentRunId) {
        String sql = "SELECT COALESCE(MAX(sequence), 0) FROM run_events WHERE agent_run_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agentRunId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to query max sequence for run {}: {}", agentRunId, e.getMessage());
        }
        return 0;
    }

    private RunEventRecord mapRow(ResultSet rs) throws SQLException {
        RunEventRecord r = new RunEventRecord();
        r.setEventId(rs.getString("event_id"));
        r.setSequence(rs.getLong("sequence"));
        String ts = rs.getString("timestamp");
        if (ts != null) r.setTimestamp(Instant.parse(ts));
        r.setProjectId(rs.getString("project_id"));
        r.setSessionId(rs.getString("session_id"));
        r.setAgentRunId(rs.getString("agent_run_id"));
        r.setEventType(rs.getString("event_type"));
        r.setPayload(rs.getString("payload"));
        return r;
    }
}
