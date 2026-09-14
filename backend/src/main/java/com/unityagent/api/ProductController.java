package com.unityagent.api;

import com.unityagent.product.model.*;
import com.unityagent.product.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * REST API controller for Phase 12 Product & Distribution operations.
 */
@RestController
@RequestMapping("/api/product")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final WorkspaceManager workspaceManager;
    private final ProjectTemplateManager templateManager;
    private final ConfigurationManager configurationManager;
    private final VersionManager versionManager;
    private final ArtifactManager artifactManager;
    private final PlatformCapabilityDetector capabilityDetector;
    private final ReleaseManager releaseManager;
    private final BackupManager backupManager;
    private final ProjectPackageService packageService;
    private final DeploymentManager deploymentManager;
    private final ProductDiagnosticsService diagnosticsService;

    public ProductController(WorkspaceManager workspaceManager,
                             ProjectTemplateManager templateManager,
                             ConfigurationManager configurationManager,
                             VersionManager versionManager,
                             ArtifactManager artifactManager,
                             PlatformCapabilityDetector capabilityDetector,
                             ReleaseManager releaseManager,
                             BackupManager backupManager,
                             ProjectPackageService packageService,
                             DeploymentManager deploymentManager,
                             ProductDiagnosticsService diagnosticsService) {
        this.workspaceManager = workspaceManager;
        this.templateManager = templateManager;
        this.configurationManager = configurationManager;
        this.versionManager = versionManager;
        this.artifactManager = artifactManager;
        this.capabilityDetector = capabilityDetector;
        this.releaseManager = releaseManager;
        this.backupManager = backupManager;
        this.packageService = packageService;
        this.deploymentManager = deploymentManager;
        this.diagnosticsService = diagnosticsService;
    }

    // ── Workspaces & Project Lifecycle ──────────────────────────────────────

    @GetMapping("/workspaces")
    public List<Workspace> getWorkspaces() {
        return workspaceManager.listWorkspaces();
    }

    @PostMapping("/workspaces")
    public Workspace createWorkspace(@RequestBody Map<String, String> body) {
        String id = body.getOrDefault("workspaceId", "ws_" + UUID.randomUUID().toString().substring(0, 8));
        String name = body.getOrDefault("name", "Studio Workspace");
        String rootPath = body.getOrDefault("rootPath", "Workspaces/" + id);
        return workspaceManager.createWorkspace(id, name, rootPath);
    }

    @GetMapping("/workspaces/{workspaceId}/projects")
    public List<WorkspaceProject> getWorkspaceProjects(@PathVariable String workspaceId) {
        return workspaceManager.listWorkspaceProjects(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/projects/{projectId}")
    public WorkspaceProject linkProject(@PathVariable String workspaceId, @PathVariable String projectId) {
        return workspaceManager.addProjectToWorkspace(workspaceId, projectId);
    }

    @PostMapping("/projects/{projectId}/archive")
    public ResponseEntity<Void> archiveProject(@PathVariable String projectId) {
        workspaceManager.archiveProject(projectId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/projects/{projectId}/restore")
    public ResponseEntity<Void> restoreProject(@PathVariable String projectId) {
        workspaceManager.restoreProject(projectId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<?> deleteProject(@PathVariable String projectId,
                                           @RequestParam(defaultValue = "false") boolean confirm) {
        try {
            workspaceManager.deleteProject(projectId, confirm);
            return ResponseEntity.ok(Map.of("message", "Project " + projectId + " deleted"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Templates ───────────────────────────────────────────────────────────

    @GetMapping("/templates")
    public List<ProjectTemplate> getTemplates() {
        return templateManager.listTemplates();
    }

    @PostMapping("/templates/{templateId}/instantiate")
    public ResponseEntity<?> instantiateTemplate(@PathVariable String templateId,
                                                 @RequestBody Map<String, String> body) {
        String projectId = body.getOrDefault("projectId", "proj_" + UUID.randomUUID().toString().substring(0, 8));
        String projectName = body.getOrDefault("projectName", "NewGameProject");
        String rootPath = body.getOrDefault("targetDirectory", "Projects/" + projectId);
        try {
            templateManager.instantiateProject(templateId, projectId, projectName, Paths.get(rootPath));
            return ResponseEntity.ok(Map.of("projectId", projectId, "projectName", projectName, "status", "INSTANTIATED"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Platform Capabilities ───────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/capabilities")
    public Map<String, PlatformCapability> getCapabilities(@PathVariable String projectId) {
        return capabilityDetector.detectCapabilities(projectId);
    }

    // ── Releases & Approvals ────────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/releases")
    public List<Release> getReleases(@PathVariable String projectId) {
        return releaseManager.listReleasesForProject(projectId);
    }

    @PostMapping("/projects/{projectId}/releases")
    public ResponseEntity<?> createReleaseCandidate(@PathVariable String projectId,
                                                    @RequestBody Map<String, String> body) {
        String releaseId = body.getOrDefault("releaseId", "rel_" + UUID.randomUUID().toString().substring(0, 8));
        String version = body.getOrDefault("version", "1.0.0");
        String channelStr = body.getOrDefault("channel", "DEVELOPMENT");
        String buildId = body.get("buildId");
        String changelog = body.getOrDefault("changelog", "Initial release candidate");

        try {
            ReleaseChannel channel = ReleaseChannel.valueOf(channelStr.toUpperCase());
            Release rel = releaseManager.createReleaseCandidate(releaseId, projectId, version, channel, buildId, changelog);
            return ResponseEntity.ok(rel);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/releases/{releaseId}/approve")
    public ResponseEntity<?> approveRelease(@PathVariable String releaseId,
                                            @RequestBody Map<String, String> body) {
        String role = body.getOrDefault("role", "REVIEWER");
        String reviewerId = body.getOrDefault("reviewerId", "human_lead");
        String notes = body.getOrDefault("notes", "Approved via Studio");

        try {
            Release approved = releaseManager.approveRelease(releaseId, role, reviewerId, notes);
            return ResponseEntity.ok(approved);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", se.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/releases/{releaseId}/publish")
    public ResponseEntity<?> publishRelease(@PathVariable String releaseId,
                                            @RequestParam String projectId,
                                            @RequestParam String artifactId,
                                            @RequestParam(defaultValue = ".") String projectRoot) {
        try {
            BuildArtifact artifact = artifactManager.getArtifactWithTenantCheck(artifactId, projectId);
            Release published = releaseManager.publishRelease(releaseId, projectId, artifact, Paths.get(projectRoot));
            return ResponseEntity.ok(published);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", se.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/releases/{releaseId}/rollback")
    public ResponseEntity<?> rollbackRelease(@PathVariable String releaseId, @RequestParam String projectId) {
        try {
            Release rolledBack = releaseManager.rollbackRelease(releaseId, projectId);
            return ResponseEntity.ok(rolledBack);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Deployments ─────────────────────────────────────────────────────────

    @PostMapping("/releases/{releaseId}/deploy")
    public ResponseEntity<?> deployRelease(@PathVariable String releaseId,
                                           @RequestBody Map<String, String> body) {
        String deploymentId = body.getOrDefault("deploymentId", "dep_" + UUID.randomUUID().toString().substring(0, 8));
        String artifactId = body.get("artifactId");
        String targetName = body.getOrDefault("targetName", "Local");
        String providerType = body.getOrDefault("providerType", "LOCAL");
        String projectRootStr = body.getOrDefault("projectRoot", ".");
        String destinationStr = body.getOrDefault("destination", "Deployments/" + releaseId);

        try {
            Release rel = releaseManager.getRelease(releaseId)
                    .orElseThrow(() -> new IllegalArgumentException("Release not found"));
            BuildArtifact art = artifactManager.getArtifactWithTenantCheck(artifactId, rel.getProjectId());

            DeploymentRecord record = deploymentManager.deployRelease(deploymentId, rel, art, targetName,
                    providerType, Paths.get(projectRootStr), Paths.get(destinationStr));
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Backups & Restore ───────────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/backups")
    public List<BackupManifest> listBackups(@PathVariable String projectId) {
        return backupManager.listBackups(projectId);
    }

    @PostMapping("/projects/{projectId}/backups")
    public ResponseEntity<?> createBackup(@PathVariable String projectId,
                                          @RequestBody Map<String, String> body) {
        String backupId = body.getOrDefault("backupId", "bk_" + UUID.randomUUID().toString().substring(0, 8));
        String projectRootStr = body.getOrDefault("projectRoot", ".");
        String archiveLocationStr = body.getOrDefault("archiveLocation", "Backups/" + backupId + ".autonomous-backup");

        try {
            BackupManifest manifest = backupManager.createBackup(
                    backupId, projectId, Paths.get(projectRootStr), Paths.get(archiveLocationStr)
            );
            return ResponseEntity.ok(manifest);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/projects/{projectId}/backups/{backupId}/restore")
    public ResponseEntity<?> restoreBackup(@PathVariable String projectId,
                                           @PathVariable String backupId,
                                           @RequestBody Map<String, String> body) {
        String archiveLocationStr = body.getOrDefault("archiveLocation", "Backups/" + backupId + ".autonomous-backup");
        String targetRootStr = body.getOrDefault("targetRoot", "Restored/" + projectId);

        try {
            BackupManager.RestoreResult result = backupManager.restoreBackup(
                    backupId, projectId, Paths.get(archiveLocationStr), Paths.get(targetRootStr)
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Portable Packages (.autonomous-project) ─────────────────────────────

    @PostMapping("/projects/{projectId}/package/export")
    public ResponseEntity<?> exportPackage(@PathVariable String projectId,
                                           @RequestBody Map<String, String> body) {
        String projectName = body.getOrDefault("projectName", "ExportedGame");
        String projectRootStr = body.getOrDefault("projectRoot", ".");
        String exportLocationStr = body.getOrDefault("exportLocation", "Exports/" + projectId + ".autonomous-project");

        try {
            Path exported = packageService.exportProject(
                    projectId, projectName, Paths.get(projectRootStr), Paths.get(exportLocationStr)
            );
            return ResponseEntity.ok(Map.of("exportedFile", exported.toString(), "projectId", projectId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/projects/package/import")
    public ResponseEntity<?> importPackage(@RequestBody Map<String, String> body) {
        String packageFileStr = body.get("packageFile");
        String targetDirectoryStr = body.getOrDefault("targetDirectory", "Projects/Imported");

        if (packageFileStr == null || packageFileStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "packageFile is required"));
        }

        try {
            Map<String, Object> manifest = packageService.importProject(
                    Paths.get(packageFileStr), Paths.get(targetDirectoryStr)
            );
            return ResponseEntity.ok(manifest);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Configuration Profiles ──────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/configurations")
    public List<ConfigurationProfile> listConfigurations(@PathVariable String projectId) {
        return configurationManager.listProfiles(projectId);
    }

    @PostMapping("/projects/{projectId}/configurations")
    public ResponseEntity<?> saveConfiguration(@PathVariable String projectId,
                                               @RequestBody Map<String, Object> body) {
        String profileId = (String) body.getOrDefault("profileId", "cfg_" + UUID.randomUUID().toString().substring(0, 8));
        String profileName = (String) body.getOrDefault("profileName", "Default Config");
        String envStr = (String) body.getOrDefault("environment", "DEVELOPMENT");
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) body.getOrDefault("settings", Map.of());

        try {
            ConfigEnvironment env = ConfigEnvironment.valueOf(envStr.toUpperCase());
            ConfigurationProfile saved = configurationManager.saveProfile(profileId, projectId, profileName, env, settings);
            return ResponseEntity.ok(saved);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", se.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Diagnostics Bundle ──────────────────────────────────────────────────

    @PostMapping("/diagnostics/export")
    public ResponseEntity<?> exportDiagnostics(@RequestBody(required = false) Map<String, String> body) {
        String targetZipStr = body != null && body.containsKey("targetZip")
                ? body.get("targetZip")
                : "Diagnostics/diagnostics-bundle-" + System.currentTimeMillis() + ".zip";

        try {
            Path exported = diagnosticsService.exportDiagnosticsBundle(Paths.get(targetZipStr));
            return ResponseEntity.ok(Map.of("bundlePath", exported.toString()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
