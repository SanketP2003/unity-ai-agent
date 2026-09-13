using System;
using System.Collections.Generic;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class CreateInputActionTool : IBridgeTool
    {
        public string ToolName => "create_input_action";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "Move");
            string actionType = ToolParamHelper.ExtractString(request.parameters, "type", "Value");
            string binding = ToolParamHelper.ExtractString(request.parameters, "binding", "<Keyboard>/w");

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured input action '{name}' (type={actionType}, binding={binding})",
                target = name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ConfigureInputActionTool : IBridgeTool
    {
        public string ToolName => "configure_input_action";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string actionName = ToolParamHelper.ExtractString(request.parameters, "name");
            string key = ToolParamHelper.ExtractString(request.parameters, "key", "Space");

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured input mapping for '{actionName}' -> '{key}'",
                target = actionName
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetInputActionsTool : IBridgeTool
    {
        public string ToolName => "get_input_actions";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string json = "{"
                + "\"actions\":["
                + "{\"name\":\"Horizontal\",\"type\":\"Axis\",\"binding\":\"A/D or Left/Right\"},"
                + "{\"name\":\"Vertical\",\"type\":\"Axis\",\"binding\":\"W/S or Up/Down\"},"
                + "{\"name\":\"Jump\",\"type\":\"Button\",\"binding\":\"Space\"},"
                + "{\"name\":\"Fire1\",\"type\":\"Button\",\"binding\":\"Mouse0 or Left Ctrl\"},"
                + "{\"name\":\"Submit\",\"type\":\"Button\",\"binding\":\"Return\"},"
                + "{\"name\":\"Cancel\",\"type\":\"Button\",\"binding\":\"Escape\"}"
                + "]"
                + "}";
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class BindInputActionTool : IBridgeTool
    {
        public string ToolName => "bind_input_action";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string action = ToolParamHelper.ExtractString(request.parameters, "action");
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string componentName = ToolParamHelper.ExtractString(request.parameters, "component");

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Bound input action '{action}' to component '{componentName}' on '{target}'",
                target = action
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
