package com.unityagent.product;

import com.unityagent.product.model.BuildProfile;
import com.unityagent.product.model.PlatformCapability;
import com.unityagent.product.service.BuildProfileManager;
import com.unityagent.product.service.PlatformCapabilityDetector;
import com.unityagent.unity.UnityConnection;
import com.unityagent.unity.UnityMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class PlatformCapabilityTest {

    private UnityConnection unityConnection;
    private PlatformCapabilityDetector detector;
    private BuildProfileManager profileManager;

    @BeforeEach
    void setUp() {
        unityConnection = Mockito.mock(UnityConnection.class);
        detector = new PlatformCapabilityDetector(unityConnection);
        profileManager = new BuildProfileManager(detector);
    }

    @Test
    void testDetectCapabilitiesFromUnityBridge() throws Exception {
        when(unityConnection.isReady()).thenReturn(true);
        UnityMessage bridgeResponse = UnityMessage.toolResponse("op-1", true, Map.of(
                "capabilities", List.of(
                        Map.of("platform", "WINDOWS", "supported", true, "moduleInstalled", true, "buildAvailable", true),
                        Map.of("platform", "ANDROID", "supported", true, "moduleInstalled", false, "buildAvailable", false),
                        Map.of("platform", "WEBGL", "supported", true, "moduleInstalled", false, "buildAvailable", false)
                )
        ));
        when(unityConnection.sendToolRequest(eq("proj-cap-1"), any())).thenReturn(bridgeResponse);

        Map<String, PlatformCapability> caps = detector.detectCapabilities("proj-cap-1");
        assertNotNull(caps);
        assertTrue(caps.containsKey("WINDOWS"));
        assertTrue(caps.get("WINDOWS").isBuildAvailable());
        assertTrue(caps.get("WINDOWS").isModuleInstalled());

        assertTrue(caps.containsKey("ANDROID"));
        assertFalse(caps.get("ANDROID").isBuildAvailable());
        assertFalse(caps.get("ANDROID").isModuleInstalled());
    }

    @Test
    void testBuildProfileValidationRejectsMissingModule() throws Exception {
        when(unityConnection.isReady()).thenReturn(true);
        // Only WINDOWS is installed; ANDROID is NOT installed
        UnityMessage bridgeResponse = UnityMessage.toolResponse("op-2", true, Map.of(
                "capabilities", List.of(
                        Map.of("platform", "WINDOWS", "supported", true, "moduleInstalled", true, "buildAvailable", true),
                        Map.of("platform", "ANDROID", "supported", true, "moduleInstalled", false, "buildAvailable", false)
                )
        ));
        when(unityConnection.sendToolRequest(eq("proj-cap-1"), any())).thenReturn(bridgeResponse);

        // Windows profile should validate cleanly
        BuildProfile winProfile = profileManager.getProfile("Windows Release").orElseThrow();
        assertDoesNotThrow(() -> profileManager.validateProfile("proj-cap-1", winProfile));

        // Android profile should fail because module is not installed
        BuildProfile androidProfile = profileManager.getProfile("Android Release").orElseThrow();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            profileManager.validateProfile("proj-cap-1", androidProfile);
        });
        assertTrue(ex.getMessage().contains("BUILD_PROFILE_INVALID"));
        assertTrue(ex.getMessage().contains("ANDROID"));
    }
}
