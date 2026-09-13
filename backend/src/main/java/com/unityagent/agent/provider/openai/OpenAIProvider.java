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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * OpenAI implementation of AIProvider.
 * Uses Java 21's standard HttpClient and Jackson for zero extra dependencies.
 *
 * <p>API key security:
 * The key is resolved from {@code OPENAI_API_KEY} environment variable or local config.
 * If unset, the provider reports {@code isConfigured() == false}, allowing the backend
 * and Unity bridge to function completely normally.
 */
import com.unityagent.agent.provider.ProviderCapabilities;

@Component
public class OpenAIProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAIProvider.class);

    private volatile String apiKey;
    private volatile String model;
    private volatile String baseUrl;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final OpenAIToolMapper toolMapper;

    @Autowired
    public OpenAIProvider(
            @Value("${agent.openai.api-key:}") String apiKey,
            @Value("${agent.openai.model:gpt-4o}") String model,
            @Value("${agent.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${agent.openai.timeout-seconds:60}") int timeoutSeconds,
            @Autowired(required = false) ObjectMapper mapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null ? model.trim() : "";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "https://api.openai.com/v1";
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.toolMapper = new OpenAIToolMapper(this.mapper);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        if (isConfigured()) {
            log.info("OpenAIProvider initialized with model: {} (endpoint: {})", this.model, this.baseUrl);
        } else {
            log.info("OpenAIProvider initialized without API key. LLM requests will report unconfigured.");
        }
    }

    /**
     * Testing constructor allowing custom HttpClient.
     */
    public OpenAIProvider(String apiKey, String model, String baseUrl, int timeoutSeconds, HttpClient httpClient, ObjectMapper mapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "gpt-4o";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "https://api.openai.com/v1";
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.toolMapper = new OpenAIToolMapper(this.mapper);
        this.httpClient = httpClient;
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public AgentCompletion generate(AgentPrompt prompt) throws AIProviderException {
        if (!isConfigured()) {
            throw new AIProviderException(
                    "AI provider is not configured: OPENAI_API_KEY environment variable is missing.",
                    ErrorType.PROVIDER_ERROR,
                    401
            );
        }

        try {
            Map<String, Object> requestBodyMap = toolMapper.toRequestBody(model, prompt);
            String requestJson = mapper.writeValueAsString(requestBodyMap);

            URI endpoint = URI.create(baseUrl + "/chat/completions");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            log.debug("Sending OpenAI chat completion request with {} messages and {} tools",
                    prompt.getMessages().size(), prompt.getTools().size());

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("OpenAI API returned error status {}: {}", response.statusCode(), response.body());
                throw new AIProviderException(
                        "OpenAI API returned error (" + response.statusCode() + "): " + response.body(),
                        ErrorType.PROVIDER_ERROR,
                        response.statusCode()
                );
            }

            AgentCompletion completion = toolMapper.parseResponseBody(response.body());
            log.debug("OpenAI completed: hasToolCalls={}, finishReason={}",
                    completion.hasToolCalls(), completion.getFinishReason());
            return completion;

        } catch (AIProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI request failed: {}", e.getMessage(), e);
            throw new AIProviderException("Failed to generate OpenAI completion: " + e.getMessage(), e);
        }
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        return ProviderCapabilities.openaiDefault();
    }

    @Override
    public void validateConfiguration() throws AIProviderException {
        if (apiKey.isBlank()) {
            throw new AIProviderException("OpenAI API key is missing (OPENAI_API_KEY).", ErrorType.PROVIDER_ERROR, 401);
        }
        if (model.isBlank()) {
            throw new AIProviderException("Model name is required (OPENAI_MODEL).", ErrorType.PROVIDER_ERROR, 400);
        }
        if (!baseUrl.isBlank() && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new AIProviderException("Base URL must start with http:// or https://", ErrorType.PROVIDER_ERROR, 400);
        }
    }

    public synchronized void updateConfig(String apiKey, String model, String baseUrl) {
        if (apiKey != null && !apiKey.isBlank()) this.apiKey = apiKey.trim();
        if (model != null && !model.isBlank()) this.model = model.trim();
        if (baseUrl != null && !baseUrl.isBlank()) this.baseUrl = baseUrl.trim();
        log.info("OpenAIProvider configuration updated: model='{}', baseUrl='{}', configured={}",
                this.model, this.baseUrl, isConfigured());
    }

    public OpenAIToolMapper getToolMapper() {
        return toolMapper;
    }

    public String getModel() {
        return model;
    }

    @Override
    public String getModelName() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }
}
