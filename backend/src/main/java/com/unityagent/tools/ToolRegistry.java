package com.unityagent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic registry of all available Unity tools.
 * Supports tool registration, unregistration, lookup, mode/permission filtering,
 * parameter validation, and OpenAI function calling schema generation.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final ConcurrentHashMap<String, Tool> tools = new ConcurrentHashMap<>();

    /**
     * Construct the registry with all available Tool beans.
     * Spring injects all beans implementing the Tool interface.
     */
    public ToolRegistry(List<Tool> toolBeans) {
        if (toolBeans != null) {
            for (Tool tool : toolBeans) {
                register(tool);
            }
        }
        log.info("ToolRegistry initialized with {} tool(s): {}", tools.size(), tools.keySet());
    }

    /**
     * Register a tool. Rejects duplicates.
     */
    public void register(Tool tool) {
        Objects.requireNonNull(tool, "Tool cannot be null");
        Tool existing = tools.putIfAbsent(tool.name(), tool);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Duplicate tool registration: '" + tool.name() + "' is already registered");
        }
        log.debug("Registered tool: {}", tool.name());
    }

    /**
     * Unregister a tool by name.
     *
     * @return true if the tool was found and removed
     */
    public boolean unregister(String name) {
        if (name == null) return false;
        boolean removed = tools.remove(name) != null;
        if (removed) {
            log.info("Unregistered tool: {}", name);
        }
        return removed;
    }

    /**
     * Check if a tool is registered.
     */
    public boolean hasTool(String name) {
        return name != null && tools.containsKey(name);
    }

    /**
     * Get a tool by name.
     *
     * @throws IllegalArgumentException if the tool is not found
     */
    public Tool getTool(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return tool;
    }

    /**
     * Find a tool by name wrapped in an Optional.
     */
    public Optional<Tool> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * @return unmodifiable view of all registered tools
     */
    public Collection<Tool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * Alias for getAllTools() per architecture specification.
     */
    public Collection<Tool> list() {
        return getAllTools();
    }

    /**
     * @return all tool definitions
     */
    public List<ToolDefinition> listDefinitions() {
        return tools.values().stream()
                .map(Tool::definition)
                .toList();
    }

    /**
     * Validate a tool by name and its parameters.
     *
     * @return null if valid, or a descriptive error message
     */
    public String validate(String toolName, Map<String, Object> parameters) {
        if (toolName == null || toolName.isBlank()) {
            return "Tool name is required";
        }
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return "Unknown tool: " + toolName;
        }
        if (tool.permission() == ToolPermission.BLOCKED) {
            return "Tool '" + toolName + "' is BLOCKED from execution";
        }
        return tool.validate(parameters);
    }

    /**
     * Generate OpenAI-compatible tool specifications for all active tools.
     */
    public List<Map<String, Object>> getOpenAIToolDefinitions() {
        return getOpenAIToolDefinitions(ToolMode.BOTH, EnumSet.complementOf(EnumSet.of(ToolPermission.BLOCKED)));
    }

    /**
     * Generate OpenAI-compatible tool specifications filtered by mode and allowed permissions.
     */
    public List<Map<String, Object>> getOpenAIToolDefinitions(ToolMode mode, Set<ToolPermission> allowedPermissions) {
        List<Map<String, Object>> openAiTools = new ArrayList<>();
        for (Tool tool : tools.values()) {
            ToolDefinition def = tool.definition();
            if (def.getPermission() == ToolPermission.BLOCKED) {
                continue;
            }
            if (allowedPermissions != null && !allowedPermissions.contains(def.getPermission())) {
                continue;
            }
            if (mode != null && !def.isAllowedInMode(mode)) {
                continue;
            }
            openAiTools.add(def.toOpenAITool());
        }
        return openAiTools;
    }

    /**
     * @return count of registered tools
     */
    public int size() {
        return tools.size();
    }

    /**
     * Finds tools belonging to a specific functional domain.
     */
    public List<Tool> getToolsByDomain(String domain) {
        if (domain == null) return List.of();
        List<Tool> result = new ArrayList<>();
        for (Tool tool : tools.values()) {
            if (domain.equalsIgnoreCase(tool.domain())) {
                result.add(tool);
            }
        }
        return result;
    }

    /**
     * Finds tools that produce a specific artifact, state, or entity.
     */
    public List<Tool> getToolsProducing(String artifact) {
        if (artifact == null) return List.of();
        List<Tool> result = new ArrayList<>();
        for (Tool tool : tools.values()) {
            for (String prod : tool.produces()) {
                if (artifact.equalsIgnoreCase(prod)) {
                    result.add(tool);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Finds tools requiring a specific prerequisite state.
     */
    public List<Tool> getToolsRequiring(String prerequisite) {
        if (prerequisite == null) return List.of();
        List<Tool> result = new ArrayList<>();
        for (Tool tool : tools.values()) {
            for (String req : tool.prerequisites()) {
                if (prerequisite.equalsIgnoreCase(req)) {
                    result.add(tool);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Finds tools matching a specific risk level.
     */
    public List<Tool> getToolsByRisk(ToolRiskLevel risk) {
        if (risk == null) return List.of();
        List<Tool> result = new ArrayList<>();
        for (Tool tool : tools.values()) {
            if (tool.riskLevel() == risk) {
                result.add(tool);
            }
        }
        return result;
    }

    /**
     * @return all tool definitions
     */
    public List<ToolDefinition> getDefinitions() {
        List<ToolDefinition> defs = new ArrayList<>();
        for (Tool tool : tools.values()) {
            defs.add(tool.definition());
        }
        return defs;
    }
}
