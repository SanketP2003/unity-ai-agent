package com.unityagent.agent.provider.openai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.model.AgentCompletion;
import com.unityagent.agent.model.AgentPrompt;
import com.unityagent.agent.model.ChatMessage;
import com.unityagent.agent.model.ToolCall;
import com.unityagent.agent.model.UsageMetadata;
import com.unityagent.tools.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Dedicated mapper that isolates OpenAI wire protocol formatting and parsing
 * from the provider-neutral agent architecture.
 */
public class OpenAIToolMapper {

    private static final Pattern CONTROL_TOKEN_PATTERN = Pattern.compile("(?s)<\\|.*?\\|>");
    private static final Pattern CONTROL_TOKEN_SINGLE_PATTERN = Pattern.compile("<\\|[^>]*(\\|>|$)?");
    private static final Pattern TO_PREFIX_PATTERN = Pattern.compile("(?m)^\\s*to=[^\\s\\n]+\\s*");
    private static final Pattern TO_FUNCTIONS_PATTERN = Pattern.compile("to=functions\\.[^\\s\\n]*");
    private static final Pattern INVALID_CONTROL_CHARS_PATTERN = Pattern.compile("[^\\x20-\\x7E\\r\\n\\t]");
    private static final Pattern ID_CLEANUP_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final Pattern FUNCTIONS_PREFIX_PATTERN = Pattern.compile("^functions\\.");
    private static final Pattern PYTHON_TAG_PATTERN = Pattern.compile("<\\|python_tag\\|>");
    private static final Pattern GENERIC_CODE_BLOCK_START = Pattern.compile("^```[a-zA-Z]*\\s*");
    private static final Pattern JSON_CODE_BLOCK_START = Pattern.compile("^```json");
    private static final Pattern CODE_BLOCK_START = Pattern.compile("^```");
    private static final Pattern CODE_BLOCK_END = Pattern.compile("```$");
    private static final Pattern TRAILING_QUOTES_PATTERN = Pattern.compile("(?<=[0-9])\"(?=[,\\}\\]\\s])");
    private static final Pattern TRAILING_COMMA_PATTERN = Pattern.compile(",\\s*([\\}\\]])");

    private final ObjectMapper mapper;

    public OpenAIToolMapper(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    /**
     * Map provider-neutral AgentPrompt to OpenAI /v1/chat/completions JSON payload.
     */
    public Map<String, Object> toRequestBody(String model, AgentPrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", prompt.getTemperature());
        if (prompt.getMaxTokens() != null) {
            body.put("max_tokens", prompt.getMaxTokens());
        }

        // Map messages
        List<Map<String, Object>> messagesList = new ArrayList<>();
        for (ChatMessage msg : prompt.getMessages()) {
            messagesList.add(toOpenAIMessage(msg));
        }
        body.put("messages", messagesList);

        // Map tools
        if (prompt.hasTools()) {
            List<Map<String, Object>> toolsList = new ArrayList<>();
            for (ToolDefinition toolDef : prompt.getTools()) {
                toolsList.add(toolDef.toOpenAITool());
            }
            body.put("tools", toolsList);
            body.put("tool_choice", "auto");
        }

        return body;
    }

    /**
     * Sanitizes raw special control tokens (e.g. <|end|>, <|start|>, <|channel|>)
     * that can leak from open-source model completions and corrupt chat templates.
     */
    public static String sanitizeContent(String text) {
        if (text == null) return null;
        String cleaned = CONTROL_TOKEN_PATTERN.matcher(text).replaceAll("");
        cleaned = CONTROL_TOKEN_SINGLE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TO_PREFIX_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TO_FUNCTIONS_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = INVALID_CONTROL_CHARS_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static String sanitizeId(String id) {
        if (id == null || id.isBlank()) {
            return "call_" + UUID.randomUUID().toString().substring(0, 8);
        }
        String cleaned = CONTROL_TOKEN_PATTERN.matcher(id).replaceAll("");
        cleaned = CONTROL_TOKEN_SINGLE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = ID_CLEANUP_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned.isBlank() ? "call_" + Math.abs(id.hashCode()) : cleaned;
    }

    public static String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "";
        String cleaned = CONTROL_TOKEN_PATTERN.matcher(name).replaceAll("");
        cleaned = CONTROL_TOKEN_SINGLE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = FUNCTIONS_PREFIX_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = ID_CLEANUP_PATTERN.matcher(cleaned).replaceAll("");
        if (cleaned.endsWith("commentary") && cleaned.length() > 10) {
            cleaned = cleaned.substring(0, cleaned.length() - 10);
        }
        if (cleaned.endsWith("thought") && cleaned.length() > 7) {
            cleaned = cleaned.substring(0, cleaned.length() - 7);
        }
        if (cleaned.endsWith("json") && cleaned.length() > 4) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }
        return cleaned;
    }

