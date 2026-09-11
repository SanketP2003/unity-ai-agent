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
            string setupStr = ToolParamHelper.ExtractString(request.parameters, "setup", "DefaultGame");
            NewSceneSetup setup = setupStr == "EmptyScene" ? NewSceneSetup.EmptyScene : NewSceneSetup.DefaultGame;

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
            Scene activeScene = SceneManager.GetActiveScene();
            string scenePath = ToolParamHelper.ExtractString(request.parameters, "scenePath", null);

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
}
