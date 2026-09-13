package com.unityagent.agent.model;

import com.unityagent.tools.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provider-neutral prompt passed to an AIProvider.
 */
public class AgentPrompt {

    private final List<ChatMessage> messages;
    private final List<ToolDefinition> tools;
    private final Double temperature;
    private final Integer maxTokens;

    public AgentPrompt(List<ChatMessage> messages, List<ToolDefinition> tools, Double temperature, Integer maxTokens) {
        this.messages = messages != null ? Collections.unmodifiableList(new ArrayList<>(messages)) : List.of();
        this.tools = tools != null ? Collections.unmodifiableList(new ArrayList<>(tools)) : List.of();
        this.temperature = temperature != null ? temperature : 0.2;
        this.maxTokens = maxTokens != null ? maxTokens : 2048;
    }

    public static AgentPrompt of(List<ChatMessage> messages, List<ToolDefinition> tools) {
        return new AgentPrompt(messages, tools, 0.2, 2048);
    }

    public static AgentPrompt of(List<ChatMessage> messages) {
        return new AgentPrompt(messages, List.of(), 0.2, 2048);
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public boolean hasTools() {
        return !tools.isEmpty();
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }
}
