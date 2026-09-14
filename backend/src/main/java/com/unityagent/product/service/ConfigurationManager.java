package com.unityagent.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ConfigEnvironment;
import com.unityagent.product.model.ConfigurationProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service managing environment configuration profiles.
 * Enforces strict security boundary: credentials and secrets are forbidden in profiles.
 */
@Service
public class ConfigurationManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationManager.class);
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(sk-[a-zA-Z0-9]{20,}|bearer\\s+[a-zA-Z0-9_.-]{20,})");
    private static final Set<String> SENSITIVE_KEYS = Set.of("apikey", "api_key", "secret", "password", "token", "private_key", "credentials");

    private final MemoryDatabase memoryDb;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ConfigurationManager(MemoryDatabase memoryDb) {
        this.memoryDb = memoryDb;
    }

    public ConfigurationProfile saveProfile(String profileId, String projectId, String profileName,
                                            ConfigEnvironment environment, Map<String, Object> settings) {
        // Enforce zero-secret invariant
        validateNoSecrets(settings);

        Instant now = Instant.now();
        ConfigurationProfile profile = new ConfigurationProfile(profileId, projectId, profileName, environment, settings, now);

        String sql = "INSERT OR REPLACE INTO configuration_profiles (profile_id, project_id, profile_name, environment, settings_json, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, profile.getProfileId());
            ps.setString(2, profile.getProjectId());
            ps.setString(3, profile.getProfileName());
            ps.setString(4, profile.getEnvironment().name());
            ps.setString(5, objectMapper.writeValueAsString(profile.getSettings()));
            ps.setString(6, profile.getCreatedAt().toString());
            ps.executeUpdate();
            log.info("Saved configuration profile {} for project {} in {}", profileId, projectId, environment);
        } catch (Exception e) {
            log.error("Failed to save profile {}: {}", profileId, e.getMessage());
            throw new RuntimeException("Failed to save profile: " + e.getMessage(), e);
        }

        return profile;
    }

    public Optional<ConfigurationProfile> getProfile(String profileId) {
        String sql = "SELECT profile_id, project_id, profile_name, environment, settings_json, created_at FROM configuration_profiles WHERE profile_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> settings = objectMapper.readValue(rs.getString("settings_json"), new TypeReference<>() {});
                    return Optional.of(new ConfigurationProfile(
                            rs.getString("profile_id"),
                            rs.getString("project_id"),
                            rs.getString("profile_name"),
                            ConfigEnvironment.valueOf(rs.getString("environment")),
                            settings,
                            Instant.parse(rs.getString("created_at"))
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get profile {}: {}", profileId, e.getMessage());
        }
        return Optional.empty();
    }

    public List<ConfigurationProfile> listProfilesForProject(String projectId) {
        List<ConfigurationProfile> list = new ArrayList<>();
        String sql = "SELECT profile_id, project_id, profile_name, environment, settings_json, created_at FROM configuration_profiles WHERE project_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> settings = objectMapper.readValue(rs.getString("settings_json"), new TypeReference<>() {});
                    list.add(new ConfigurationProfile(
                            rs.getString("profile_id"),
                            rs.getString("project_id"),
                            rs.getString("profile_name"),
                            ConfigEnvironment.valueOf(rs.getString("environment")),
                            settings,
                            Instant.parse(rs.getString("created_at"))
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list profiles for project {}: {}", projectId, e.getMessage());
        }
        return list;
    }

    public List<ConfigurationProfile> listProfiles(String projectId) {
        return listProfilesForProject(projectId);
    }

    public void validateNoSecrets(Map<String, Object> settings) {
        if (settings == null) return;
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().toLowerCase().replace("-", "_") : "";
            boolean isSensitive = SENSITIVE_KEYS.stream().anyMatch(s -> {
                if ("token".equals(s)) {
                    return key.equals("token") || key.endsWith("_token") || key.startsWith("token_")
                            || key.contains("auth_token") || key.contains("access_token");
                }
                return key.contains(s);
            });
            if (isSensitive) {
                throw new SecurityException("Security violation: Configuration profiles cannot contain secret key '" + entry.getKey() + "'");
            }
            if (entry.getValue() instanceof String val) {
                if (SECRET_PATTERN.matcher(val).find()) {
                    throw new SecurityException("Security violation: Detected credential or API key token in configuration value");
                }
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castMap = (Map<String, Object>) nested;
                validateNoSecrets(castMap);
            }
        }
    }
}
