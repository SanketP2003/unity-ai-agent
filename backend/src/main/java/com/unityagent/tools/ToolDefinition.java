package com.unityagent.tools;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.*;

/**
 * Encapsulates the complete metadata, schema, permissions, runtime modes,
 * prerequisites, and effects for a Unity tool.
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
    private final List<String> failureCategories;
    private final ToolRiskLevel riskLevel;
    private final boolean reversible;
    private final List<String> prerequisites;
    private final List<String> produces;
    private final List<String> modifies;
    private final String validationRequired;

    public ToolDefinition(String name,
                          String description,
                          Map<String, Object> inputSchema,
                          ToolPermission permission,
                          Set<ToolMode> allowedModes,
                          boolean destructive,
                          int timeoutSeconds) {
        this(name, description, inputSchema, null, permission, allowedModes, destructive, timeoutSeconds,
                "General", List.of(), destructive ? ToolRiskLevel.HIGH : ToolRiskLevel.LOW, !destructive,
                List.of(), List.of(), List.of(), null);
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
                          List<String> failureCategories) {
        this(name, description, inputSchema, outputSchema, permission, allowedModes, destructive, timeoutSeconds,
                domain, failureCategories, destructive ? ToolRiskLevel.HIGH : ToolRiskLevel.LOW, !destructive,
                List.of(), List.of(), List.of(), null);
    }

    public ToolDefinition(String name,
                          String description,
                          Map<String, Object> inputSchema,
                          ToolPermission permission,
                          Set<ToolMode> allowedModes,
                          boolean destructive,
                          int timeoutSeconds,
                          String domain,
                          ToolRiskLevel riskLevel,
                          boolean reversible,
                          List<String> prerequisites,
                          List<String> produces,
                          List<String> modifies,
                          String validationRequired) {
        this(name, description, inputSchema, null, permission, allowedModes, destructive, timeoutSeconds,
                domain, List.of(), riskLevel, reversible, prerequisites, produces, modifies, validationRequired);
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
                          List<String> failureCategories,
                          ToolRiskLevel riskLevel,
                          boolean reversible,
                          List<String> prerequisites,
                          List<String> produces,
                          List<String> modifies,
                          String validationRequired) {
        this.name = Objects.requireNonNull(name, "Tool name cannot be null");
        this.description = Objects.requireNonNull(description, "Tool description cannot be null");
        this.inputSchema = inputSchema != null ? inputSchema : Map.of("type", "object", "properties", Map.of());
        this.outputSchema = outputSchema != null ? outputSchema : Map.of("type", "object");
        this.permission = permission != null ? permission : ToolPermission.SAFE;
        this.allowedModes = allowedModes != null ? allowedModes : Set.of(ToolMode.BOTH);
        this.destructive = destructive;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.domain = domain != null ? domain : "General";
        this.failureCategories = failureCategories != null ? failureCategories : List.of();
        this.riskLevel = riskLevel != null ? riskLevel : (destructive ? ToolRiskLevel.HIGH : ToolRiskLevel.LOW);
        this.reversible = reversible;
        this.prerequisites = prerequisites != null ? List.copyOf(prerequisites) : List.of();
        this.produces = produces != null ? List.copyOf(produces) : List.of();
        this.modifies = modifies != null ? List.copyOf(modifies) : List.of();
        this.validationRequired = validationRequired;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getInputSchema() { return inputSchema; }
    public Map<String, Object> getOutputSchema() { return outputSchema; }
    public ToolPermission getPermission() { return permission; }
    public Set<ToolMode> getAllowedModes() { return allowedModes; }
    public boolean isDestructive() { return destructive; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public String getDomain() { return domain; }
    public List<String> getFailureCategories() { return failureCategories; }
    public ToolRiskLevel getRiskLevel() { return riskLevel; }
    public boolean isReversible() { return reversible; }
    public List<String> getPrerequisites() { return prerequisites; }
    public List<String> getProduces() { return produces; }
    public List<String> getModifies() { return modifies; }
    public String getValidationRequired() { return validationRequired; }

    public boolean isAllowedInMode(ToolMode mode) {
        if (mode == null || mode == ToolMode.BOTH) return true;
        if (allowedModes.contains(ToolMode.BOTH)) return true;
        return allowedModes.contains(mode);
    }

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
                ", domain='" + domain + '\'' +
                ", risk=" + riskLevel +
                ", produces=" + produces +
                '}';
    }
}
