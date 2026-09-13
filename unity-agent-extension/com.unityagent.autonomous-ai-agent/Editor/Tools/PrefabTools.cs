using System;
using System.IO;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class CreatePrefabTool : IBridgeTool
    {
        public string ToolName => "create_prefab";

        internal static string ResolvePrefabPath(string parameters)
        {
            string path = ToolParamHelper.ExtractString(parameters, "prefabAssetPath");
            if (string.IsNullOrEmpty(path)) path = ToolParamHelper.ExtractString(parameters, "assetPath");
            if (string.IsNullOrEmpty(path)) path = ToolParamHelper.ExtractString(parameters, "path");
            if (string.IsNullOrEmpty(path))
            {
                string name = ToolParamHelper.ExtractString(parameters, "name");
                if (!string.IsNullOrEmpty(name))
                {
                    string[] guids = AssetDatabase.FindAssets(name + " t:Prefab");
                    if (guids != null && guids.Length > 0) path = AssetDatabase.GUIDToAssetPath(guids[0]);
                }
            }
            if (!string.IsNullOrEmpty(path) && !path.EndsWith(".prefab", StringComparison.OrdinalIgnoreCase))
            {
                path += ".prefab";
            }
            return path;
        }

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string path = ResolvePrefabPath(request.parameters);

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}' to create prefab");
            }

            if (string.IsNullOrEmpty(path))
            {
                path = "Assets/Prefabs/" + go.name + ".prefab";
            }

            string dir = Path.GetDirectoryName(path);
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
                AssetDatabase.Refresh();
            }

            bool success;
            GameObject prefab = PrefabUtility.SaveAsPrefabAssetAndConnect(go, path, InteractionMode.UserAction, out success);
            if (!success || prefab == null)
            {
                return BridgeMessage.Error(request.operationId, "PREFAB_SAVE_FAILED", $"Failed to save prefab to '{path}'");
            }

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Successfully created prefab for '{go.name}' at '{path}'",
                target = path
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class OpenPrefabTool : IBridgeTool
    {
        public string ToolName => "open_prefab";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string path = CreatePrefabTool.ResolvePrefabPath(request.parameters);

            if (string.IsNullOrEmpty(path) || !File.Exists(path))
            {
                return BridgeMessage.Error(request.operationId, "PREFAB_NOT_FOUND", $"Prefab not found at '{path}'");
            }

            bool opened = UnityEditor.SceneManagement.PrefabStageUtility.OpenPrefab(path);
            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = opened ? $"Opened prefab stage for '{path}'" : $"Could not open prefab '{path}'",
                target = path
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, opened, json);
        }
    }

    public class UpdatePrefabTool : IBridgeTool
    {
        public string ToolName => "update_prefab";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string path = CreatePrefabTool.ResolvePrefabPath(request.parameters);
            if (string.IsNullOrEmpty(path) || !File.Exists(path))
            {
                return BridgeMessage.Error(request.operationId, "PREFAB_NOT_FOUND", $"Prefab not found at '{path}'");
            }

            GameObject prefabRoot = PrefabUtility.LoadPrefabContents(path);
            if (prefabRoot == null)
            {
                return BridgeMessage.Error(request.operationId, "LOAD_FAILED", $"Failed to load prefab contents from '{path}'");
            }

            try
            {
                if (request.parameters.Contains("\"scale\""))
                {
                    var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
                    prefabRoot.transform.localScale = p.scale.ToVector3();
                }
                PrefabUtility.SaveAsPrefabAsset(prefabRoot, path);
            }
            finally
            {
                PrefabUtility.UnloadPrefabContents(prefabRoot);
            }

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Successfully updated prefab asset at '{path}'",
                target = path
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class InstantiatePrefabTool : IBridgeTool
    {
        public string ToolName => "instantiate_prefab";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string path = CreatePrefabTool.ResolvePrefabPath(request.parameters);

            if (string.IsNullOrEmpty(path) || !File.Exists(path))
            {
                return BridgeMessage.Error(request.operationId, "PREFAB_NOT_FOUND", $"Prefab not found at '{path}'");
            }

            GameObject prefabAsset = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (prefabAsset == null)
            {
                return BridgeMessage.Error(request.operationId, "LOAD_FAILED", $"Failed to load GameObject from '{path}'");
            }

            GameObject instance = (GameObject)PrefabUtility.InstantiatePrefab(prefabAsset);
            string instanceName = ToolParamHelper.ExtractString(request.parameters, "instanceName", null);
            if (!string.IsNullOrEmpty(instanceName))
            {
                instance.name = instanceName;
            }

            var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
            instance.transform.position = p.position.ToVector3();
            if (request.parameters.Contains("\"rotation\""))
            {
                instance.transform.eulerAngles = p.rotation.ToVector3();
            }

            string parent = ToolParamHelper.ExtractString(request.parameters, "parent", null);
            if (!string.IsNullOrEmpty(parent))
            {
                GameObject parentGo = ToolParamHelper.FindTarget(parent);
                if (parentGo != null)
                {
                    instance.transform.SetParent(parentGo.transform, true);
                }
            }

            Undo.RegisterCreatedObjectUndo(instance, "Instantiate Prefab " + instance.name);

            var responseData = new GameObjectResponseData
            {
                objectId = ToolParamHelper.FormatObjectId(instance),
                instanceId = instance.GetInstanceID(),
                name = instance.name,
                position = Vector3Data.FromVector3(instance.transform.position),
                rotation = Vector3Data.FromVector3(instance.transform.eulerAngles),
                scale = Vector3Data.FromVector3(instance.transform.localScale),
                parent = instance.transform.parent != null ? instance.transform.parent.name : null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class GetPrefabInfoTool : IBridgeTool
    {
        public string ToolName => "get_prefab_info";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string path = CreatePrefabTool.ResolvePrefabPath(request.parameters);

            if (string.IsNullOrEmpty(path) || !File.Exists(path))
            {
                return BridgeMessage.Error(request.operationId, "PREFAB_NOT_FOUND", $"Prefab not found at '{path}'");
            }

            GameObject prefab = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (prefab == null)
            {
                return BridgeMessage.Error(request.operationId, "LOAD_FAILED", $"Failed to load prefab at '{path}'");
            }

            var comps = prefab.GetComponents<Component>();
            var compNames = new System.Collections.Generic.List<string>();
            foreach (var c in comps) if (c != null) compNames.Add(c.GetType().Name);

            string json = "{"
                + "\"name\":\"" + prefab.name + "\","
                + "\"path\":\"" + path + "\","
                + "\"childCount\":" + prefab.transform.childCount + ","
                + "\"components\":[" + string.Join(",", compNames.ConvertAll(cn => "\"" + cn + "\"")) + "]"
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ApplyPrefabChangesTool : IBridgeTool
    {
        public string ToolName => "apply_prefab_changes";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find instance '{target}'");
            }

            if (!PrefabUtility.IsPartOfPrefabInstance(go))
            {
                return BridgeMessage.Error(request.operationId, "NOT_A_PREFAB_INSTANCE", $"GameObject '{target}' is not part of a prefab instance");
            }

            PrefabUtility.ApplyPrefabInstance(go, InteractionMode.UserAction);
            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Applied prefab changes from '{go.name}' to asset source",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
