package com.unityagent.product.service;

import com.unityagent.product.model.PlatformCapability;
import com.unityagent.unity.UnityConnection;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service combining system requirements, Unity installation detection, and platform build module capabilities.
 * Returns authoritative structured environment status.
 */
@Service
public class EnvironmentValidator {

    private final SystemRequirementsService requirementsService;
    private final InstallationDetector installationDetector;
    private final PlatformCapabilityDetector capabilityDetector;
    private final UnityConnection unityConnection;

    public EnvironmentValidator(SystemRequirementsService requirementsService,
                                InstallationDetector installationDetector,
                                PlatformCapabilityDetector capabilityDetector,
                                UnityConnection unityConnection) {
        this.requirementsService = requirementsService;
        this.installationDetector = installationDetector;
        this.capabilityDetector = capabilityDetector;
        this.unityConnection = unityConnection;
    }

    public Map<String, Object> validateEnvironment(String projectId, Path workingDirectory) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. Backend host readiness
        SystemRequirementsService.RequirementsReport req = requirementsService.checkRequirements(workingDirectory);
        result.put("backend", req.satisfied() ? "READY" : "NOT_SUPPORTED");
        result.put("backendDetails", requirementsService.toStatusMap(req));

        // 2. Unity Bridge and Installation status
        boolean unityConnected = unityConnection != null && unityConnection.isReady();
        var installs = installationDetector.detectInstallations();

        String unityStatus;
        if (unityConnected) {
            unityStatus = "CONNECTED";
        } else if (!installs.isEmpty()) {
            unityStatus = "INSTALLED_AWAITING_CONNECTION";
        } else {
            unityStatus = "NOT_DETECTED";
        }
        result.put("unity", unityStatus);
        result.put("unityConnected", unityConnected);
        result.put("detectedUnityVersions", installs.stream().map(InstallationDetector.UnityInstallation::version).toList());

        // 3. Current Host Platform
        String os = System.getProperty("os.name", "").toLowerCase();
        String currentPlatform = os.contains("win") ? "WINDOWS" :
                os.contains("mac") || os.contains("darwin") ? "OSX" : "LINUX";
        result.put("platform", currentPlatform);

        // 4. Detected build target capabilities
        Map<String, Boolean> buildModules = new LinkedHashMap<>();
        if (projectId != null && !projectId.isBlank()) {
            Map<String, PlatformCapability> caps = capabilityDetector.detectCapabilities(projectId);
            caps.forEach((k, v) -> buildModules.put(k, v.isBuildAvailable()));
        } else {
            // Default capability detection
            buildModules.put(currentPlatform, true);
        }
        result.put("buildModules", buildModules);

        return result;
    }
}
