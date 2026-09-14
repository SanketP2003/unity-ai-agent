package com.unityagent.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ProjectTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;

/**
 * Service managing versioned project templates and automated project instantiation.
 */
@Service
public class ProjectTemplateManager {

    private static final Logger log = LoggerFactory.getLogger(ProjectTemplateManager.class);
    private final MemoryDatabase memoryDb;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ProjectTemplateManager(MemoryDatabase memoryDb) {
        this.memoryDb = memoryDb;
    }

    @PostConstruct
    public void initBuiltinTemplates() {
        registerBuiltinTemplates();
    }

    public synchronized void registerBuiltinTemplates() {
        List<ProjectTemplate> defaults = List.of(
                new ProjectTemplate("tpl-blank-3d", "Blank 3D", "6000.4.7f1",
                        List.of("com.unity.modules.ai", "com.unity.modules.physics"),
                        List.of("Assets/Scenes/SampleScene.unity"),
                        List.of("WINDOWS", "LINUX", "OSX", "WEBGL"), "1.0.0"),
                new ProjectTemplate("tpl-3d-platformer", "3D Platformer", "6000.4.7f1",
                        List.of("com.unity.modules.physics", "com.unity.inputsystem"),
                        List.of("Assets/Scenes/PlatformerLevel1.unity"),
                        List.of("WINDOWS", "LINUX", "OSX", "ANDROID", "WEBGL"), "1.0.0"),
                new ProjectTemplate("tpl-top-down", "Top-Down Game", "6000.4.7f1",
                        List.of("com.unity.modules.physics", "com.unity.modules.navmesh"),
                        List.of("Assets/Scenes/TopDownArena.unity"),
                        List.of("WINDOWS", "LINUX", "OSX", "WEBGL"), "1.0.0"),
                new ProjectTemplate("tpl-third-person", "Third-Person Game", "6000.4.7f1",
                        List.of("com.unity.cinemachine", "com.unity.inputsystem"),
                        List.of("Assets/Scenes/ThirdPersonWorld.unity"),
                        List.of("WINDOWS", "LINUX", "OSX"), "1.0.0"),
                new ProjectTemplate("tpl-2d-game", "2D Game", "6000.4.7f1",
                        List.of("com.unity.2d.sprite", "com.unity.2d.tilemap"),
                        List.of("Assets/Scenes/Game2D.unity"),
                        List.of("WINDOWS", "LINUX", "OSX", "ANDROID", "WEBGL"), "1.0.0")
        );

        for (ProjectTemplate t : defaults) {
            registerTemplate(t);
        }
    }

    public void registerTemplate(ProjectTemplate tpl) {
        String sql = "INSERT OR REPLACE INTO project_templates " +
                     "(template_id, name, unity_version, required_packages, default_scenes, supported_platforms, version) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tpl.getTemplateId());
            ps.setString(2, tpl.getName());
            ps.setString(3, tpl.getUnityVersion());
            ps.setString(4, objectMapper.writeValueAsString(tpl.getRequiredPackages()));
            ps.setString(5, objectMapper.writeValueAsString(tpl.getDefaultScenes()));
            ps.setString(6, objectMapper.writeValueAsString(tpl.getSupportedPlatforms()));
            ps.setString(7, tpl.getVersion());
            ps.executeUpdate();
            log.debug("Registered project template: {}", tpl.getTemplateId());
        } catch (Exception e) {
            log.warn("Failed to register template {}: {}", tpl.getTemplateId(), e.getMessage());
        }
    }

    public Optional<ProjectTemplate> getTemplate(String templateId) {
        String sql = "SELECT template_id, name, unity_version, required_packages, default_scenes, supported_platforms, version " +
                     "FROM project_templates WHERE template_id = ?";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    List<String> pkgs = objectMapper.readValue(rs.getString("required_packages"), new TypeReference<>() {});
                    List<String> scenes = objectMapper.readValue(rs.getString("default_scenes"), new TypeReference<>() {});
                    List<String> platforms = objectMapper.readValue(rs.getString("supported_platforms"), new TypeReference<>() {});
                    return Optional.of(new ProjectTemplate(
                            rs.getString("template_id"),
                            rs.getString("name"),
                            rs.getString("unity_version"),
                            pkgs, scenes, platforms,
                            rs.getString("version")
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get template {}: {}", templateId, e.getMessage());
        }
        return Optional.empty();
    }

    public List<ProjectTemplate> listTemplates() {
        List<ProjectTemplate> list = new ArrayList<>();
        String sql = "SELECT template_id, name, unity_version, required_packages, default_scenes, supported_platforms, version " +
                     "FROM project_templates ORDER BY name ASC";
        try (Connection conn = memoryDb.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> pkgs = objectMapper.readValue(rs.getString("required_packages"), new TypeReference<>() {});
                List<String> scenes = objectMapper.readValue(rs.getString("default_scenes"), new TypeReference<>() {});
                List<String> platforms = objectMapper.readValue(rs.getString("supported_platforms"), new TypeReference<>() {});
                list.add(new ProjectTemplate(
                        rs.getString("template_id"),
                        rs.getString("name"),
                        rs.getString("unity_version"),
                        pkgs, scenes, platforms,
                        rs.getString("version")
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to list templates: {}", e.getMessage());
        }
        return list;
    }

    /**
     * Instantiates a template into a real filesystem directory and registers the project in the authoritative projects table.
     */
    public void instantiateProject(String templateId, String projectId, String projectName, Path targetRoot) {
        ProjectTemplate tpl = getTemplate(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        try {
            // Create Unity directory scaffold
            Files.createDirectories(targetRoot.resolve("Assets").resolve("Scenes"));
            Files.createDirectories(targetRoot.resolve("Assets").resolve("Scripts"));
            Files.createDirectories(targetRoot.resolve("ProjectSettings"));

            // Create default scene files if specified
            for (String scenePath : tpl.getDefaultScenes()) {
                Path fullScenePath = targetRoot.resolve(scenePath);
                Files.createDirectories(fullScenePath.getParent());
                if (!Files.exists(fullScenePath)) {
                    Files.writeString(fullScenePath, "%YAML 1.1\n%TAG !u! tag:unity3d.com,2011:\n--- !u!29 &1\nOcclusionCullingSettings:\n  m_ObjectHideFlags: 0\n");
                }
            }

            // Register in authoritative projects table (Single Source of Truth)
            String sql = "INSERT OR REPLACE INTO projects (project_id, project_name, unity_version, platform, created_at) " +
                         "VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = memoryDb.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, projectId);
                ps.setString(2, projectName);
                ps.setString(3, tpl.getUnityVersion());
                ps.setString(4, tpl.getSupportedPlatforms().isEmpty() ? "WINDOWS" : tpl.getSupportedPlatforms().get(0));
                ps.setString(5, Instant.now().toString());
                ps.executeUpdate();
            }

            log.info("Instantiated template {} for project {} at {}", templateId, projectId, targetRoot);
        } catch (Exception e) {
            log.error("Failed to instantiate template {} for project {}: {}", templateId, projectId, e.getMessage());
            throw new RuntimeException("Instantiation failed: " + e.getMessage(), e);
        }
    }
}
