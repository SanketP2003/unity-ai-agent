package com.unityagent.product.service;

import com.unityagent.agent.provider.openai.OpenAICompatibleProvider;
import com.unityagent.product.model.ProviderProfile;
import com.unityagent.unity.UnityConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Service managing First-Run Setup, environment readiness, and configuration wizard state.
 * Enforces zero-credential persistence: API keys are validated in-memory and never logged or persisted in plain text.
 */
@Service
public class FirstRunSetupService {

    private static final Logger log = LoggerFactory.getLogger(FirstRunSetupService.class);

    public enum ProviderState {
        UNCONFIGURED,
        CONFIGURED,
        VERIFIED,
        FAILED
    }

    public enum UnityState {
        NOT_CONNECTED,
        WAITING_FOR_UNITY,
        CONNECTED
    }

    public enum ReadinessState {
        NOT_READY,
        READY_FOR_AUTONOMOUS_RUN
    }

    public record SetupStatus(
            SystemRequirementsService.RequirementsReport systemCheck,
            ProviderState providerState,
            String activeProviderId,
            String activeProviderModel,
            String activeProviderEndpoint,
            boolean hasApiKey,
            UnityState unityState,
            int connectedProjectCount,
            ReadinessState readinessState,
            List<String> missingPrerequisites,
            String lastProviderMessage
    ) {}

    public record ProviderTestResult(
            boolean success,
            int statusCode,
            String message,
            long latencyMs
    ) {}

    private final SystemRequirementsService systemRequirementsService;
    private final ProviderProfileManager profileManager;
    private final UnityConnection unityConnection;
    private final OpenAICompatibleProvider openAICompatibleProvider;
    private final HttpClient httpClient;

    private volatile String activeProfileId = "nvidia";
    private volatile ProviderState lastProviderVerificationState = null;
    private volatile String lastProviderMessage = null;

