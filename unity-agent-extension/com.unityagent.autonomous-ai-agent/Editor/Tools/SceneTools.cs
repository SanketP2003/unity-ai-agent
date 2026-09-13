#pragma warning disable CS0618
using System;
using System.Collections.Generic;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class GetSceneHierarchyTool : IBridgeTool
    {
        public string ToolName => "get_scene_hierarchy";

        public BridgeMessage Execute(BridgeMessage request)
        {
            Scene activeScene = SceneManager.GetActiveScene();
            bool rootOnly = ToolParamHelper.ExtractBool(request.parameters, "rootOnly", false);

            var responseData = new SceneHierarchyResponseData
            {
                sceneName = activeScene.name,
                roots = new List<HierarchyNodeData>()
            };

            GameObject[] rootObjects = activeScene.GetRootGameObjects();
            responseData.rootCount = rootObjects.Length;

            foreach (var root in rootObjects)
            {
                responseData.roots.Add(BuildNode(root, rootOnly));
            }

            string json = JsonHelper.ToJson(responseData);
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }

        private HierarchyNodeData BuildNode(GameObject go, bool rootOnly)
        {
            Transform t = go.transform;
            var node = new HierarchyNodeData
            {
                objectId = ToolParamHelper.FormatObjectId(go),
                instanceId = go.GetInstanceID(),
                name = go.name,
                tag = go.tag,
                layer = go.layer,
                activeSelf = go.activeSelf,
                position = Vector3Data.FromVector3(t.position),
                rotation = Vector3Data.FromVector3(t.eulerAngles),
                scale = Vector3Data.FromVector3(t.localScale),
                components = new List<string>(),
                children = new List<HierarchyNodeData>()
            };

            foreach (var comp in go.GetComponents<Component>())
            {
                if (comp != null)
                {
                    node.components.Add(comp.GetType().Name);
                }
            }

            if (!rootOnly)
            {
                for (int i = 0; i < t.childCount; i++)
                {
                    node.children.Add(BuildNode(t.GetChild(i).gameObject, false));
                }
            }

            return node;
        }
    }

    public class CreateSceneTool : IBridgeTool
    {
        public string ToolName => "create_scene";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string setupStr = ToolParamHelper.ExtractString(request.parameters, "setup", "DefaultGameObjects");
            NewSceneSetup setup = setupStr == "EmptyScene" ? NewSceneSetup.EmptyScene : NewSceneSetup.DefaultGameObjects;

            Scene newScene = EditorSceneManager.NewScene(setup, NewSceneMode.Single);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created new scene '{newScene.name}' with setup {setup}",
                target = newScene.name
            });

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class SaveSceneTool : IBridgeTool
    {
        public string ToolName => "save_scene";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string scenePath = ToolParamHelper.ExtractString(request.parameters, "scenePath", null)
                ?? ToolParamHelper.ExtractString(request.parameters, "path", null);

            var activeScene = EditorSceneManager.GetActiveScene();
            bool success;
            if (string.IsNullOrEmpty(scenePath))
            {
                if (string.IsNullOrEmpty(activeScene.path))
                {
                    scenePath = "Assets/Scenes/Untitled.unity";
                    if (!AssetDatabase.IsValidFolder("Assets/Scenes"))
                    {
                        AssetDatabase.CreateFolder("Assets", "Scenes");
                    }
                    success = EditorSceneManager.SaveScene(activeScene, scenePath);
                }
                else
                {
                    success = EditorSceneManager.SaveScene(activeScene);
                    scenePath = activeScene.path;
                }
            }
            else
            {
                if (!scenePath.EndsWith(".unity", StringComparison.OrdinalIgnoreCase))
                {
                    scenePath += ".unity";
                }
                success = EditorSceneManager.SaveScene(activeScene, scenePath);
            }

            if (success)
            {
                AssetDatabase.Refresh();
                string json = JsonHelper.ToJson(new SimpleSuccessData
                {
                    message = $"Saved scene to '{scenePath}'",
                    target = scenePath
                });
                return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
            }
            else
            {
                return BridgeMessage.Error(request.operationId, "SAVE_FAILED", $"Failed to save scene to '{scenePath}'");
            }
        }
    }

    public class OpenSceneTool : IBridgeTool
    {
        public string ToolName => "open_scene";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string scenePath = ToolParamHelper.ExtractString(request.parameters, "scenePath");
            if (string.IsNullOrEmpty(scenePath))
            {
                string sceneName = ToolParamHelper.ExtractString(request.parameters, "sceneName");
                if (string.IsNullOrEmpty(sceneName))
                {
                    sceneName = ToolParamHelper.ExtractString(request.parameters, "name");
                }
                if (!string.IsNullOrEmpty(sceneName))
                {
                    if (!sceneName.EndsWith(".unity", StringComparison.OrdinalIgnoreCase))
                    {
                        sceneName += ".unity";
                    }
                    string[] guids = AssetDatabase.FindAssets(System.IO.Path.GetFileNameWithoutExtension(sceneName) + " t:Scene");
                    if (guids != null && guids.Length > 0)
                    {
                        scenePath = AssetDatabase.GUIDToAssetPath(guids[0]);
                    }
                    else
                    {
                        scenePath = "Assets/Scenes/" + sceneName;
                    }
                }
            }

            if (string.IsNullOrEmpty(scenePath))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameter 'scenePath' or 'sceneName' is required.");
            }

            if (!scenePath.EndsWith(".unity", StringComparison.OrdinalIgnoreCase))
            {
                scenePath += ".unity";
            }

            if (!System.IO.File.Exists(scenePath))
            {
                return BridgeMessage.Error(request.operationId, "SCENE_NOT_FOUND", $"Scene file does not exist at '{scenePath}'");
            }

            Scene openedScene = EditorSceneManager.OpenScene(scenePath, OpenSceneMode.Single);
            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Successfully opened scene '{openedScene.name}' from '{scenePath}'",
                target = openedScene.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetSceneInfoTool : IBridgeTool
    {
        public string ToolName => "get_scene_info";

        public BridgeMessage Execute(BridgeMessage request)
        {
            Scene active = SceneManager.GetActiveScene();
            var lights = UnityEngine.Object.FindObjectsOfType<Light>();
            var cameras = UnityEngine.Object.FindObjectsOfType<Camera>();

            string json = "{"
                + "\"sceneName\":\"" + active.name + "\","
                + "\"scenePath\":\"" + (string.IsNullOrEmpty(active.path) ? "" : active.path) + "\","
                + "\"isDirty\":" + (active.isDirty ? "true" : "false") + ","
                + "\"isLoaded\":" + (active.isLoaded ? "true" : "false") + ","
                + "\"rootCount\":" + active.rootCount + ","
                + "\"cameraCount\":" + cameras.Length + ","
                + "\"lightCount\":" + lights.Length + ","
                + "\"fogEnabled\":" + (RenderSettings.fog ? "true" : "false") + ","
                + "\"fogDensity\":" + RenderSettings.fogDensity.ToString("F3", System.Globalization.CultureInfo.InvariantCulture) + ","
                + "\"ambientLight\":\"#" + ColorUtility.ToHtmlStringRGBA(RenderSettings.ambientLight) + "\""
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
