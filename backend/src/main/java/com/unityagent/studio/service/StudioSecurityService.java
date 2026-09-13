package com.unityagent.studio.service;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.model.AuditEvent;
import com.unityagent.studio.model.StudioPermission;
import com.unityagent.studio.model.StudioRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing RBAC permissions and immutable audit event logging.
 */
@Service
public class StudioSecurityService {

    private static final Logger log = LoggerFactory.getLogger(StudioSecurityService.class);

    private final MemoryDatabase memoryDb;
    private final Map<StudioRole, Set<StudioPermission>> rolePermissions = new EnumMap<>(StudioRole.class);

    public StudioSecurityService(MemoryDatabase memoryDb) {
        this.memoryDb = memoryDb;
        initRolePermissions();
    }

    private void initRolePermissions() {
        // OWNER: all permissions
        rolePermissions.put(StudioRole.OWNER, EnumSet.allOf(StudioPermission.class));

        // ADMIN: all permissions
        rolePermissions.put(StudioRole.ADMIN, EnumSet.allOf(StudioPermission.class));

        // DEVELOPER: can edit, run, build, rollback, approve low/med changes
        rolePermissions.put(StudioRole.DEVELOPER, EnumSet.of(
                StudioPermission.PROJECT_VIEW,
                StudioPermission.PROJECT_EDIT,
                StudioPermission.RUN_TRIGGER,
                StudioPermission.RUN_CANCEL,
                StudioPermission.CHANGE_APPROVE_MED_LOW,
                StudioPermission.BUILD_TRIGGER,
                StudioPermission.ROLLBACK_EXECUTE,
                StudioPermission.DIAGNOSTICS_VIEW
        ));

        // REVIEWER: can view, approve changes (high and low/med), view diagnostics/audit
        rolePermissions.put(StudioRole.REVIEWER, EnumSet.of(
                StudioPermission.PROJECT_VIEW,
                StudioPermission.CHANGE_APPROVE_HIGH,
                StudioPermission.CHANGE_APPROVE_MED_LOW,
                StudioPermission.DIAGNOSTICS_VIEW,
                StudioPermission.AUDIT_VIEW
        ));

        // VIEWER: strictly read-only
        rolePermissions.put(StudioRole.VIEWER, EnumSet.of(
                StudioPermission.PROJECT_VIEW,
                StudioPermission.DIAGNOSTICS_VIEW
        ));
    }

    public boolean hasPermission(StudioRole role, StudioPermission permission) {
        if (role == null || permission == null) return false;
        Set<StudioPermission> perms = rolePermissions.get(role);
        return perms != null && perms.contains(permission);
    }

    public void checkPermission(StudioRole role, StudioPermission permission) {
        if (!hasPermission(role, permission)) {
            throw new SecurityException("Role '" + role + "' lacks required permission: " + permission);
        }
    }

    /**
     * Records an immutable audit event in the database.
     */
    public synchronized AuditEvent logAuditEvent(String userId, String projectId, String runId,
                                                String action, String target, String result, String details) {
        String eventId = "audit_" + UUID.randomUUID().toString().substring(0, 8);
        String now = Instant.now().toString();

        AuditEvent event = new AuditEvent(eventId, now, userId != null ? userId : "anonymous",
                projectId, runId, action, target, result, details);

        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO audit_events (event_id, timestamp, user_id, project_id, run_id, action, target, result, details)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
            stmt.setString(1, eventId);
            stmt.setString(2, now);
            stmt.setString(3, event.getUserId());
            stmt.setString(4, projectId);
            stmt.setString(5, runId);
            stmt.setString(6, action);
            stmt.setString(7, target);
            stmt.setString(8, result);
            stmt.setString(9, details);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert audit event {}: {}", eventId, e.getMessage());
        }

        log.debug("Audit event recorded: action={}, user={}, result={}", action, userId, result);
        return event;
    }

    /**
     * Retrieves audit trail for a project or globally.
     */
    public List<AuditEvent> getAuditTrail(String projectId, int limit) {
        List<AuditEvent> list = new ArrayList<>();
        String sql = (projectId != null)
                ? "SELECT event_id, timestamp, user_id, project_id, run_id, action, target, result, details FROM audit_events WHERE project_id = ? ORDER BY timestamp DESC LIMIT ?"
                : "SELECT event_id, timestamp, user_id, project_id, run_id, action, target, result, details FROM audit_events ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = memoryDb.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (projectId != null) {
                stmt.setString(1, projectId);
                stmt.setInt(2, limit > 0 ? limit : 50);
            } else {
                stmt.setInt(1, limit > 0 ? limit : 50);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new AuditEvent(
                            rs.getString("event_id"),
                            rs.getString("timestamp"),
                            rs.getString("user_id"),
                            rs.getString("project_id"),
                            rs.getString("run_id"),
                            rs.getString("action"),
                            rs.getString("target"),
                            rs.getString("result"),
                            rs.getString("details")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query audit trail: {}", e.getMessage());
        }

        return list;
    }
}
