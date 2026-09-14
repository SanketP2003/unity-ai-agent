package com.unityagent.product.service;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;

/**
 * Service orchestrating artifact deployments via provider-neutral deployment adapters.
 * Guarantees that only verified, published releases can be deployed.
 */
@Service
public class DeploymentManager {

    private static final Logger log = LoggerFactory.getLogger(DeploymentManager.class);
    private final MemoryDatabase memoryDb;
    private final ArtifactManager artifactManager;

    public DeploymentManager(MemoryDatabase memoryDb, ArtifactManager artifactManager) {
        this.memoryDb = memoryDb;
        this.artifactManager = artifactManager;
    }

    /**
     * Deploys a published release artifact to the specified target directory.
     */
    public DeploymentRecord deployRelease(String deploymentId, Release release, BuildArtifact artifact,
                                          String targetName, String providerType, Path projectRoot, Path destinationDirectory) {
        log.info("Starting deployment {} for release {} ({}) to {}", deploymentId, release.getReleaseId(), targetName, destinationDirectory);

        // Security check: only PUBLISHED releases can be deployed
        if (release.getStatus() != ReleaseStatus.PUBLISHED) {
            throw new IllegalStateException("Deployment rejected: Only PUBLISHED releases can be deployed. Current status: " + release.getStatus());
        }

        // Verify physical artifact integrity before deploying
        boolean intact = artifactManager.verifyArtifactIntegrity(artifact.getArtifactId(), projectRoot);
        if (!intact) {
            throw new IllegalStateException("Deployment rejected: Artifact checksum or size mismatch (CORRUPTED)");
        }

        Instant now = Instant.now();
        DeploymentRecord record = new DeploymentRecord(deploymentId, release.getReleaseId(), release.getProjectId(),
                targetName, providerType, DeploymentStatus.DEPLOYING, destinationDirectory.toString(), now);

        saveDeploymentRecord(record);

        try {
            Files.createDirectories(destinationDirectory);
            Path sourceFile = projectRoot.resolve(artifact.getRelativePath());
            Path targetFile = destinationDirectory.resolve(sourceFile.getFileName());

            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

            record.setStatus(DeploymentStatus.SUCCEEDED);
            record.setCompletedAt(Instant.now());
            updateDeploymentRecord(record);

            log.info("Deployment {} SUCCEEDED to {}", deploymentId, targetFile);
            return record;
        } catch (Exception e) {
            log.error("Deployment {} FAILED: {}", deploymentId, e.getMessage());
            record.setStatus(DeploymentStatus.FAILED);
            record.setCompletedAt(Instant.now());
            updateDeploymentRecord(record);
            throw new RuntimeException("Deployment failed: " + e.getMessage(), e);
        }
    }

    private void saveDeploymentRecord(DeploymentRecord record) {
        String sql = "INSERT OR REPLACE INTO deployment_records " +
                     "(deployment_id, release_id, project_id, target_name, provider_type, status, target_location, created_at, completed_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getDeploymentId());
            ps.setString(2, record.getReleaseId());
            ps.setString(3, record.getProjectId());
            ps.setString(4, record.getTargetName());
            ps.setString(5, record.getProviderType());
            ps.setString(6, record.getStatus().name());
            ps.setString(7, record.getTargetLocation());
            ps.setString(8, record.getCreatedAt().toString());
            ps.setString(9, record.getCompletedAt() != null ? record.getCompletedAt().toString() : null);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to save deployment record {}: {}", record.getDeploymentId(), e.getMessage());
        }
    }

    private void updateDeploymentRecord(DeploymentRecord record) {
        String sql = "UPDATE deployment_records SET status = ?, completed_at = ? WHERE deployment_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getStatus().name());
            ps.setString(2, record.getCompletedAt() != null ? record.getCompletedAt().toString() : null);
            ps.setString(3, record.getDeploymentId());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to update deployment record {}: {}", record.getDeploymentId(), e.getMessage());
        }
    }

    public List<DeploymentRecord> listDeployments(String projectId) {
        List<DeploymentRecord> list = new ArrayList<>();
        String sql = "SELECT deployment_id, release_id, project_id, target_name, provider_type, status, target_location, created_at, completed_at " +
                     "FROM deployment_records WHERE project_id = ? ORDER BY created_at DESC";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DeploymentRecord r = new DeploymentRecord(
                            rs.getString("deployment_id"),
                            rs.getString("release_id"),
                            rs.getString("project_id"),
                            rs.getString("target_name"),
                            rs.getString("provider_type"),
                            DeploymentStatus.valueOf(rs.getString("status")),
                            rs.getString("target_location"),
                            Instant.parse(rs.getString("created_at"))
                    );
                    if (rs.getString("completed_at") != null) {
                        r.setCompletedAt(Instant.parse(rs.getString("completed_at")));
                    }
                    list.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list deployments for {}: {}", projectId, e.getMessage());
        }
        return list;
    }
}
