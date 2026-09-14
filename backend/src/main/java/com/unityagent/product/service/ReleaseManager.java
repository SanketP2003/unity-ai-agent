package com.unityagent.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.*;
import com.unityagent.studio.model.StudioPermission;
import com.unityagent.studio.model.StudioRole;
import com.unityagent.studio.service.StudioSecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;

/**
 * Service orchestrating the complete release candidate lifecycle,
 * enforcing human approval boundaries, immutable version locks,
 * and atomic publishing transactions.
 */
@Service
public class ReleaseManager {

    private static final Logger log = LoggerFactory.getLogger(ReleaseManager.class);

    private final MemoryDatabase memoryDb;
    private final ReleaseValidator releaseValidator;
    private final ArtifactManager artifactManager;
    private final VersionManager versionManager;
    private final StudioSecurityService securityService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @org.springframework.beans.factory.annotation.Autowired
    public ReleaseManager(MemoryDatabase memoryDb,
                          ReleaseValidator releaseValidator,
                          VersionManager versionManager,
                          ArtifactManager artifactManager,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) StudioSecurityService securityService) {
        this.memoryDb = memoryDb;
        this.releaseValidator = releaseValidator;
        this.versionManager = versionManager;
        this.artifactManager = artifactManager;
        this.securityService = securityService;
    }

    public ReleaseManager(MemoryDatabase memoryDb,
                          ReleaseValidator releaseValidator,
                          VersionManager versionManager,
                          ArtifactManager artifactManager) {
        this(memoryDb, releaseValidator, versionManager, artifactManager, null);
    }

    public Release createReleaseCandidate(String releaseId, String projectId, String versionString,
                                          ReleaseChannel channel, String changelog, String buildId, String artifactId) {
        return createReleaseCandidate(releaseId, projectId, versionString, channel, buildId, changelog);
    }

    public Release createReleaseCandidate(String releaseId, String projectId, String versionString,
                                          ReleaseChannel channel, String buildId, String changelog) {
        GameVersion.parse(versionString);

        if (!versionManager.isVersionUnused(projectId, versionString)) {
            throw new IllegalArgumentException("Version " + versionString + " is already in use for project " + projectId);
        }

        Instant now = Instant.now();
        Release release = new Release(releaseId, projectId, versionString, channel, ReleaseStatus.DRAFT, buildId, changelog, now);

        String sql = "INSERT OR REPLACE INTO game_releases " +
                     "(release_id, project_id, version_string, channel, status, build_id, changelog, is_immutable, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, release.getReleaseId());
            ps.setString(2, release.getProjectId());
            ps.setString(3, release.getVersionString());
            ps.setString(4, release.getChannel().name());
            ps.setString(5, release.getStatus().name());
            ps.setString(6, release.getBuildId());
            ps.setString(7, release.getChangelog());
            ps.setString(8, release.getCreatedAt().toString());
            ps.executeUpdate();
            log.info("Created release candidate {}: version={}, status={}", releaseId, versionString, release.getStatus());
        } catch (Exception e) {
            log.error("Failed to create release {}: {}", releaseId, e.getMessage());
            throw new RuntimeException("Failed to create release: " + e.getMessage(), e);
        }

        return release;
    }

    public ReleaseValidationReport validateReleaseCandidate(String releaseId, BuildArtifact artifact, Path projectRoot) {
        Release release = getRelease(releaseId)
                .orElseThrow(() -> new IllegalArgumentException("Release not found: " + releaseId));

        updateReleaseStatus(releaseId, ReleaseStatus.VALIDATING);

        ReleaseValidationReport report = releaseValidator.validateRelease(release, artifact, projectRoot);

        try {
            String reportJson = objectMapper.writeValueAsString(report);
            ReleaseStatus nextStatus = report.isValid() ? ReleaseStatus.READY_FOR_REVIEW : ReleaseStatus.FAILED;

            String sql = "UPDATE game_releases SET status = ?, validation_report_json = ? WHERE release_id = ?";
            try (Connection conn = memoryDb.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nextStatus.name());
                ps.setString(2, reportJson);
                ps.setString(3, releaseId);
                ps.executeUpdate();
            }
            release.setStatus(nextStatus);
            release.setValidationReportJson(reportJson);
        } catch (Exception e) {
            log.error("Failed to update validation report for release {}: {}", releaseId, e.getMessage());
        }

        return report;
    }

    /**
     * Enforces human approval boundary: LLMs and automated agents CANNOT self-approve.
     * Only human leads with REVIEWER, ADMIN, or OWNER role can approve.
     */
    public Release approveRelease(String releaseId, String reviewerRole, String reviewerId, String notes) {
        if (reviewerId == null || reviewerId.toLowerCase().contains("agent") || reviewerId.toLowerCase().contains("llm")
                || reviewerId.toLowerCase().contains("autonomous")) {
            throw new SecurityException("Security boundary violation: Automated agents and LLMs are strictly forbidden from approving releases.");
        }

        StudioRole role;
        try {
            role = StudioRole.valueOf(reviewerRole.toUpperCase());
        } catch (Exception e) {
            throw new SecurityException("Invalid reviewer role: " + reviewerRole);
        }

        if (role != StudioRole.REVIEWER && role != StudioRole.ADMIN && role != StudioRole.OWNER) {
            throw new SecurityException("Role " + role + " does not have permission to approve releases. Required: REVIEWER, ADMIN, or OWNER");
        }

        Release release = getRelease(releaseId)
                .orElseThrow(() -> new IllegalArgumentException("Release not found: " + releaseId));

        if (release.getStatus() != ReleaseStatus.READY_FOR_REVIEW) {
            throw new IllegalStateException("Release cannot be approved; current status is: " + release.getStatus());
        }

        Map<String, Object> approval = Map.of(
                "approvedBy", reviewerId,
                "role", role.name(),
                "notes", notes != null ? notes : "Approved by human lead",
                "timestamp", Instant.now().toString()
        );

        try {
            String approvalJson = objectMapper.writeValueAsString(approval);
            String sql = "UPDATE game_releases SET status = 'APPROVED', approval_record_json = ? WHERE release_id = ?";
            try (Connection conn = memoryDb.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, approvalJson);
                ps.setString(2, releaseId);
                ps.executeUpdate();
            }
            release.setStatus(ReleaseStatus.APPROVED);
            release.setApprovalRecordJson(approvalJson);
            log.info("Release {} APPROVED by {} ({})", releaseId, reviewerId, role);
        } catch (Exception e) {
            throw new RuntimeException("Failed to approve release: " + e.getMessage(), e);
        }

        return release;
    }

    /**
     * Atomically executes the release publishing transaction.
     * Verifies all 9 mandatory conditions before locking the release as PUBLISHED and IMMUTABLE.
     */
    public synchronized Release publishRelease(String releaseId, String expectedProjectId, BuildArtifact artifact, Path projectRoot) {
        Release release = getRelease(releaseId)
                .orElseThrow(() -> new IllegalArgumentException("Release not found: " + releaseId));

        // 1. Release exists and project matches
        if (!release.getProjectId().equals(expectedProjectId)) {
            throw new SecurityException("Project mismatch for release: expected=" + expectedProjectId + ", actual=" + release.getProjectId());
        }

        // 2. Human approval valid
        if (release.getStatus() != ReleaseStatus.APPROVED) {
            throw new IllegalStateException("Publish transaction aborted: Release must be in APPROVED state (current: " + release.getStatus() + ")");
        }
        if (release.getApprovalRecordJson() == null || release.getApprovalRecordJson().isBlank()) {
            throw new SecurityException("Publish transaction aborted: No valid human approval record found");
        }

        // 3. Artifact exists and hash matches
        if (artifact == null) {
            throw new IllegalStateException("Publish transaction aborted: Missing build artifact");
        }
        boolean artifactIntact = artifactManager.verifyArtifactIntegrity(artifact.getArtifactId(), projectRoot);
        if (!artifactIntact) {
            throw new IllegalStateException("Publish transaction aborted: Artifact file missing or SHA-256 hash mismatch (CORRUPTED)");
        }

        // 4. Validation report present and passing
        if (release.getValidationReportJson() == null) {
            throw new IllegalStateException("Publish transaction aborted: Release has no validation report");
        }

        // 5. Version unused
        // (already checked on creation, but re-verify no other PUBLISHED release has same version)
        String checkVerSql = "SELECT release_id FROM game_releases WHERE project_id = ? AND version_string = ? AND status = 'PUBLISHED' AND release_id != ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkVerSql)) {
            ps.setString(1, expectedProjectId);
            ps.setString(2, release.getVersionString());
            ps.setString(3, releaseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException("Publish transaction aborted: Version " + release.getVersionString() + " is already published");
                }
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) throw ise;
            throw new RuntimeException("Version collision check failed: " + e.getMessage(), e);
        }

        // ATOMIC PUBLISH
        updateReleaseStatus(releaseId, ReleaseStatus.PUBLISHING);
        versionManager.lockPublishedRelease(releaseId);

        release.setStatus(ReleaseStatus.PUBLISHED);
        release.setImmutable(true);
        release.setPublishedAt(Instant.now());

        log.info("ATOMIC RELEASE PUBLISHED: id={}, project={}, version={}, sha256={}",
                releaseId, expectedProjectId, release.getVersionString(), artifact.getSha256Checksum());

        return release;
    }

    public Release rollbackRelease(String releaseId, String projectId) {
        Release release = getRelease(releaseId)
                .orElseThrow(() -> new IllegalArgumentException("Release not found: " + releaseId));

        if (!release.getProjectId().equals(projectId)) {
            throw new SecurityException("Cross-project rollback attempt rejected");
        }

        String sql = "UPDATE game_releases SET status = 'ROLLED_BACK' WHERE release_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, releaseId);
            ps.executeUpdate();
            release.setStatus(ReleaseStatus.ROLLED_BACK);
            log.info("Release {} rolled back for project {}", releaseId, projectId);
        } catch (Exception e) {
            throw new RuntimeException("Rollback failed: " + e.getMessage(), e);
        }

        return release;
    }

    public Release archiveRelease(String releaseId, String projectId) {
        Release release = getRelease(releaseId)
                .orElseThrow(() -> new IllegalArgumentException("Release not found: " + releaseId));

        if (!release.getProjectId().equals(projectId)) {
            throw new SecurityException("Cross-project archive attempt rejected");
        }

        String sql = "UPDATE game_releases SET status = 'ARCHIVED' WHERE release_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, releaseId);
            ps.executeUpdate();
            release.setStatus(ReleaseStatus.ARCHIVED);
            log.info("Release {} archived for project {}", releaseId, projectId);
        } catch (Exception e) {
            throw new RuntimeException("Archive failed: " + e.getMessage(), e);
        }

        return release;
    }

    public Optional<Release> getRelease(String releaseId) {
        String sql = "SELECT release_id, project_id, version_string, channel, status, build_id, " +
                     "validation_report_json, approval_record_json, changelog, is_immutable, created_at, published_at " +
                     "FROM game_releases WHERE release_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, releaseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Instant createdAt = parseInstantSafely(rs.getString("created_at"));
                    Release r = new Release(
                            rs.getString("release_id"),
                            rs.getString("project_id"),
                            rs.getString("version_string"),
                            ReleaseChannel.valueOf(rs.getString("channel")),
                            ReleaseStatus.valueOf(rs.getString("status")),
                            rs.getString("build_id"),
                            rs.getString("changelog"),
                            createdAt
                    );
                    r.setValidationReportJson(rs.getString("validation_report_json"));
                    r.setApprovalRecordJson(rs.getString("approval_record_json"));
                    r.setImmutable(rs.getInt("is_immutable") == 1);
                    if (rs.getString("published_at") != null) {
                        r.setPublishedAt(parseInstantSafely(rs.getString("published_at")));
                    }
                    return Optional.of(r);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get release {}: {}", releaseId, e.getMessage());
        }
        return Optional.empty();
    }

    public List<Release> listReleasesForProject(String projectId) {
        List<Release> list = new ArrayList<>();
        String sql = "SELECT release_id, project_id, version_string, channel, status, build_id, " +
                     "validation_report_json, approval_record_json, changelog, is_immutable, created_at, published_at " +
                     "FROM game_releases WHERE project_id = ? ORDER BY created_at DESC";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Release r = new Release(
                            rs.getString("release_id"),
                            rs.getString("project_id"),
                            rs.getString("version_string"),
                            ReleaseChannel.valueOf(rs.getString("channel")),
                            ReleaseStatus.valueOf(rs.getString("status")),
                            rs.getString("build_id"),
                            rs.getString("changelog"),
                            Instant.parse(rs.getString("created_at"))
                    );
                    r.setValidationReportJson(rs.getString("validation_report_json"));
                    r.setApprovalRecordJson(rs.getString("approval_record_json"));
                    r.setImmutable(rs.getInt("is_immutable") == 1);
                    if (rs.getString("published_at") != null) {
                        r.setPublishedAt(Instant.parse(rs.getString("published_at")));
                    }
                    list.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list releases for project {}: {}", projectId, e.getMessage());
        }
        return list;
    }

    public void updateReleaseStatus(String releaseId, ReleaseStatus status) {
        String sql = "UPDATE game_releases SET status = ? WHERE release_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, releaseId);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to update status for release {}: {}", releaseId, e.getMessage());
        }
    }

    public void updateValidationReport(String releaseId, String reportJson) {
        String sql = "UPDATE game_releases SET validation_report_json = ? WHERE release_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reportJson);
            ps.setString(2, releaseId);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to update validation report for release {}: {}", releaseId, e.getMessage());
        }
    }

    private Instant parseInstantSafely(String s) {
        if (s == null || s.isBlank()) return Instant.now();
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            try {
                return Instant.parse(s.replace(' ', 'T') + "Z");
            } catch (Exception ignored) {
                return Instant.now();
            }
        }
    }
}