    public String sanitizeArguments(String rawArgs, Map<String, Object> parsedArgs) {
        if (rawArgs == null || rawArgs.isBlank() || rawArgs.contains("<|")) {
            return serializeArguments(parsedArgs != null ? parsedArgs : Collections.emptyMap());
        }
        try {
            JsonNode node = mapper.readTree(rawArgs);
            if (node.isObject() || node.isArray()) {
                return rawArgs;
            }
        } catch (Exception ignored) {
        }
        return serializeArguments(parsedArgs != null ? parsedArgs : Collections.emptyMap());
    }

    /**
     * Map a provider-neutral ChatMessage to OpenAI message object.
     */
    public Map<String, Object> toOpenAIMessage(ChatMessage msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (msg.getRole()) {
            case SYSTEM -> {
                m.put("role", "system");
                m.put("content", sanitizeContent(msg.getContent()));
            }
            case USER -> {
                m.put("role", "user");
                m.put("content", sanitizeContent(msg.getContent()));
            }
            case ASSISTANT -> {
                m.put("role", "assistant");
                if (msg.hasToolCalls()) {
                    m.put("content", null);
                } else {
                    m.put("content", sanitizeContent(msg.getContent()));
                }
                if (msg.hasToolCalls()) {
                    List<Map<String, Object>> toolCallsList = new ArrayList<>();
                    for (ToolCall tc : msg.getToolCalls()) {
                        Map<String, Object> callMap = new LinkedHashMap<>();
                        String cleanId = sanitizeId(tc.getId());
                        callMap.put("id", cleanId);
                        callMap.put("type", "function");

                        Map<String, Object> funcMap = new LinkedHashMap<>();
                        funcMap.put("name", sanitizeName(tc.getName()));
                        funcMap.put("arguments", sanitizeArguments(tc.getRawArguments(), tc.getArguments()));
                        callMap.put("function", funcMap);

                        toolCallsList.add(callMap);
                    }
                    m.put("tool_calls", toolCallsList);
                }
            }
            case TOOL -> {
                m.put("role", "tool");
                m.put("tool_call_id", sanitizeId(msg.getToolCallId()));
                String c = sanitizeContent(msg.getContent());
                m.put("content", c != null ? c : "{}");
            }
        }
        return m;
    }

    /**
     * Parse official OpenAI chat completions response into provider-neutral AgentCompletion.
     */
    public AgentCompletion parseResponseBody(String responseJson) throws Exception {
        JsonNode root = mapper.readTree(responseJson);

        // Check for OpenAI API error structure
        if (root.has("error")) {
            JsonNode err = root.get("error");
            String message = err.has("message") ? err.get("message").asText() : "OpenAI error";
            if (message.contains("unexpected tokens remaining in message header: Some(\"")) {
                int start = message.indexOf("Some(\"") + 6;
                int end = message.lastIndexOf("\")");
                if (end > start) {
                    String extracted = message.substring(start, end);
                    String cleanContent = sanitizeContent(extracted);
                    if (cleanContent != null && !cleanContent.isBlank()) {
                        return new AgentCompletion(cleanContent, null, Collections.emptyList(), "stop", UsageMetadata.empty());
                    }
                }
            }
            throw new RuntimeException("OpenAI API returned error: " + message);
        }

        JsonNode choices = root.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("OpenAI API returned no choices in response");
        }

        JsonNode choice = choices.get(0);
        String finishReason = choice.has("finish_reason") && !choice.get("finish_reason").isNull()
                ? choice.get("finish_reason").asText() : "stop";

        JsonNode messageNode = choice.get("message");
        String content = messageNode.has("content") && !messageNode.get("content").isNull()
                ? sanitizeContent(messageNode.get("content").asText()) : null;
        String reasoningContent = messageNode.has("reasoning_content") && !messageNode.get("reasoning_content").isNull()
                ? sanitizeContent(messageNode.get("reasoning_content").asText()) : null;

