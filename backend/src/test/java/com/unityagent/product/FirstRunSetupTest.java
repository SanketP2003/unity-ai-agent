package com.unityagent.product;

import com.unityagent.agent.provider.openai.OpenAICompatibleProvider;
import com.unityagent.product.model.ProviderProfile;
import com.unityagent.product.service.FirstRunSetupService;
import com.unityagent.product.service.ProviderProfileManager;
import com.unityagent.product.service.SystemRequirementsService;
import com.unityagent.unity.UnityConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FirstRunSetupTest {

    private SystemRequirementsService systemRequirementsService;
    private ProviderProfileManager profileManager;
    private UnityConnection unityConnection;
    private OpenAICompatibleProvider openAICompatibleProvider;
    private HttpClient mockHttpClient;
    private FirstRunSetupService setupService;

    @BeforeEach
    void setUp() {
        systemRequirementsService = mock(SystemRequirementsService.class);
        profileManager = mock(ProviderProfileManager.class);
        unityConnection = mock(UnityConnection.class);
        openAICompatibleProvider = mock(OpenAICompatibleProvider.class);
        mockHttpClient = mock(HttpClient.class);

        setupService = new FirstRunSetupService(
                systemRequirementsService,
                profileManager,
                unityConnection,
                openAICompatibleProvider,
                mockHttpClient
        );
    }

    @Test
    void testSetupStatusWhenUnconfigured() {
        when(systemRequirementsService.checkRequirements(null)).thenReturn(
                new SystemRequirementsService.RequirementsReport(true, 21, 1024L * 1024L * 1024L, 50L * 1024L * 1024L * 1024L, true, true, "OK")
        );
        when(profileManager.getProfile("nvidia")).thenReturn(
                Optional.of(new ProviderProfile("nvidia", "NVIDIA", "openai-compatible", "https://api.nvidia.com/v1", "model"))
        );
        when(profileManager.resolveExternalApiKey("nvidia")).thenReturn(Optional.empty());
        when(unityConnection.isReady()).thenReturn(false);
        when(unityConnection.getState()).thenReturn(UnityConnection.ConnectionState.DISCONNECTED);
        when(unityConnection.getConnectedProjects()).thenReturn(Collections.emptyList());

        FirstRunSetupService.SetupStatus status = setupService.getSetupStatus();

        assertNotNull(status);
        assertEquals(FirstRunSetupService.ProviderState.UNCONFIGURED, status.providerState());
        assertEquals(FirstRunSetupService.UnityState.NOT_CONNECTED, status.unityState());
        assertEquals(FirstRunSetupService.ReadinessState.NOT_READY, status.readinessState());
        assertFalse(status.missingPrerequisites().isEmpty());
    }

    @Test
    void testSetupStatusWhenReady() {
        when(systemRequirementsService.checkRequirements(null)).thenReturn(
                new SystemRequirementsService.RequirementsReport(true, 21, 2048L * 1024L * 1024L, 100L * 1024L * 1024L * 1024L, true, true, "OK")
        );
        when(profileManager.getProfile("nvidia")).thenReturn(
                Optional.of(new ProviderProfile("nvidia", "NVIDIA", "openai-compatible", "https://api.nvidia.com/v1", "model"))
        );
        when(profileManager.resolveExternalApiKey("nvidia")).thenReturn(Optional.of("test-secret-key"));
        when(unityConnection.isReady()).thenReturn(true);
        when(unityConnection.getState()).thenReturn(UnityConnection.ConnectionState.READY);
        when(unityConnection.getConnectedProjects()).thenReturn(List.of(mock(UnityConnection.ProjectConnectionInfo.class)));

        setupService.setVerificationStateForTesting(FirstRunSetupService.ProviderState.VERIFIED, "Ping OK");

        FirstRunSetupService.SetupStatus status = setupService.getSetupStatus();

        assertNotNull(status);
        assertEquals(FirstRunSetupService.ProviderState.VERIFIED, status.providerState());
        assertEquals(FirstRunSetupService.UnityState.CONNECTED, status.unityState());
        assertEquals(FirstRunSetupService.ReadinessState.READY_FOR_AUTONOMOUS_RUN, status.readinessState());
        assertTrue(status.missingPrerequisites().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProviderPingSuccess() throws Exception {
        when(profileManager.getProfile("nvidia")).thenReturn(
                Optional.of(new ProviderProfile("nvidia", "NVIDIA", "openai-compatible", "https://api.nvidia.com/v1", "model"))
        );
        when(profileManager.resolveExternalApiKey("nvidia")).thenReturn(Optional.of("valid-key"));

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        FirstRunSetupService.ProviderTestResult result = setupService.testProviderConnection("nvidia", null);

        assertTrue(result.success());
        assertEquals(200, result.statusCode());
        assertTrue(result.message().contains("successfully verified"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProviderPingAuthFailure() throws Exception {
        when(profileManager.getProfile("nvidia")).thenReturn(
                Optional.of(new ProviderProfile("nvidia", "NVIDIA", "openai-compatible", "https://api.nvidia.com/v1", "model"))
        );
        when(profileManager.resolveExternalApiKey("nvidia")).thenReturn(Optional.of("invalid-key"));

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        FirstRunSetupService.ProviderTestResult result = setupService.testProviderConnection("nvidia", null);

        assertFalse(result.success());
        assertEquals(401, result.statusCode());
        assertTrue(result.message().contains("Authentication failed"));
    }

    @Test
    void testSecretScrubbingInConnectionError() throws Exception {
        when(profileManager.getProfile("nvidia")).thenReturn(
                Optional.of(new ProviderProfile("nvidia", "NVIDIA", "openai-compatible", "https://api.nvidia.com/v1", "model"))
        );
        when(profileManager.resolveExternalApiKey("nvidia")).thenReturn(Optional.of("secret-token-xyz"));

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("Connection failed for Bearer secret-token-xyz"));

        FirstRunSetupService.ProviderTestResult result = setupService.testProviderConnection("nvidia", null);

        assertFalse(result.success());
        assertFalse(result.message().contains("secret-token-xyz"));
        assertTrue(result.message().contains("[SCRUBBED]"));
    }
}
