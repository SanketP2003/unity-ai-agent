package com.unityagent.studio.service;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tracking, risk assessment, and human approval workflows for project changes.
 * Guarantees that LLMs cannot self-approve HIGH-risk change sets.
 */
@Service
public class ChangeReviewService {

    private static final Logger log = LoggerFactory.getLogger(ChangeReviewService.class);

    private final MemoryDatabase memoryDb;
    private final Map<String, ChangeSet> inMemoryCache = new ConcurrentHashMap<>();

    public ChangeReviewService(MemoryDatabase memoryDb) {
        this.memoryDb = memoryDb;
    }

    /**
     * Evaluates the risk level of an individual change entry.
     */
    public RiskLevel evaluateRisk(ChangeType type, String targetPath) {
        if (type == ChangeType.FILE_DELETE || type == ChangeType.SCENE_OBJECT_DELETE) {
            return RiskLevel.HIGH;
        }

        if (targetPath != null) {
            String lower = targetPath.toLowerCase().replace('\\', '/');
            if (lower.endsWith(".unity") ||
                lower.contains("projectsettings/") ||
                lower.contains("packages/manifest.json") ||
                lower.endsWith(".dll") ||
                lower.endsWith(".so") ||
                lower.endsWith(".bundle")) {
                return RiskLevel.HIGH;
            }
        }

        if (type == ChangeType.FILE_MODIFY || type == ChangeType.COMPONENT_MODIFY) {
            return RiskLevel.MEDIUM;
        }

        return RiskLevel.LOW;
    }

