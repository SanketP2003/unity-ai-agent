package com.unityagent.product.service;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.GameVersion;
import com.unityagent.product.model.Release;
import com.unityagent.product.model.ReleaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;

/**
 * Service managing semantic versions and enforcing release immutability.
 */
@Service
public class VersionManager {

    private static final Logger log = LoggerFactory.getLogger(VersionManager.class);
    private final MemoryDatabase memoryDb;

    public VersionManager(MemoryDatabase memoryDb) {
        this.memoryDb = memoryDb;
    }

    public GameVersion parseVersion(String versionString) {
        return GameVersion.parse(versionString);
    }

    public boolean isVersionUnused(String projectId, String versionString) {
        String sql = "SELECT 1 FROM game_releases WHERE project_id = ? AND version_string = ? AND status != 'FAILED'";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, versionString.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next();
            }
        } catch (Exception e) {
            log.warn("Failed to check version uniqueness for project {}: {}", projectId, e.getMessage());
            return false;
        }
    }

    public Optional<GameVersion> getLatestPublishedVersion(String projectId) {
        String sql = "SELECT version_string FROM game_releases WHERE project_id = ? AND status = 'PUBLISHED' " +
                     "ORDER BY rowid DESC";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                GameVersion latest = null;
                while (rs.next()) {
                    try {
                        GameVersion v = GameVersion.parse(rs.getString("version_string"));
                        if (latest == null || v.compareTo(latest) > 0) {
                            latest = v;
                        }
                    } catch (Exception ignored) {}
                }
                return Optional.ofNullable(latest);
            }
        } catch (Exception e) {
            log.warn("Failed to query latest version: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public GameVersion suggestNextVersion(String projectId, String bumpType) {
        Optional<GameVersion> latest = getLatestPublishedVersion(projectId);
        GameVersion base = latest.orElse(new GameVersion(0, 1, 0));

        if ("MAJOR".equalsIgnoreCase(bumpType)) {
            return base.bumpMajor();
        } else if ("MINOR".equalsIgnoreCase(bumpType)) {
            return base.bumpMinor();
        } else {
            return base.bumpPatch();
        }
    }

    public void lockPublishedRelease(String releaseId) {
        String sql = "UPDATE game_releases SET is_immutable = 1, status = 'PUBLISHED', published_at = ? WHERE release_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, releaseId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                log.info("Release {} is now LOCKED and IMMUTABLE", releaseId);
            }
        } catch (Exception e) {
            log.error("Failed to lock release {}: {}", releaseId, e.getMessage());
            throw new RuntimeException("Failed to lock release: " + e.getMessage(), e);
        }
    }

    public boolean isReleaseImmutable(String releaseId) {
        String sql = "SELECT is_immutable FROM game_releases WHERE release_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, releaseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("is_immutable") == 1;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check release immutability: {}", e.getMessage());
        }
        return false;
    }
}
