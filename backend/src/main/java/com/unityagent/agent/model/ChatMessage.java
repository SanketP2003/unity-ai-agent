package com.unityagent.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral representation of a message in the conversation history.
 * Explicitly associates TOOL messages with their original toolCallId.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }

    private final Role role;
    private final String content;
    private final List<ToolCall> toolCalls;
    private final String toolCallId;
    private final String toolName;

    public ChatMessage(Role role, String content, List<ToolCall> toolCalls, String toolCallId, String toolName) {
        this.role = Objects.requireNonNull(role, "Role cannot be null");
        this.content = content;
        this.toolCalls = toolCalls != null ? Collections.unmodifiableList(new ArrayList<>(toolCalls)) : List.of();
        this.toolCallId = toolCallId;
        this.toolName = toolName;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, null, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, null, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, null, null, null);
    }

    public static ChatMessage assistantToolCalls(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, content, toolCalls, null, null);
    }

    public static ChatMessage toolResult(String toolCallId, String toolName, String result) {
        Objects.requireNonNull(toolCallId, "toolCallId is required for TOOL messages");
        return new ChatMessage(Role.TOOL, result, null, toolCallId, toolName);
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "role=" + role +
                ", content='" + (content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
                ", toolCalls=" + toolCalls.size() +
                ", toolCallId='" + toolCallId + '\'' +
                '}';
    }
}
