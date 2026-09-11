using System;
using System.Collections.Generic;
using AutonomousUnityAgent.Models;
using UnityEngine;

namespace AutonomousUnityAgent.Editor
{
    /// <summary>
    /// Interface for a Unity-side tool handler.
    /// Each tool processes a TOOL_REQUEST and returns a TOOL_RESPONSE.
    /// </summary>
    public interface IBridgeTool
    {
        string ToolName { get; }
        BridgeMessage Execute(BridgeMessage request);
    }

    /// <summary>
    /// Routes incoming TOOL_REQUEST messages to the correct C# tool handler.
    /// Registry pattern matching the Java ToolRegistry.
    /// </summary>
    public class ToolDispatcher
    {
        private readonly Dictionary<string, IBridgeTool> _tools = new Dictionary<string, IBridgeTool>();

        /// <summary>
        /// Register a tool handler.
        /// </summary>
        public void Register(IBridgeTool tool)
        {
            if (_tools.ContainsKey(tool.ToolName))
            {
                Debug.LogWarning($"[ToolDispatcher] Overwriting existing tool: {tool.ToolName}");
            }
            _tools[tool.ToolName] = tool;
            Debug.Log($"[ToolDispatcher] Registered tool: {tool.ToolName}");
        }

        /// <summary>
        /// Dispatch a TOOL_REQUEST to the appropriate handler.
        /// Returns a TOOL_RESPONSE message.
        /// </summary>
        public BridgeMessage Dispatch(BridgeMessage request)
        {
            if (request == null)
            {
                return BridgeMessage.Error(null, "NULL_REQUEST", "Received null request");
            }

            string toolName = request.tool;

            if (string.IsNullOrEmpty(toolName))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_TOOL",
                    "Tool name is required in TOOL_REQUEST");
            }

            if (!_tools.TryGetValue(toolName, out IBridgeTool tool))
            {
                return BridgeMessage.Error(request.operationId, "UNKNOWN_TOOL",
                    $"No handler registered for tool: {toolName}");
            }

            try
            {
                Debug.Log($"[ToolDispatcher] Executing tool: {toolName} (operationId={request.operationId})");
                BridgeMessage response = tool.Execute(request);
                Debug.Log($"[ToolDispatcher] Tool {toolName} completed: success={response.success}");
                return response;
            }
            catch (Exception ex)
            {
                Debug.LogError($"[ToolDispatcher] Tool {toolName} threw exception: {ex.Message}\n{ex.StackTrace}");
                return BridgeMessage.Error(request.operationId, "TOOL_EXCEPTION",
                    $"Tool '{toolName}' failed: {ex.Message}");
            }
        }

        /// <summary>
        /// Check if a tool is registered.
        /// </summary>
        public bool HasTool(string toolName) => _tools.ContainsKey(toolName);

        /// <summary>
        /// Get all registered tool names.
        /// </summary>
        public IEnumerable<string> GetToolNames() => _tools.Keys;

        /// <summary>
        /// Number of registered tools.
        /// </summary>
        public int Count => _tools.Count;
    }
}
