package com.unityagent.agent.provider.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.model.AgentCompletion;
import com.unityagent.agent.model.AgentPrompt;
import com.unityagent.agent.model.ErrorType;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.provider.AIProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * AIProvider implementation for OpenAI-compatible LLM endpoints
 * (e.g. NVIDIA Nemotron/NIM, OpenRouter, Groq, Together, Ollama, vLLM, etc.).
 *
 * <p>Security:
 * Does NOT print or leak the API key in logs or exceptions.
 * Reports {@code isConfigured() == false} if required properties are missing,
 * allowing the backend to start and run without errors.
 */
import com.unityagent.agent.provider.ProviderCapabilities;

@Component
public class OpenAICompatibleProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleProvider.class);

    private volatile String baseUrl;
    private volatile String apiKey;
    private volatile String model;
    private volatile ProviderCapabilities capabilities = ProviderCapabilities.compatibleDefault();
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final OpenAIToolMapper toolMapper;

    @Autowired
    public OpenAICompatibleProvider(
            @Value("${agent.ai.base-url:}") String baseUrl,
            @Value("${agent.ai.api-key:}") String apiKey,
            @Value("${agent.ai.model:}") String model,
            @Value("${agent.ai.timeout-seconds:120}") int timeoutSeconds,
            @Autowired(required = false) ObjectMapper mapper) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null ? model.trim() : "";
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 120;
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.toolMapper = new OpenAIToolMapper(this.mapper);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        log.info("OpenAICompatibleProvider initialized with model: '{}', endpoint: '{}', apiKeyConfigured: {}",
                this.model, this.baseUrl, !this.apiKey.isBlank());
    }

    /**
     * Testing constructor allowing custom HttpClient and ObjectMapper.
     */
    public OpenAICompatibleProvider(String baseUrl, String apiKey, String model, int timeoutSeconds,
                                    HttpClient httpClient, ObjectMapper mapper) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null ? model.trim() : "";
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.toolMapper = new OpenAIToolMapper(this.mapper);
        this.httpClient = httpClient;
    }

    @Override
    public String getProviderName() {
        return "openai-compatible";
    }

    @Override
    public boolean isConfigured() {
        return !baseUrl.isBlank() && !model.isBlank() && !apiKey.isBlank();
    }

    @Override
    public AgentCompletion generate(AgentPrompt prompt) throws AIProviderException {
        if (baseUrl.isBlank()) {
            throw new AIProviderException(
                    "AI provider 'openai-compatible' is not configured: base URL is missing (AI_BASE_URL).",
                    ErrorType.PROVIDER_ERROR,
                    400
            );
        }
        if (model.isBlank()) {
            throw new AIProviderException(
                    "AI provider 'openai-compatible' is not configured: model is missing (AI_MODEL).",
                    ErrorType.PROVIDER_ERROR,
                    400
            );
        }
        if (apiKey.isBlank()) {
            throw new AIProviderException(
                    "AI provider 'openai-compatible' is not configured: API key is missing (AI_API_KEY).",
                    ErrorType.PROVIDER_ERROR,
                    401
            );
        }

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return executeRequest(prompt);
            } catch (AIProviderException e) {
                if (attempt == maxAttempts || Integer.valueOf(401).equals(e.getHttpStatus()) || (e.getMessage() != null && e.getMessage().contains("not configured"))) {
                    throw e;
                }
                log.warn("Attempt {}/{} to OpenAI-compatible provider failed (transient): {}. Retrying in 1.5s...",
                        attempt, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw new AIProviderException("Failed after " + maxAttempts + " attempts", ErrorType.PROVIDER_ERROR, 500);
    }

    private AgentCompletion executeRequest(AgentPrompt prompt) throws AIProviderException {
        try {
            URI endpoint = resolveEndpoint(baseUrl);
            Map<String, Object> requestBodyMap = toolMapper.toRequestBody(model, prompt);
            String requestJson = mapper.writeValueAsString(requestBodyMap);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson));

            if (!apiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            log.debug("Sending OpenAI-compatible request to {} for model '{}' ({} messages, {} tools)",
                    endpoint, model, prompt.getMessages().size(), prompt.getTools().size());

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String responseBody = response.body();

            if (status == 401) {
                log.error("Authentication failed for OpenAI-compatible endpoint (401)");
                throw new AIProviderException(
                        "Authentication failed for OpenAI-compatible endpoint (401): Please verify AI_API_KEY.",
                        ErrorType.PROVIDER_ERROR,
                        401
                );
            }

            if (status == 429) {
                log.warn("Rate limit exceeded for OpenAI-compatible endpoint (429)");
                throw new AIProviderException(
                        "Rate limit exceeded on OpenAI-compatible endpoint (429): " + responseBody,
                        ErrorType.PROVIDER_ERROR,
                        429
                );
            }

            if (status == 400) {
                String bodyLower = responseBody != null ? responseBody.toLowerCase() : "";
                if (bodyLower.contains("tool") && (bodyLower.contains("not supported") || bodyLower.contains("unsupported") || bodyLower.contains("failed to parse"))) {
                    log.error("Model '{}' does not support compatible tool calling: {}", model, responseBody);
                    throw new AIProviderException(
                            "Selected model does not provide compatible tool-calling support: " + responseBody,
                            ErrorType.PROVIDER_ERROR,
                            400
                    );
                }
                throw new AIProviderException(
                        "Bad request to OpenAI-compatible endpoint (400): " + responseBody,
                        ErrorType.PROVIDER_ERROR,
                        400
                );
            }

            if (status >= 400) {
                log.error("OpenAI-compatible endpoint returned error {}: {}", status, responseBody);
                throw new AIProviderException(
                        "OpenAI-compatible endpoint returned error (" + status + "): " + responseBody,
                        ErrorType.PROVIDER_ERROR,
                        status
                );
            }

            if (responseBody == null || responseBody.isBlank()) {
                throw new AIProviderException(
                        "Malformed or empty response from OpenAI-compatible endpoint",
                        ErrorType.PROVIDER_ERROR,
                        502
                );
            }

            AgentCompletion completion = toolMapper.parseResponseBody(responseBody);
            log.debug("OpenAI-compatible completion received: hasToolCalls={}, finishReason={}",
                    completion.hasToolCalls(), completion.getFinishReason());
            return completion;

        } catch (AIProviderException e) {
            throw e;
        } catch (HttpTimeoutException | TimeoutException e) {
            log.error("Request to OpenAI-compatible endpoint timed out after {}s", timeoutSeconds);
            throw new AIProviderException("Request to OpenAI-compatible endpoint timed out after " + timeoutSeconds + "s", ErrorType.TIMEOUT, 408);
        } catch (ConnectException e) {
            log.error("Failed to connect to OpenAI-compatible endpoint: {}", e.getMessage());
            throw new AIProviderException("OpenAI-compatible provider unavailable (connection refused): " + e.getMessage(), ErrorType.PROVIDER_ERROR, 503);
        } catch (Exception e) {
            log.error("OpenAI-compatible completion failed: {}", e.getMessage(), e);
            throw new AIProviderException("Failed to generate OpenAI-compatible completion: " + e.getMessage(), e);
        }
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        return capabilities != null ? capabilities : ProviderCapabilities.compatibleDefault();
    }

    public void setCapabilities(ProviderCapabilities capabilities) {
        this.capabilities = capabilities != null ? capabilities : ProviderCapabilities.compatibleDefault();
    }

    @Override
    public void validateConfiguration() throws AIProviderException {
        if (baseUrl.isBlank()) {
            throw new AIProviderException("Base URL is missing (AI_BASE_URL).", ErrorType.PROVIDER_ERROR, 400);
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new AIProviderException("Base URL must start with http:// or https://", ErrorType.PROVIDER_ERROR, 400);
        }
        if (model.isBlank()) {
            throw new AIProviderException("Model is missing (AI_MODEL).", ErrorType.PROVIDER_ERROR, 400);
        }
        if (apiKey.isBlank()) {
            throw new AIProviderException("API key is missing (AI_API_KEY).", ErrorType.PROVIDER_ERROR, 401);
        }
    }

    public synchronized void updateConfig(String baseUrl, String apiKey, String model) {
        updateConfig(baseUrl, apiKey, model, null);
    }

    public synchronized void updateConfig(String baseUrl, String apiKey, String model, ProviderCapabilities capabilities) {
        if (baseUrl != null && !baseUrl.isBlank()) this.baseUrl = baseUrl.trim();
        if (apiKey != null && !apiKey.isBlank()) this.apiKey = apiKey.trim();
        if (model != null && !model.isBlank()) this.model = model.trim();
        if (capabilities != null) this.capabilities = capabilities;
        log.info("OpenAICompatibleProvider configuration updated: model='{}', endpoint='{}', configured={}",
                this.model, this.baseUrl, isConfigured());
    }

    public OpenAIToolMapper getToolMapper() {
        return toolMapper;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    @Override
    public String getModelName() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }

    private URI resolveEndpoint(String base) {
        String trimmed = base.trim();
        if (trimmed.endsWith("/chat/completions")) {
            return URI.create(trimmed);
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return URI.create(trimmed + "/chat/completions");
    }
}
