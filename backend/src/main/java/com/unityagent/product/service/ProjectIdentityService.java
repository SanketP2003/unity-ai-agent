package com.unityagent.product.service;

import com.unityagent.memory.MemoryDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service managing persistent project identity, format validation,
 * fingerprinting, and clone detection.
 */
@Service
public class ProjectIdentityService {

    private static final Logger log = LoggerFactory.getLogger(ProjectIdentityService.class);
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^(proj|project)_[a-zA-Z0-9_-]{4,64}$");

    private final MemoryDatabase memoryDb;

    public ProjectIdentityService(MemoryDatabase memoryDb) {
        this.memoryDb = memoryDb;
    }

    public static class CloneDetectionResult {
        private final boolean cloneDetected;
        private final String projectId;
        private final String registeredPath;
        private final String currentPath;

        public CloneDetectionResult(boolean cloneDetected, String projectId, String registeredPath, String currentPath) {
            this.cloneDetected = cloneDetected;
            this.projectId = projectId;
            this.registeredPath = registeredPath;
            this.currentPath = currentPath;
        }

        public boolean isCloneDetected() {
            return cloneDetected;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getRegisteredPath() {
            return registeredPath;
        }

        public String getCurrentPath() {
            return currentPath;
        }
    }

    /**
     * Validates if the project ID conforms to standard Autonomous Game Studio project identifiers.
     */
    public boolean isValidProjectId(String projectId) {
        if (projectId == null || projectId.trim().isEmpty()) {
            return false;
        }
        return VALID_ID_PATTERN.matcher(projectId.trim()).matches();
    }

    /**
     * Generates a new stable project identifier prefixed with 'proj_'.
     */
    public String generateProjectId() {
        return "proj_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * Computes a deterministic SHA-256 fingerprint for a project.
     */
    public String computeFingerprint(String projectId, String projectPath, String createdAt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = (projectId != null ? projectId : "") + "|"
                    + normalizePath(projectPath) + "|"
                    + (createdAt != null ? createdAt : "");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to compute project fingerprint: {}", e.getMessage());
            return "fp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }

    /**
     * Checks if a project directory was moved or cloned from an existing registered project path.
     */
    public CloneDetectionResult detectCloneOrMove(String projectId, String currentPath) {
        if (projectId == null || currentPath == null) {
            return new CloneDetectionResult(false, projectId, null, currentPath);
        }

        String normalizedCurrent = normalizePath(currentPath);
        String sql = "SELECT project_path FROM projects WHERE project_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String registered = rs.getString("project_path");
                    if (registered != null && !registered.trim().isEmpty()) {
                        String normalizedReg = normalizePath(registered);
                        if (!normalizedReg.equalsIgnoreCase(normalizedCurrent)) {
                            log.warn("Clone or directory move detected for project {}: registered='{}', current='{}'",
                                    projectId, normalizedReg, normalizedCurrent);
                            return new CloneDetectionResult(true, projectId, normalizedReg, normalizedCurrent);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error verifying clone state for project {}: {}", projectId, e.getMessage());
        }

        return new CloneDetectionResult(false, projectId, normalizedCurrent, normalizedCurrent);
    }

    public String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return "";
        }
        try {
            Path p = Paths.get(rawPath.trim()).toAbsolutePath().normalize();
            return p.toString().replace('\\', '/').replaceAll("/+$", "");
        } catch (Exception e) {
            return rawPath.trim().replace('\\', '/').replaceAll("/+$", "");
        }
    }
}
