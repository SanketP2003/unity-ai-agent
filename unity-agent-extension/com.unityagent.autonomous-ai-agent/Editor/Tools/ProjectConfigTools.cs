using System;
using System.Collections.Generic;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class GetProjectSettingsTool : IBridgeTool
    {
        public string ToolName => "get_project_settings";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string json = "{"
                + "\"productName\":\"" + PlayerSettings.productName + "\","
                + "\"companyName\":\"" + PlayerSettings.companyName + "\","
                + "\"unityVersion\":\"" + Application.unityVersion + "\","
                + "\"platform\":\"" + EditorUserBuildSettings.activeBuildTarget.ToString() + "\","
                + "\"colorSpace\":\"" + PlayerSettings.colorSpace.ToString() + "\""
                + "}";
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetBuildSettingsTool : IBridgeTool
    {
        public string ToolName => "get_build_settings";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var scenes = EditorBuildSettings.scenes;
            var sceneList = new List<string>();
            foreach (var s in scenes)
            {
                sceneList.Add("{\"path\":\"" + s.path + "\",\"enabled\":" + (s.enabled ? "true" : "false") + "}");
            }

            string json = "{"
                + "\"platform\":\"" + EditorUserBuildSettings.activeBuildTarget.ToString() + "\","
                + "\"sceneCount\":" + scenes.Length + ","
                + "\"scenes\":[" + string.Join(",", sceneList) + "]"
                + "}";
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class SetBuildSettingTool : IBridgeTool
    {
        public string ToolName => "set_build_setting";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string scenePath = ToolParamHelper.ExtractString(request.parameters, "scenePath");
            bool enabled = ToolParamHelper.ExtractBool(request.parameters, "enabled", true);

            if (string.IsNullOrEmpty(scenePath))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameter 'scenePath' is required");
            }

            var scenes = new List<EditorBuildSettingsScene>(EditorBuildSettings.scenes);
            bool found = false;
            for (int i = 0; i < scenes.Count; i++)
            {
                if (scenes[i].path.Equals(scenePath, StringComparison.OrdinalIgnoreCase))
                {
                    scenes[i].enabled = enabled;
                    found = true;
                    break;
                }
            }

            if (!found)
            {
                scenes.Add(new EditorBuildSettingsScene(scenePath, enabled));
            }

            EditorBuildSettings.scenes = scenes.ToArray();

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Added/updated build scene '{scenePath}' (enabled={enabled})",
                target = scenePath
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetLayersTool : IBridgeTool
    {
        public string ToolName => "get_layers";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var layerList = new List<string>();
            for (int i = 0; i < 32; i++)
            {
                string layerName = LayerMask.LayerToName(i);
                if (!string.IsNullOrEmpty(layerName))
                {
                    layerList.Add("{\"index\":" + i + ",\"name\":\"" + layerName + "\"}");
                }
            }

            string json = "{\"layers\":[" + string.Join(",", layerList) + "]}";
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class SetLayerTool : IBridgeTool
    {
        public string ToolName => "set_layer";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string layerStr = ToolParamHelper.ExtractString(request.parameters, "layer");

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Target '{target}' not found");

            int layer = LayerMask.NameToLayer(layerStr);
            if (layer < 0) int.TryParse(layerStr, out layer);
            if (layer < 0 || layer > 31) return BridgeMessage.Error(request.operationId, "INVALID_LAYER", $"Invalid layer '{layerStr}'");

            Undo.RecordObject(go, "Set Layer " + go.name);
            go.layer = layer;

            bool recursive = ToolParamHelper.ExtractBool(request.parameters, "recursive", false);
            if (recursive)
            {
                foreach (Transform child in go.GetComponentsInChildren<Transform>(true))
                {
                    child.gameObject.layer = layer;
                }
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Set layer of '{go.name}' to {layer} ('{LayerMask.LayerToName(layer)}')",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetTagsTool : IBridgeTool
    {
        public string ToolName => "get_tags";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var tags = UnityEditorInternal.InternalEditorUtility.tags;
            var tagList = new List<string>();
            foreach (var t in tags) tagList.Add("\"" + t + "\"");

            string json = "{\"tags\":[" + string.Join(",", tagList) + "]}";
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateTagTool : IBridgeTool
    {
        public string ToolName => "create_tag";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string tagName = ToolParamHelper.ExtractString(request.parameters, "tag");
            if (string.IsNullOrEmpty(tagName)) return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameter 'tag' is required");

            UnityEditorInternal.InternalEditorUtility.AddTag(tagName);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created custom tag '{tagName}'",
                target = tagName
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
