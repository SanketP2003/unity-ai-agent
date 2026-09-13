package com.unityagent.agent.model;

/**
 * Token usage metadata for an LLM response.
 */
public record UsageMetadata(int promptTokens, int completionTokens, int totalTokens) {

    public static UsageMetadata of(int promptTokens, int completionTokens, int totalTokens) {
        return new UsageMetadata(promptTokens, completionTokens, totalTokens);
    }

    public static UsageMetadata empty() {
        return new UsageMetadata(0, 0, 0);
    }
}