    @Autowired
    public FirstRunSetupService(SystemRequirementsService systemRequirementsService,
                                ProviderProfileManager profileManager,
                                UnityConnection unityConnection,
                                OpenAICompatibleProvider openAICompatibleProvider) {
        this(systemRequirementsService, profileManager, unityConnection, openAICompatibleProvider,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public FirstRunSetupService(SystemRequirementsService systemRequirementsService,
                                ProviderProfileManager profileManager,
                                UnityConnection unityConnection,
                                OpenAICompatibleProvider openAICompatibleProvider,
                                HttpClient httpClient) {
        this.systemRequirementsService = systemRequirementsService;
        this.profileManager = profileManager;
        this.unityConnection = unityConnection;
        this.openAICompatibleProvider = openAICompatibleProvider;
        this.httpClient = httpClient;
    }

    public SetupStatus getSetupStatus() {
        SystemRequirementsService.RequirementsReport sysCheck = systemRequirementsService.checkRequirements(null);
        List<String> missing = new ArrayList<>();

        if (!sysCheck.satisfied()) {
            missing.add("System requirements not satisfied: " + sysCheck.details());
        }

        // Provider Status
        ProviderProfile profile = profileManager.getProfile(activeProfileId).orElse(null);
        boolean keyResolved = profileManager.resolveExternalApiKey(activeProfileId).isPresent()
                || (openAICompatibleProvider != null && openAICompatibleProvider.isConfigured());

        ProviderState provState = ProviderState.UNCONFIGURED;
        if (profile == null) {
            missing.add("No active AI provider profile configured");
        } else if (!keyResolved) {
            provState = ProviderState.UNCONFIGURED;
            missing.add("API key missing for provider: " + activeProfileId);
        } else {
            if (lastProviderVerificationState != null) {
                provState = lastProviderVerificationState;
                if (provState == ProviderState.FAILED) {
                    missing.add("AI provider verification failed: " + (lastProviderMessage != null ? lastProviderMessage : "endpoint unreachable"));
                }
            } else {
                provState = ProviderState.CONFIGURED;
            }
        }

        // Unity Status
        UnityState uState = UnityState.NOT_CONNECTED;
        int connectedProjects = 0;
        if (unityConnection != null) {
            connectedProjects = unityConnection.getConnectedProjects().size();
            if (unityConnection.isReady()) {
                uState = UnityState.CONNECTED;
            } else if (unityConnection.getState() == UnityConnection.ConnectionState.CONNECTING
                    || unityConnection.getState() == UnityConnection.ConnectionState.HANDSHAKING) {
                uState = UnityState.WAITING_FOR_UNITY;
            } else {
                uState = UnityState.NOT_CONNECTED;
            }
        }

        if (uState != UnityState.CONNECTED) {
            missing.add("Unity Editor bridge is not connected");
        }

        ReadinessState readiness = missing.isEmpty() ? ReadinessState.READY_FOR_AUTONOMOUS_RUN : ReadinessState.NOT_READY;

        String endpoint = profile != null ? profile.getBaseUrl() : (openAICompatibleProvider != null ? openAICompatibleProvider.getBaseUrl() : "");
        String model = profile != null ? profile.getDefaultModel() : (openAICompatibleProvider != null ? openAICompatibleProvider.getModel() : "");

        return new SetupStatus(
                sysCheck,
                provState,
                activeProfileId,
                model,
                endpoint,
                keyResolved,
                uState,
                connectedProjects,
                readiness,
                missing,
                lastProviderMessage
        );
    }

    public ProviderTestResult testProviderConnection(String profileId, String apiKeyOverride) {
        ProviderProfile profile = profileManager.getProfile(profileId).orElse(null);
        if (profile == null) {
            lastProviderVerificationState = ProviderState.FAILED;
            lastProviderMessage = "Profile not found: " + profileId;
            return new ProviderTestResult(false, 404, lastProviderMessage, 0);
        }

        String key = apiKeyOverride != null && !apiKeyOverride.isBlank()
                ? apiKeyOverride.trim()
                : profileManager.resolveExternalApiKey(profileId).orElse("");

        if (key.isBlank() && openAICompatibleProvider != null && openAICompatibleProvider.isConfigured()) {
            key = openAICompatibleProvider.getApiKey();
        }

        if (key.isBlank() && !profile.getBaseUrl().contains("localhost") && !profile.getBaseUrl().contains("127.0.0.1")) {
            lastProviderVerificationState = ProviderState.FAILED;
            lastProviderMessage = "Missing API key for remote endpoint: " + profile.getBaseUrl();
            return new ProviderTestResult(false, 401, lastProviderMessage, 0);
        }

        String endpointUrl = profile.getBaseUrl();
        if (endpointUrl.endsWith("/chat/completions")) {
            endpointUrl = endpointUrl.substring(0, endpointUrl.lastIndexOf("/chat/completions"));
        }
        if (endpointUrl.endsWith("/")) {
            endpointUrl = endpointUrl.substring(0, endpointUrl.length() - 1);
        }
        // Use /models or root ping for lightweight connectivity test
        String testUrl = endpointUrl.endsWith("/v1") ? endpointUrl + "/models" : endpointUrl + "/v1/models";

        long start = System.currentTimeMillis();
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(testUrl))
                    .timeout(Duration.ofSeconds(6))
                    .GET();

            if (!key.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + key);
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            // 200 OK or 404 (endpoint exists but /models not routed) or 400 with valid server response
            if (resp.statusCode() == 200 || resp.statusCode() == 404) {
                lastProviderVerificationState = ProviderState.VERIFIED;
                lastProviderMessage = "Provider successfully verified (HTTP " + resp.statusCode() + ", " + latency + "ms)";
                return new ProviderTestResult(true, resp.statusCode(), lastProviderMessage, latency);
            } else if (resp.statusCode() == 401) {
                lastProviderVerificationState = ProviderState.FAILED;
                lastProviderMessage = "Authentication failed (HTTP 401): Please verify API key.";
                return new ProviderTestResult(false, 401, lastProviderMessage, latency);
            } else {
                lastProviderVerificationState = ProviderState.FAILED;
                lastProviderMessage = "Provider returned HTTP " + resp.statusCode();
                return new ProviderTestResult(false, resp.statusCode(), lastProviderMessage, latency);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            lastProviderVerificationState = ProviderState.FAILED;
            String sanitized = scrubSecrets(e.getMessage());
            lastProviderMessage = "Connection failed: " + sanitized;
            return new ProviderTestResult(false, 503, lastProviderMessage, latency);
        }
    }

    public synchronized void selectActiveProfile(String profileId) {
        if (profileManager.getProfile(profileId).isPresent()) {
            this.activeProfileId = profileId;
            this.lastProviderVerificationState = null;
            this.lastProviderMessage = null;
        } else {
            throw new IllegalArgumentException("Unknown provider profile: " + profileId);
        }
    }

    public void setVerificationStateForTesting(ProviderState state, String message) {
        this.lastProviderVerificationState = state;
        this.lastProviderMessage = message;
    }

    private String scrubSecrets(String msg) {
        if (msg == null) return "Unknown error";
        return msg.replaceAll("Bearer\\s+([A-Za-z0-9_\\-\\.]+)", "Bearer [SCRUBBED]")
                .replaceAll("(?i)key=([A-Za-z0-9_\\-\\.]+)", "key=[SCRUBBED]");
    }
}
