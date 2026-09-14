package com.unityagent.product.service;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ArtifactStatus;
import com.unityagent.product.model.BuildArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Service managing build artifacts with zero-trust path containment,
 * cryptographic SHA-256 hashing, physical verification, and tamper detection.
 */
@Service
public class ArtifactManager {

    private static final Logger log = LoggerFactory.getLogger(ArtifactManager.class);
    public static final long MAX_ARTIFACT_SIZE_BYTES = 10L * 1024 * 1024 * 1024; // 10 GB limit

    private final MemoryDatabase memoryDb;

    public ArtifactManager(MemoryDatabase memoryDb) {
        this.memoryDb = memoryDb;
    }

    /**
     * Resolves and strictly canonicalizes an artifact path.
     * Prevents directory traversal, symlink escapes, and unapproved roots.
     */
    public Path validateAndCanonicalizePath(Path projectRoot, String rawRelativePath) {
        if (rawRelativePath == null || rawRelativePath.isBlank()) {
            throw new SecurityException("Artifact path cannot be null or blank");
        }
        if (rawRelativePath.contains("\0") || rawRelativePath.contains("..")) {
            throw new SecurityException("Illegal path sequence detected in artifact path: " + rawRelativePath);
        }

        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path candidate = normalizedRoot.resolve(rawRelativePath).normalize().toAbsolutePath();

        if (!candidate.startsWith(normalizedRoot)) {
            throw new SecurityException("Path containment violation: Artifact path must reside within project root: " + rawRelativePath);
        }

        // Check symlink containment if link exists
        try {
            if (Files.isSymbolicLink(candidate)) {
                Path realTarget = candidate.toRealPath();
                if (!realTarget.startsWith(normalizedRoot)) {
                    throw new SecurityException("Symlink escape detected: Artifact link targets outside project root");
                }
            }
        } catch (Exception e) {
            log.debug("Path resolution check: {}", e.getMessage());
        }

        return candidate;
    }

    /**
     * Computes the cryptographic SHA-256 checksum of a file on disk.
     */
    public String calculateSha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.error("Failed to compute SHA-256 for {}: {}", file, e.getMessage());
            throw new RuntimeException("SHA-256 calculation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Registers a physical artifact with on-disk verification and hash calculation.
     */
    public BuildArtifact registerArtifact(String artifactId, String projectId, String releaseId,
                                          String platform, String architecture,
                                          Path projectRoot, String rawRelativePath) {
        Path canonicalPath = validateAndCanonicalizePath(projectRoot, rawRelativePath);

        if (!Files.exists(canonicalPath)) {
            throw new IllegalStateException("Physical artifact file does not exist on disk: " + canonicalPath);
        }
        if (!Files.isRegularFile(canonicalPath)) {
            throw new IllegalStateException("Artifact path is not a regular file: " + canonicalPath);
        }

        long size;
        try {
            size = Files.size(canonicalPath);
        } catch (Exception e) {
            throw new RuntimeException("Cannot read artifact size: " + e.getMessage(), e);
        }

        if (size <= 0) {
            throw new IllegalStateException("Artifact size must be greater than 0 bytes");
        }
        if (size > MAX_ARTIFACT_SIZE_BYTES) {
            throw new SecurityException("Artifact exceeds maximum permissible size of " + MAX_ARTIFACT_SIZE_BYTES + " bytes: " + size);
        }

        String hash = calculateSha256(canonicalPath);
        String relativeStoredPath = projectRoot.toAbsolutePath().normalize().relativize(canonicalPath).toString().replace('\\', '/');

        Instant now = Instant.now();
        BuildArtifact artifact = new BuildArtifact(artifactId, projectId, releaseId, platform, architecture,
                relativeStoredPath, size, hash, ArtifactStatus.VERIFIED, now);

        String sql = "INSERT OR REPLACE INTO build_artifacts " +
                     "(artifact_id, project_id, release_id, platform, architecture, relative_path, size_bytes, sha256_checksum, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, artifact.getArtifactId());
            ps.setString(2, artifact.getProjectId());
            ps.setString(3, artifact.getReleaseId());
            ps.setString(4, artifact.getPlatform());
            ps.setString(5, artifact.getArchitecture());
            ps.setString(6, artifact.getRelativePath());
            ps.setLong(7, artifact.getSizeBytes());
            ps.setString(8, artifact.getSha256Checksum());
            ps.setString(9, artifact.getStatus().name());
            ps.setString(10, artifact.getCreatedAt().toString());
            ps.executeUpdate();
            log.info("Registered verified artifact {}: size={}, sha256={}", artifactId, size, hash);
        } catch (Exception e) {
            log.error("Failed to register artifact {}: {}", artifactId, e.getMessage());
            throw new RuntimeException("Failed to register artifact: " + e.getMessage(), e);
        }

        return artifact;
    }

