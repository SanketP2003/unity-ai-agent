package com.unityagent.agent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provider-neutral completion returned by an AIProvider.
 */
public class AgentCompletion {

    private final String content;
    private final String reasoningContent;
    private final List<ToolCall> toolCalls;
    private final String finishReason;
    private final UsageMetadata usage;

    public AgentCompletion(String content, String reasoningContent, List<ToolCall> toolCalls, String finishReason, UsageMetadata usage) {
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.toolCalls = toolCalls != null ? Collections.unmodifiableList(new ArrayList<>(toolCalls)) : List.of();
        this.finishReason = finishReason != null ? finishReason : "stop";
        this.usage = usage != null ? usage : UsageMetadata.empty();
    }

    public AgentCompletion(String content, List<ToolCall> toolCalls, String finishReason, UsageMetadata usage) {
        this(content, null, toolCalls, finishReason, usage);
    }

    public static AgentCompletion text(String content) {
        return new AgentCompletion(content, null, List.of(), "stop", UsageMetadata.empty());
    }

    public static AgentCompletion toolCalls(List<ToolCall> toolCalls) {
        return new AgentCompletion(null, null, toolCalls, "tool_calls", UsageMetadata.empty());
    }

    public static AgentCompletion toolCalls(String content, List<ToolCall> toolCalls) {
        return new AgentCompletion(content, null, toolCalls, "tool_calls", UsageMetadata.empty());
    }

    public String getContent() {
        return content;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public boolean hasReasoningContent() {
        return reasoningContent != null && !reasoningContent.isBlank();
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public String getFinishReason() {
        return finishReason;
    }

    public UsageMetadata getUsage() {
        return usage;
    }

    @Override
    public String toString() {
        return "AgentCompletion{" +
                "content='" + (content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
                ", reasoning=" + (reasoningContent != null ? "yes" : "no") +
                ", toolCalls=" + toolCalls.size() +
                ", finishReason='" + finishReason + '\'' +
                '}';
    }
}
