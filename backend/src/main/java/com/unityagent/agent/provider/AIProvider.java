package com.unityagent.agent.provider;

import com.unityagent.agent.model.AgentCompletion;
import com.unityagent.agent.model.AgentPrompt;

import java.util.concurrent.CompletableFuture;

/**
 * Pluggable provider interface for LLM backends (OpenAI, Gemini, Local models, etc.).
 * The AgentLoop depends solely on this abstraction, never directly on a specific LLM vendor.
 */
public interface AIProvider {

    /**
     * @return unique provider identifier (e.g. "openai", "gemini", "mock")
     */
    String getProviderName();

    /**
     * @return true if the provider has all necessary configuration (e.g. valid API key)
     */
    boolean isConfigured();

    /**
     * Synchronously generate an LLM completion for the given prompt.
     *
     * @param prompt the conversation history, available tools, and parameters
     * @return the LLM response, containing assistant content and/or tool calls
     * @throws AIProviderException if the provider is not configured or execution fails
     */
    AgentCompletion generate(AgentPrompt prompt) throws AIProviderException;

    /**
     * Asynchronously generate an LLM completion.
     */
    default CompletableFuture<AgentCompletion> generateAsync(AgentPrompt prompt) {
        return CompletableFuture.supplyAsync(() -> generate(prompt));
    }

    /**
     * @return the capabilities supported by this provider/model
     */
    default ProviderCapabilities getCapabilities() {
        return ProviderCapabilities.compatibleDefault();
    }

    /**
     * Validates that the provider configuration is valid and complete.
     *
     * @throws AIProviderException if configuration is missing or invalid
     */
    default void validateConfiguration() throws AIProviderException {
        if (!isConfigured()) {
            throw new AIProviderException("Provider '" + getProviderName() + "' is not properly configured.",
                    com.unityagent.agent.model.ErrorType.PROVIDER_ERROR, 400);
        }
    }

    /**
     * @return active model name (e.g. "gpt-4o", "llama-3.1-70b"), or empty string if not applicable
     */
    default String getModelName() {
        return "";
    }
}