    /**
     * Creates a new ChangeSet.
     */
    public synchronized ChangeSet createChangeSet(String projectId, String agentRunId, String planNodeId, String summary) {
        String id = "cs_" + UUID.randomUUID().toString().substring(0, 8);
        String now = Instant.now().toString();

        ChangeSet changeSet = new ChangeSet(id, projectId, agentRunId, planNodeId, summary, ApprovalState.PENDING, now);
        inMemoryCache.put(id, changeSet);

        // Persist to DB
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO change_sets (change_set_id, project_id, agent_run_id, plan_node_id, summary, status, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?)
             """)) {
            stmt.setString(1, id);
            stmt.setString(2, projectId);
            stmt.setString(3, agentRunId);
            stmt.setString(4, planNodeId);
            stmt.setString(5, summary);
            stmt.setString(6, ApprovalState.PENDING.name());
            stmt.setString(7, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert change_set {}: {}", id, e.getMessage());
        }

        return changeSet;
    }

    /**
     * Adds an entry to an existing ChangeSet.
     */
    public synchronized ChangeEntry addEntry(String changeSetId, ChangeType type, String targetPath,
                                            String beforeHash, String afterHash, String diffContent, String rationale) {
        ChangeSet cs = getChangeSet(changeSetId);
        if (cs == null) {
            throw new IllegalArgumentException("ChangeSet not found: " + changeSetId);
        }

        String entryId = "ce_" + UUID.randomUUID().toString().substring(0, 8);
        RiskLevel risk = evaluateRisk(type, targetPath);
        ChangeEntry entry = new ChangeEntry(entryId, changeSetId, type, targetPath, beforeHash, afterHash,
                diffContent, risk, ApprovalState.PENDING, rationale);

        cs.addEntry(entry);

        // Persist entry to DB
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO change_entries (entry_id, change_set_id, change_type, target_path, before_hash, after_hash,
                                             diff_content, risk_level, approval_state, rationale)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
            stmt.setString(1, entryId);
            stmt.setString(2, changeSetId);
            stmt.setString(3, type.name());
            stmt.setString(4, targetPath != null ? targetPath : "");
            stmt.setString(5, beforeHash);
            stmt.setString(6, afterHash);
            stmt.setString(7, diffContent);
            stmt.setString(8, risk.name());
            stmt.setString(9, ApprovalState.PENDING.name());
            stmt.setString(10, rationale);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert change_entry {}: {}", entryId, e.getMessage());
        }

        return entry;
    }

    /**
     * Approves a ChangeSet.
     * Strictly enforces that HIGH-risk changes can ONLY be approved by human roles, never by LLM or AGENT.
     */
    public synchronized ChangeSet approveChangeSet(String changeSetId, String reviewerRole, String reviewerId) {
        ChangeSet cs = getChangeSet(changeSetId);
        if (cs == null) {
            throw new IllegalArgumentException("ChangeSet not found: " + changeSetId);
        }

        if (cs.getRiskLevel() == RiskLevel.HIGH) {
            if (reviewerRole == null ||
                reviewerRole.toUpperCase().contains("AGENT") ||
                reviewerRole.toUpperCase().contains("LLM") ||
                reviewerRole.toUpperCase().contains("SYSTEM")) {
                throw new SecurityException("Automated agent or LLM cannot self-approve HIGH-risk change sets. Human approval is required.");
            }
        }

        String now = Instant.now().toString();
        cs.setStatus(ApprovalState.APPROVED);
        cs.setReviewedAt(now);
        cs.setReviewedBy(reviewerId != null ? reviewerId : "user");

        for (ChangeEntry e : cs.getEntries()) {
            e.setApprovalState(ApprovalState.APPROVED);
        }

        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 UPDATE change_sets SET status = ?, reviewed_at = ?, reviewed_by = ? WHERE change_set_id = ?
             """)) {
            stmt.setString(1, ApprovalState.APPROVED.name());
            stmt.setString(2, now);
            stmt.setString(3, cs.getReviewedBy());
            stmt.setString(4, changeSetId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update change_set approval: {}", e.getMessage());
        }

        log.info("ChangeSet {} APPROVED by {} (role: {})", changeSetId, reviewerId, reviewerRole);
        return cs;
    }

    /**
     * Rejects a ChangeSet.
     */
    public synchronized ChangeSet rejectChangeSet(String changeSetId, String reviewerId, String reason) {
        ChangeSet cs = getChangeSet(changeSetId);
        if (cs == null) {
            throw new IllegalArgumentException("ChangeSet not found: " + changeSetId);
        }

        String now = Instant.now().toString();
        cs.setStatus(ApprovalState.REJECTED);
        cs.setReviewedAt(now);
        cs.setReviewedBy(reviewerId != null ? reviewerId : "user");
        cs.setRejectionReason(reason);

        for (ChangeEntry e : cs.getEntries()) {
            e.setApprovalState(ApprovalState.REJECTED);
        }

        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 UPDATE change_sets SET status = ?, reviewed_at = ?, reviewed_by = ? WHERE change_set_id = ?
             """)) {
            stmt.setString(1, ApprovalState.REJECTED.name());
            stmt.setString(2, now);
            stmt.setString(3, cs.getReviewedBy());
            stmt.setString(4, changeSetId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update change_set rejection: {}", e.getMessage());
        }

        log.info("ChangeSet {} REJECTED by {}: {}", changeSetId, reviewerId, reason);
        return cs;
    }

    public ChangeSet getChangeSet(String changeSetId) {
        if (changeSetId == null) return null;
        ChangeSet cached = inMemoryCache.get(changeSetId);
        if (cached != null) return cached;

        // Try load from DB
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT change_set_id, project_id, agent_run_id, plan_node_id, summary, status, created_at, reviewed_at, reviewed_by
                 FROM change_sets WHERE change_set_id = ?
             """)) {
            stmt.setString(1, changeSetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ChangeSet cs = new ChangeSet(
                        rs.getString("change_set_id"),
                        rs.getString("project_id"),
                        rs.getString("agent_run_id"),
                        rs.getString("plan_node_id"),
                        rs.getString("summary"),
                        ApprovalState.valueOf(rs.getString("status")),
                        rs.getString("created_at")
                    );
                    cs.setReviewedAt(rs.getString("reviewed_at"));
                    cs.setReviewedBy(rs.getString("reviewed_by"));
                    loadEntriesForChangeSet(cs, conn);
                    inMemoryCache.put(changeSetId, cs);
                    return cs;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load change_set {}: {}", changeSetId, e.getMessage());
        }
        return null;
    }

    public List<ChangeSet> getChangeSetsForProject(String projectId) {
        List<ChangeSet> list = new ArrayList<>();
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT change_set_id FROM change_sets WHERE project_id = ? ORDER BY created_at DESC
             """)) {
            stmt.setString(1, projectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChangeSet cs = getChangeSet(rs.getString("change_set_id"));
                    if (cs != null) list.add(cs);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get change sets for project {}: {}", projectId, e.getMessage());
        }
        return list;
    }

    public List<ChangeSet> getChangeSetsForRun(String runId) {
        List<ChangeSet> list = new ArrayList<>();
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT change_set_id FROM change_sets WHERE agent_run_id = ? ORDER BY created_at ASC
             """)) {
            stmt.setString(1, runId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChangeSet cs = getChangeSet(rs.getString("change_set_id"));
                    if (cs != null) list.add(cs);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get change sets for run {}: {}", runId, e.getMessage());
        }
        return list;
    }

    public List<ChangeSet> getPendingChangeSets(String projectId) {
        List<ChangeSet> list = new ArrayList<>();
        for (ChangeSet cs : getChangeSetsForProject(projectId)) {
            if (cs.getStatus() == ApprovalState.PENDING) {
                list.add(cs);
            }
        }
        return list;
    }

    private void loadEntriesForChangeSet(ChangeSet cs, Connection conn) {
        try (PreparedStatement stmt = conn.prepareStatement("""
                 SELECT entry_id, change_type, target_path, before_hash, after_hash, diff_content, risk_level, approval_state, rationale
                 FROM change_entries WHERE change_set_id = ?
             """)) {
            stmt.setString(1, cs.getChangeSetId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChangeEntry entry = new ChangeEntry(
                        rs.getString("entry_id"),
                        cs.getChangeSetId(),
                        ChangeType.valueOf(rs.getString("change_type")),
                        rs.getString("target_path"),
                        rs.getString("before_hash"),
                        rs.getString("after_hash"),
                        rs.getString("diff_content"),
                        RiskLevel.valueOf(rs.getString("risk_level")),
                        ApprovalState.valueOf(rs.getString("approval_state")),
                        rs.getString("rationale")
                    );
                    cs.addEntry(entry);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load entries for change_set {}: {}", cs.getChangeSetId(), e.getMessage());
        }
    }
}