        List<ToolCall> toolCalls = new ArrayList<>();
        if (messageNode.has("tool_calls") && messageNode.get("tool_calls").isArray()) {
            for (JsonNode tcNode : messageNode.get("tool_calls")) {
                String id = tcNode.has("id") ? sanitizeId(tcNode.get("id").asText()) : ("call_" + UUID.randomUUID().toString().substring(0, 8));
                JsonNode functionNode = tcNode.get("function");
                if (functionNode != null) {
                    String name = functionNode.has("name") ? sanitizeName(functionNode.get("name").asText()) : "";
                    String rawArgs = "{}";
                    if (functionNode.has("arguments")) {
                        JsonNode argsNode = functionNode.get("arguments");
                        if (argsNode.isTextual()) {
                            rawArgs = argsNode.asText();
                        } else if (argsNode.isObject() || argsNode.isArray()) {
                            rawArgs = mapper.writeValueAsString(argsNode);
                        }
                    }
                    Map<String, Object> parsedArgs = parseArguments(rawArgs);
                    toolCalls.add(new ToolCall(id, name, parsedArgs, rawArgs));
                }
            }
        }

        // Fallback: If no explicit tool_calls array, check if content contains a tool call JSON or python tag
        if (toolCalls.isEmpty() && content != null && !content.isBlank()) {
            String trimmed = content.trim();
            if (trimmed.contains("<|python_tag|>")) {
                trimmed = PYTHON_TAG_PATTERN.matcher(trimmed).replaceAll("").trim();
            }
            if (trimmed.startsWith("```json")) {
                trimmed = JSON_CODE_BLOCK_START.matcher(trimmed).replaceAll("");
                trimmed = CODE_BLOCK_END.matcher(trimmed).replaceAll("");
                trimmed = trimmed.trim();
            } else if (trimmed.startsWith("```")) {
                trimmed = CODE_BLOCK_START.matcher(trimmed).replaceAll("");
                trimmed = CODE_BLOCK_END.matcher(trimmed).replaceAll("");
                trimmed = trimmed.trim();
            }
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    JsonNode node = mapper.readTree(trimmed);
                    if (node.has("name") && (node.has("parameters") || node.has("arguments"))) {
                        String name = sanitizeName(node.get("name").asText());
                        JsonNode argsNode = node.has("parameters") ? node.get("parameters") : node.get("arguments");
                        String rawArgs = mapper.writeValueAsString(argsNode);
                        Map<String, Object> parsedArgs = parseArguments(rawArgs);
                        toolCalls.add(new ToolCall("call_" + UUID.randomUUID().toString().substring(0, 8), name, parsedArgs, rawArgs));
                    }
                } catch (Exception ignored) {}
            }
        }

        UsageMetadata usage = UsageMetadata.empty();
        if (root.has("usage") && !root.get("usage").isNull()) {
            JsonNode uNode = root.get("usage");
            int prompt = uNode.has("prompt_tokens") ? uNode.get("prompt_tokens").asInt() : 0;
            int completion = uNode.has("completion_tokens") ? uNode.get("completion_tokens").asInt() : 0;
            int total = uNode.has("total_tokens") ? uNode.get("total_tokens").asInt() : 0;
            usage = UsageMetadata.of(prompt, completion, total);
        }

        return new AgentCompletion(content, reasoningContent, toolCalls, finishReason, usage);
    }

    private String serializeArguments(Map<String, Object> args) {
        try {
            return mapper.writeValueAsString(args != null ? args : Map.of());
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> parseArguments(String rawArgs) {
        if (rawArgs == null || rawArgs.isBlank()) {
            return Map.of();
        }
        String cleaned = rawArgs.trim();
        if (cleaned.contains("<|")) {
            cleaned = cleaned.substring(0, cleaned.indexOf("<|")).trim();
        }
        try {
            return mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            try {
                if (cleaned.startsWith("```")) {
                    cleaned = GENERIC_CODE_BLOCK_START.matcher(cleaned).replaceAll("");
                    cleaned = CODE_BLOCK_END.matcher(cleaned).replaceAll("");
                    cleaned = cleaned.trim();
                }
                // Repair common LLM quirks: stray quotes immediately following numbers, trailing commas
                String repaired = TRAILING_QUOTES_PATTERN.matcher(cleaned).replaceAll("");
                repaired = TRAILING_COMMA_PATTERN.matcher(repaired).replaceAll("$1");
                return mapper.readValue(repaired, new TypeReference<>() {});
            } catch (Exception ignored) {
                return Map.of();
            }
        }
    }
}
