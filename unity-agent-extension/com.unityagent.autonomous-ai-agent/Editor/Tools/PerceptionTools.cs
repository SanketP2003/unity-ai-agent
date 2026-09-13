#pragma warning disable CS0618
using System;
using System.Collections.Generic;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class GetActiveSceneTool : IBridgeTool
    {
        public string ToolName => "get_active_scene";

        public BridgeMessage Execute(BridgeMessage request)
        {
            Scene activeScene = SceneManager.GetActiveScene();
            var responseData = new ActiveSceneResponseData
            {
                name = activeScene.name,
                path = activeScene.path,
                isLoaded = activeScene.isLoaded,
                isDirty = activeScene.isDirty,
                rootCount = activeScene.rootCount,
                buildIndex = activeScene.buildIndex
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class GetSelectedObjectTool : IBridgeTool
    {
        public string ToolName => "get_selected_object";

        public BridgeMessage Execute(BridgeMessage request)
        {
            GameObject selected = Selection.activeGameObject;
            if (selected == null)
            {
                var emptyData = new SelectedObjectResponseData
                {
                    hasSelection = false,
                    message = "No GameObject is currently selected in the Unity Editor."
                };
                return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(emptyData));
            }

            Transform t = selected.transform;
            var responseData = new SelectedObjectResponseData
            {
                hasSelection = true,
                objectId = ToolParamHelper.FormatObjectId(selected),
                name = selected.name,
                hierarchyPath = ToolParamHelper.GetHierarchyPath(selected),
                activeSelf = selected.activeSelf,
                scene = selected.scene.name,
                components = new List<string>(),
                transform = new TransformData
                {
                    position = Vector3Data.FromVector3(t.position),
                    rotation = Vector3Data.FromVector3(t.eulerAngles),
                    scale = Vector3Data.FromVector3(t.lossyScale),
                    localPosition = Vector3Data.FromVector3(t.localPosition),
                    localRotation = Vector3Data.FromVector3(t.localEulerAngles),
                    localScale = Vector3Data.FromVector3(t.localScale)
                },
                parent = t.parent != null ? t.parent.name : null,
                childCount = t.childCount
            };

            foreach (var comp in selected.GetComponents<Component>())
            {
                if (comp != null)
                {
                    responseData.components.Add(comp.GetType().Name);
                }
            }

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class GetObjectComponentsTool : IBridgeTool
    {
        public string ToolName => "get_object_components";

        public BridgeMessage Execute(BridgeMessage request)
        {
            GameObject go = ToolParamHelper.ResolveTargetFromRequest(request.parameters);
            if (go == null)
            {
                string target = ToolParamHelper.ExtractString(request.parameters, "objectId");
                if (string.IsNullOrEmpty(target))
                {
                    target = ToolParamHelper.ExtractString(request.parameters, "target");
                }
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND",
                    $"Could not find GameObject for target/objectId: '{target}'");
            }

            var responseData = new ObjectComponentsResponseData
            {
                objectId = ToolParamHelper.FormatObjectId(go),
                name = go.name,
                scene = go.scene.name,
                components = new List<string>(),
                componentDetails = new List<ComponentInfoData>()
            };

            foreach (var comp in go.GetComponents<Component>())
            {
                if (comp != null)
                {
                    string typeName = comp.GetType().Name;
                    responseData.components.Add(typeName);

                    bool enabled = true;
                    if (comp is Behaviour b)
                    {
                        enabled = b.enabled;
                    }

                    responseData.componentDetails.Add(new ComponentInfoData
                    {
                        type = typeName,
                        enabled = enabled
                    });
                }
            }

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class GetObjectTransformTool : IBridgeTool
    {
        public string ToolName => "get_object_transform";

        public BridgeMessage Execute(BridgeMessage request)
        {
            GameObject go = ToolParamHelper.ResolveTargetFromRequest(request.parameters);
            if (go == null)
            {
                string target = ToolParamHelper.ExtractString(request.parameters, "objectId");
                if (string.IsNullOrEmpty(target))
                {
                    target = ToolParamHelper.ExtractString(request.parameters, "target");
                }
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND",
                    $"Could not find GameObject for target/objectId: '{target}'");
            }

            Transform t = go.transform;
            var responseData = new ObjectTransformResponseData
            {
                objectId = ToolParamHelper.FormatObjectId(go),
                name = go.name,
                position = Vector3Data.FromVector3(t.position),
                rotation = Vector3Data.FromVector3(t.eulerAngles),
                scale = Vector3Data.FromVector3(t.lossyScale),
                localPosition = Vector3Data.FromVector3(t.localPosition),
                localRotation = Vector3Data.FromVector3(t.localEulerAngles),
                localScale = Vector3Data.FromVector3(t.localScale),
                forward = Vector3Data.FromVector3(t.forward),
                up = Vector3Data.FromVector3(t.up),
                right = Vector3Data.FromVector3(t.right),
                parent = t.parent != null ? t.parent.name : null,
                parentObjectId = t.parent != null ? ToolParamHelper.FormatObjectId(t.parent.gameObject) : null,
                childCount = t.childCount
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class GetPlayModeStateTool : IBridgeTool
    {
        public string ToolName => "get_play_mode_state";

        public BridgeMessage Execute(BridgeMessage request)
        {
            bool isPlaying = EditorApplication.isPlaying;
            bool isPaused = EditorApplication.isPaused;
            bool isCompiling = EditorApplication.isCompiling;
            string state = isPlaying ? (isPaused ? "Paused" : "Playing") : "EditMode";

            var responseData = new PlayModeStateResponseData
            {
                isPlaying = isPlaying,
                isPaused = isPaused,
                isCompiling = isCompiling,
                state = state
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class GetConsoleErrorsTool : IBridgeTool
    {
        public string ToolName => "get_console_errors";

        public BridgeMessage Execute(BridgeMessage request)
        {
            GetConsoleLogsTool.EnsureHooked();

            int count = (int)ToolParamHelper.ExtractFloat(request.parameters, "count", 20f);
            if (count <= 0) count = 20;
            if (count > 100) count = 100;

            var errorLogs = GetConsoleLogsTool.GetLogs(count, entry =>
                string.Equals(entry.type, "Error", StringComparison.OrdinalIgnoreCase) ||
                string.Equals(entry.type, "Exception", StringComparison.OrdinalIgnoreCase) ||
                string.Equals(entry.type, "Assert", StringComparison.OrdinalIgnoreCase));

            var responseData = new ConsoleErrorsResponseData
            {
                errorCount = errorLogs.Count,
                errors = errorLogs
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }
}
