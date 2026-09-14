package com.unityagent.product;

import com.unityagent.product.model.PlatformCapability;
import com.unityagent.product.service.EnvironmentValidator;
import com.unityagent.product.service.InstallationDetector;
import com.unityagent.product.service.PlatformCapabilityDetector;
import com.unityagent.product.service.SystemRequirementsService;
import com.unityagent.unity.UnityConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Phase 13.1 — Clean Environment Detection Tests")
class EnvironmentDetectionTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Verify host system requirements check passes on current JVM runtime")
    void testSystemRequirementsCheck() {
        SystemRequirementsService reqService = new SystemRequirementsService();
        SystemRequirementsService.RequirementsReport report = reqService.checkRequirements(tempDir);

        assertNotNull(report);
        assertTrue(report.javaVersion() >= 21, "Java version must be 21 or greater");
        assertTrue(report.maxMemoryBytes() > 0, "Max memory must be positive");
        assertTrue(report.freeDiskBytes() > 0, "Free disk space must be positive");
        assertTrue(report.filesystemWritable(), "Temp directory must be writable");
        assertTrue(report.sqliteSupported(), "SQLite JDBC must be supported");
        assertTrue(report.satisfied(), "All system requirements must be satisfied: " + report.details());
    }

    @Test
    @DisplayName("Verify environment validator outputs structured status conforming to spec")
    void testEnvironmentValidatorStructuredOutput() {
        SystemRequirementsService reqService = new SystemRequirementsService();
        InstallationDetector installDetector = Mockito.mock(InstallationDetector.class);
        PlatformCapabilityDetector capDetector = Mockito.mock(PlatformCapabilityDetector.class);
        UnityConnection unityConnection = Mockito.mock(UnityConnection.class);

        when(unityConnection.isReady()).thenReturn(true);
        when(capDetector.detectCapabilities("proj-1")).thenReturn(Map.of(
                "WINDOWS", new PlatformCapability("WINDOWS", true, true, true),
                "WEBGL", new PlatformCapability("WEBGL", true, true, true),
                "ANDROID", new PlatformCapability("ANDROID", false, false, false)
        ));

        EnvironmentValidator validator = new EnvironmentValidator(
                reqService, installDetector, capDetector, unityConnection
        );

        Map<String, Object> status = validator.validateEnvironment("proj-1", tempDir);

        assertNotNull(status);
        assertEquals("READY", status.get("backend"));
        assertEquals("CONNECTED", status.get("unity"));
        assertNotNull(status.get("platform"));

        @SuppressWarnings("unchecked")
        Map<String, Boolean> buildModules = (Map<String, Boolean>) status.get("buildModules");
        assertNotNull(buildModules);
        assertTrue(buildModules.get("WINDOWS"));
        assertTrue(buildModules.get("WEBGL"));
        assertFalse(buildModules.get("ANDROID"));
    }
}
