package com.unityagent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all available Unity tools.
 * Tools self-register at startup via Spring dependency injection.
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
        for (Tool tool : toolBeans) {
            register(tool);
        }
        log.info("ToolRegistry initialized with {} tool(s): {}", tools.size(), tools.keySet());
    }

    /**
     * Register a tool. Rejects duplicates.
     *
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    public void register(Tool tool) {
        Tool existing = tools.putIfAbsent(tool.name(), tool);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Duplicate tool registration: '" + tool.name() + "' is already registered");
        }
        log.debug("Registered tool: {}", tool.name());
    }

    /**
     * Check if a tool is registered.
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
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
     * @return unmodifiable view of all registered tools
     */
    public Collection<Tool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * @return count of registered tools
     */
    public int size() {
        return tools.size();
    }
}
