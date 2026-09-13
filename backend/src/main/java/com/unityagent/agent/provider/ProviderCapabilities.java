package com.unityagent.agent.provider;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Declares the capabilities supported by an AI model/provider.
 * Used by the AgentLoop to determine whether the configured model supports
 * autonomous tool calling, structured outputs, streaming, and vision.
 */
public class ProviderCapabilities {

    @JsonProperty("toolCalling")
    private final boolean toolCalling;

    @JsonProperty("structuredOutput")
    private final boolean structuredOutput;

    @JsonProperty("streaming")
    private final boolean streaming;

    @JsonProperty("vision")
    private final boolean vision;

    public ProviderCapabilities(
            @JsonProperty("toolCalling") boolean toolCalling,
            @JsonProperty("structuredOutput") boolean structuredOutput,
            @JsonProperty("streaming") boolean streaming,
            @JsonProperty("vision") boolean vision) {
        this.toolCalling = toolCalling;
        this.structuredOutput = structuredOutput;
        this.streaming = streaming;
        this.vision = vision;
    }

    public static ProviderCapabilities defaults() {
        return new ProviderCapabilities(true, true, true, false);
    }

    public static ProviderCapabilities openaiDefault() {
        return new ProviderCapabilities(true, true, true, true);
    }

    public static ProviderCapabilities compatibleDefault() {
        return new ProviderCapabilities(true, false, true, false);
    }

    public static ProviderCapabilities none() {
        return new ProviderCapabilities(false, false, false, false);
    }

    public static ProviderCapabilities custom(boolean toolCalling, boolean structuredOutput, boolean streaming, boolean vision) {
        return new ProviderCapabilities(toolCalling, structuredOutput, streaming, vision);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean toolCalling = true;
        private boolean structuredOutput = false;
        private boolean streaming = true;
        private boolean vision = false;

        public Builder supportsToolCalling(boolean val) { this.toolCalling = val; return this; }
        public Builder supportsStructuredOutput(boolean val) { this.structuredOutput = val; return this; }
        public Builder supportsStreaming(boolean val) { this.streaming = val; return this; }
        public Builder supportsVision(boolean val) { this.vision = val; return this; }

        public ProviderCapabilities build() {
            return new ProviderCapabilities(toolCalling, structuredOutput, streaming, vision);
        }
    }

    public boolean isToolCalling() {
        return toolCalling;
    }

    public boolean supportsToolCalling() {
        return toolCalling;
    }

    public boolean isStructuredOutput() {
        return structuredOutput;
    }

    public boolean supportsStructuredOutput() {
        return structuredOutput;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public boolean supportsStreaming() {
        return streaming;
    }

    public boolean isVision() {
        return vision;
    }

    public boolean supportsVision() {
        return vision;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProviderCapabilities that = (ProviderCapabilities) o;
        return toolCalling == that.toolCalling &&
                structuredOutput == that.structuredOutput &&
                streaming == that.streaming &&
                vision == that.vision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolCalling, structuredOutput, streaming, vision);
    }

    @Override
    public String toString() {
        return "ProviderCapabilities{" +
                "toolCalling=" + toolCalling +
                ", structuredOutput=" + structuredOutput +
                ", streaming=" + streaming +
                ", vision=" + vision +
                '}';
    }
}
