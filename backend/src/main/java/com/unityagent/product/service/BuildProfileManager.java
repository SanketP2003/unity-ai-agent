package com.unityagent.product.service;

import com.unityagent.product.model.BuildProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service managing reusable build profiles and validating target platform support.
 */
@Service
public class BuildProfileManager {

    private static final Logger log = LoggerFactory.getLogger(BuildProfileManager.class);
    private final PlatformCapabilityDetector capabilityDetector;
    private final Map<String, BuildProfile> defaultProfiles = new LinkedHashMap<>();

    public BuildProfileManager(PlatformCapabilityDetector capabilityDetector) {
        this.capabilityDetector = capabilityDetector;
        initDefaultProfiles();
    }

    private void initDefaultProfiles() {
        defaultProfiles.put("Windows Development", new BuildProfile(
                "Windows Development", "WINDOWS", "x64", true, "Default", "Mono",
                List.of("Assets/Scenes/MainScene.unity"), "Builds/Windows"
        ));
        defaultProfiles.put("Windows Release", new BuildProfile(
                "Windows Release", "WINDOWS", "x64", false, "LZ4HC", "IL2CPP",
                List.of("Assets/Scenes/MainScene.unity"), "Builds/Windows"
        ));
        defaultProfiles.put("Linux Release", new BuildProfile(
                "Linux Release", "LINUX", "x64", false, "LZ4HC", "Mono",
                List.of("Assets/Scenes/MainScene.unity"), "Builds/Linux"
        ));
        defaultProfiles.put("Android Release", new BuildProfile(
                "Android Release", "ANDROID", "ARM64", false, "LZ4", "IL2CPP",
                List.of("Assets/Scenes/MainScene.unity"), "Builds/Android"
        ));
        defaultProfiles.put("WebGL Release", new BuildProfile(
                "WebGL Release", "WEBGL", "Wasm", false, "Gzip", "IL2CPP",
                List.of("Assets/Scenes/MainScene.unity"), "Builds/WebGL"
        ));
    }

    public List<BuildProfile> getAvailableProfiles() {
        return new ArrayList<>(defaultProfiles.values());
    }

    public Optional<BuildProfile> getProfile(String name) {
        return Optional.ofNullable(defaultProfiles.get(name));
    }

    /**
     * Validates a build profile against detected platform capabilities.
     * Rejects build if platform module is missing.
     */
    public void validateProfile(String projectId, BuildProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("BUILD_PROFILE_INVALID: Profile cannot be null");
        }
        if (profile.getPlatform() == null || profile.getPlatform().isBlank()) {
            throw new IllegalArgumentException("BUILD_PROFILE_INVALID: Target platform must be specified");
        }
        if (profile.getScenes() == null || profile.getScenes().isEmpty()) {
            throw new IllegalArgumentException("BUILD_PROFILE_INVALID: At least one scene must be included in the build");
        }

        boolean available = capabilityDetector.isPlatformBuildAvailable(projectId, profile.getPlatform());
        if (!available) {
            throw new IllegalStateException("BUILD_PROFILE_INVALID: Platform " + profile.getPlatform() +
                    " does not have the required Unity build module installed or available on this system.");
        }
        log.info("Build profile '{}' validated for project {}", profile.getProfileName(), projectId);
    }
}