    /**
     * Verifies physical artifact integrity against stored size and SHA-256 hash.
     * Detects tampering and marks status as CORRUPTED on mismatch.
     */
    public boolean verifyArtifactIntegrity(String artifactId, Path projectRoot) {
        Optional<BuildArtifact> opt = getArtifactRaw(artifactId);
        if (opt.isEmpty()) return false;
        BuildArtifact artifact = opt.get();

        Path path;
        try {
            path = validateAndCanonicalizePath(projectRoot, artifact.getRelativePath());
        } catch (Exception e) {
            markCorrupted(artifactId);
            return false;
        }

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            log.warn("Artifact file missing on disk: {}", path);
            markCorrupted(artifactId);
            return false;
        }

        try {
            long currentSize = Files.size(path);
            if (currentSize != artifact.getSizeBytes()) {
                log.error("Artifact size mismatch for {}: expected={}, actual={}", artifactId, artifact.getSizeBytes(), currentSize);
                markCorrupted(artifactId);
                return false;
            }

            String currentHash = calculateSha256(path);
            if (!currentHash.equalsIgnoreCase(artifact.getSha256Checksum())) {
                log.error("Artifact SHA-256 mismatch (TAMPER DETECTED) for {}: recorded={}, actual={}",
                        artifactId, artifact.getSha256Checksum(), currentHash);
                markCorrupted(artifactId);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Error verifying artifact integrity for {}: {}", artifactId, e.getMessage());
            markCorrupted(artifactId);
            return false;
        }
    }

    public Optional<BuildArtifact> getArtifact(String projectId, String artifactId) {
        return getArtifactRaw(artifactId).filter(a -> a.getProjectId().equals(projectId));
    }

    /**
     * Enforces tenant isolation: Project A cannot access Project B's artifact.
     */
    public BuildArtifact getArtifactWithTenantCheck(String artifactId, String requestedProjectId) {
        BuildArtifact a = getArtifactRaw(artifactId)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + artifactId));
        if (!a.getProjectId().equals(requestedProjectId)) {
            throw new SecurityException("ACCESS_DENIED: Cross-project artifact access prohibited");
        }
        return a;
    }

    public Optional<BuildArtifact> getArtifactRaw(String artifactId) {
        String sql = "SELECT artifact_id, project_id, release_id, platform, architecture, relative_path, size_bytes, sha256_checksum, status, created_at " +
                     "FROM build_artifacts WHERE artifact_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, artifactId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new BuildArtifact(
                            rs.getString("artifact_id"),
                            rs.getString("project_id"),
                            rs.getString("release_id"),
                            rs.getString("platform"),
                            rs.getString("architecture"),
                            rs.getString("relative_path"),
                            rs.getLong("size_bytes"),
                            rs.getString("sha256_checksum"),
                            ArtifactStatus.valueOf(rs.getString("status")),
                            Instant.parse(rs.getString("created_at"))
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query artifact {}: {}", artifactId, e.getMessage());
        }
        return Optional.empty();
    }

    public List<BuildArtifact> listArtifactsForRelease(String releaseId, String projectId) {
        List<BuildArtifact> list = new ArrayList<>();
        String sql = "SELECT artifact_id, project_id, release_id, platform, architecture, relative_path, size_bytes, sha256_checksum, status, created_at " +
                     "FROM build_artifacts WHERE release_id = ? AND project_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, releaseId);
            ps.setString(2, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BuildArtifact(
                            rs.getString("artifact_id"),
                            rs.getString("project_id"),
                            rs.getString("release_id"),
                            rs.getString("platform"),
                            rs.getString("architecture"),
                            rs.getString("relative_path"),
                            rs.getLong("size_bytes"),
                            rs.getString("sha256_checksum"),
                            ArtifactStatus.valueOf(rs.getString("status")),
                            Instant.parse(rs.getString("created_at"))
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list artifacts for release {}: {}", releaseId, e.getMessage());
        }
        return list;
    }

    private void markCorrupted(String artifactId) {
        String sql = "UPDATE build_artifacts SET status = 'CORRUPTED' WHERE artifact_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, artifactId);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }
}
