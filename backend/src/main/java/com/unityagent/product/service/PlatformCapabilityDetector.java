package com.unityagent.product.service;

import com.unityagent.product.model.PlatformCapability;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Detects real Unity build platform capabilities and installed playback engines.
 * Prevents attempting builds for platforms without installed Unity modules.
 */
@Service
public class PlatformCapabilityDetector {

    private static final Logger log = LoggerFactory.getLogger(PlatformCapabilityDetector.class);
    private final UnityConnection unityConnection;

    // Standard platforms
    public static final String PLATFORM_WINDOWS = "WINDOWS";
    public static final String PLATFORM_LINUX = "LINUX";
    public static final String PLATFORM_ANDROID = "ANDROID";
    public static final String PLATFORM_WEBGL = "WEBGL";
    public static final String PLATFORM_OSX = "OSX";
    public static final String PLATFORM_IOS = "IOS";

    public PlatformCapabilityDetector(UnityConnection unityConnection) {
        this.unityConnection = unityConnection;
    }

    /**
     * Inspects actual Unity build capabilities for a given project.
     */
    public Map<String, PlatformCapability> detectCapabilities(String projectId) {
        Map<String, PlatformCapability> capabilities = new LinkedHashMap<>();

        // 1. Try querying live Unity connection if ready
        if (unityConnection != null && unityConnection.isReady()) {
            try {
                UnityMessage req = UnityMessage.toolRequest(UUID.randomUUID().toString(), "get_platform_capabilities", Map.of());
                UnityMessage resp = unityConnection.sendToolRequest(projectId, req);
                if (resp != null && Boolean.TRUE.equals(resp.getSuccess()) && resp.getData() != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> list = (List<Map<String, Object>>) resp.getData().get("capabilities");
                    if (list != null) {
                        for (Map<String, Object> item : list) {
                            String p = (String) item.get("platform");
                            boolean supp = Boolean.TRUE.equals(item.get("supported"));
                            boolean mod = Boolean.TRUE.equals(item.get("moduleInstalled"));
                            boolean avail = Boolean.TRUE.equals(item.get("buildAvailable"));
                            capabilities.put(p.toUpperCase(), new PlatformCapability(p.toUpperCase(), supp, mod, avail));
                        }
                        return capabilities;
                    }
                }
            } catch (Exception e) {
                log.debug("Live platform capability query returned: {}", e.getMessage());
            }
        }

        // 2. Local environment heuristic fallback
        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean isWindowsHost = osName.contains("win");
        boolean isLinuxHost = osName.contains("linux");
        boolean isMacHost = osName.contains("mac");

        // Host platform is always supported and installed
        capabilities.put(PLATFORM_WINDOWS, new PlatformCapability(PLATFORM_WINDOWS, true, isWindowsHost, isWindowsHost));
        capabilities.put(PLATFORM_LINUX, new PlatformCapability(PLATFORM_LINUX, true, isLinuxHost, isLinuxHost));
        capabilities.put(PLATFORM_OSX, new PlatformCapability(PLATFORM_OSX, true, isMacHost, isMacHost));

        // Additional modules: check if Unity Editor PlaybackEngines directory exists
        boolean webglInstalled = checkPlaybackEngineExists("WebGLSupport");
        capabilities.put(PLATFORM_WEBGL, new PlatformCapability(PLATFORM_WEBGL, true, webglInstalled, webglInstalled));

        boolean androidInstalled = checkPlaybackEngineExists("AndroidPlayer");
        capabilities.put(PLATFORM_ANDROID, new PlatformCapability(PLATFORM_ANDROID, true, androidInstalled, androidInstalled));

        boolean iosInstalled = checkPlaybackEngineExists("iOSSupport");
        capabilities.put(PLATFORM_IOS, new PlatformCapability(PLATFORM_IOS, isMacHost, iosInstalled, isMacHost && iosInstalled));

        return capabilities;
    }

    public boolean isPlatformBuildAvailable(String projectId, String platform) {
        if (platform == null) return false;
        String normalized = platform.toUpperCase().replace("STANDALONE", "").replace("64", "").trim();
        Map<String, PlatformCapability> caps = detectCapabilities(projectId);
        PlatformCapability cap = caps.get(normalized);
        return cap != null && cap.isBuildAvailable();
    }

    private boolean checkPlaybackEngineExists(String engineSubdir) {
        // Look in standard Unity install locations
        List<String> searchPaths = List.of(
                "C:/Program Files/Unity/Hub/Editor",
                "C:/Program Files/Unity/Editor/Data/PlaybackEngines",
                "/Applications/Unity/Hub/Editor",
                "/opt/unity/editor"
        );
        for (String sp : searchPaths) {
            Path p = Paths.get(sp);
            if (Files.exists(p)) {
                // If PlaybackEngines directory contains the engineSubdir
                if (Files.exists(p.resolve(engineSubdir)) || Files.exists(p.resolve("Data/PlaybackEngines/" + engineSubdir))) {
                    return true;
                }
            }
        }
        return false;
    }
}
