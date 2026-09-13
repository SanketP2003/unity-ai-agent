package com.unityagent.tools;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Encapsulates the complete metadata, schema, permissions, and runtime modes
 * for a Unity tool.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> inputSchema;
    private final Map<String, Object> outputSchema;
    private final ToolPermission permission;
    private final Set<ToolMode> allowedModes;
    private final boolean destructive;
    private final int timeoutSeconds;
    private final String domain;
    private final java.util.List<String> failureCategories;

    public ToolDefinition(String name,
                          String description,
                          Map<String, Object> inputSchema,
                          ToolPermission permission,
                          Set<ToolMode> allowedModes,
                          boolean destructive,
                          int timeoutSeconds) {
        this(name, description, inputSchema, null, permission, allowedModes, destructive, timeoutSeconds, "General", java.util.List.of());
    }

    public ToolDefinition(String name,
                          String description,
                          Map<String, Object> inputSchema,
                          Map<String, Object> outputSchema,
                          ToolPermission permission,
                          Set<ToolMode> allowedModes,
                          boolean destructive,
                          int timeoutSeconds,
                          String domain,
                          java.util.List<String> failureCategories) {
        this.name = Objects.requireNonNull(name, "Tool name cannot be null");
        this.description = Objects.requireNonNull(description, "Tool description cannot be null");
        this.inputSchema = inputSchema != null ? inputSchema : Map.of("type", "object", "properties", Map.of());
        this.outputSchema = outputSchema != null ? outputSchema : Map.of("type", "object");
        this.permission = permission != null ? permission : ToolPermission.SAFE;
        this.allowedModes = allowedModes != null ? allowedModes : Set.of(ToolMode.BOTH);
        this.destructive = destructive;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.domain = domain != null ? domain : "General";
        this.failureCategories = failureCategories != null ? failureCategories : java.util.List.of();
    }

    public Map<String, Object> getOutputSchema() { return outputSchema; }
    public String getDomain() { return domain; }
    public java.util.List<String> getFailureCategories() { return failureCategories; }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public ToolPermission getPermission() {
        return permission;
    }

    public Set<ToolMode> getAllowedModes() {
        return allowedModes;
    }

    public boolean isDestructive() {
        return destructive;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public boolean isAllowedInMode(ToolMode mode) {
        if (mode == null || mode == ToolMode.BOTH) {
            return true;
        }
        if (allowedModes.contains(ToolMode.BOTH)) {
            return true;
        }
        return allowedModes.contains(mode);
    }

    /**
     * Converts this definition into the standard OpenAI Tool format.
     */
    public Map<String, Object> toOpenAITool() {
        Map<String, Object> functionObj = new LinkedHashMap<>();
        functionObj.put("name", name);
        functionObj.put("description", description);
        functionObj.put("parameters", inputSchema);

        Map<String, Object> toolObj = new LinkedHashMap<>();
        toolObj.put("type", "function");
        toolObj.put("function", functionObj);
        return toolObj;
    }

    @Override
    public String toString() {
        return "ToolDefinition{" +
                "name='" + name + '\'' +
                ", permission=" + permission +
                ", allowedModes=" + allowedModes +
                ", destructive=" + destructive +
                ", timeout=" + timeoutSeconds + "s" +
                '}';
    }
}
