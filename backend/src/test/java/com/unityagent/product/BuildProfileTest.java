package com.unityagent.product;

import com.unityagent.product.model.BuildProfile;
import com.unityagent.product.service.BuildProfileManager;
import com.unityagent.product.service.PlatformCapabilityDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Build Profile Management & Validation Tests")
class BuildProfileTest {

    private PlatformCapabilityDetector capabilityDetector;
    private BuildProfileManager profileManager;

    @BeforeEach
    void setUp() {
        capabilityDetector = Mockito.mock(PlatformCapabilityDetector.class);
        profileManager = new BuildProfileManager(capabilityDetector);
    }

    @Test
    @DisplayName("Verify default build profiles are available")
    void testDefaultProfilesAvailable() {
        List<BuildProfile> profiles = profileManager.getAvailableProfiles();
        assertFalse(profiles.isEmpty());
        assertTrue(profiles.stream().anyMatch(p -> "Windows Release".equals(p.getProfileName())));
        assertTrue(profiles.stream().anyMatch(p -> "Windows Development".equals(p.getProfileName())));
        assertTrue(profiles.stream().anyMatch(p -> "WebGL Release".equals(p.getProfileName())));
    }

    @Test
    @DisplayName("Verify profile validation succeeds when platform capability is present")
    void testValidateProfileSuccess() {
        when(capabilityDetector.isPlatformBuildAvailable("proj_1", "WINDOWS")).thenReturn(true);

        Optional<BuildProfile> opt = profileManager.getProfile("Windows Release");
        assertTrue(opt.isPresent());
        assertDoesNotThrow(() -> profileManager.validateProfile("proj_1", opt.get()));
    }

    @Test
    @DisplayName("Verify profile validation fails when platform capability is absent")
    void testValidateProfileUnavailablePlatform() {
        when(capabilityDetector.isPlatformBuildAvailable("proj_1", "ANDROID")).thenReturn(false);

        Optional<BuildProfile> opt = profileManager.getProfile("Android Release");
        assertTrue(opt.isPresent());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                profileManager.validateProfile("proj_1", opt.get()));
        assertTrue(ex.getMessage().contains("BUILD_PROFILE_INVALID"));
    }

    @Test
    @DisplayName("Verify profile validation fails when scenes list is empty")
    void testValidateProfileEmptyScenes() {
        BuildProfile invalidProfile = new BuildProfile(
                "Invalid", "WINDOWS", "x64", false, "Default", "Mono",
                Collections.emptyList(), "Builds/Invalid"
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                profileManager.validateProfile("proj_1", invalidProfile));
        assertTrue(ex.getMessage().contains("At least one scene must be included"));
    }
}
