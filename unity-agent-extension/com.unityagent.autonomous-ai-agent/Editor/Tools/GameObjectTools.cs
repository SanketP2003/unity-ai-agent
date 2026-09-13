#pragma warning disable CS0618
using System;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    [Serializable]
    internal class PrimitiveParams
    {
        public string type;
        public string name;
        public Vector3Data position;
        public Vector3Data rotation;
        public Vector3Data scale;
        public string parent;
    }

    public class CreatePrimitiveTool : IBridgeTool
    {
        public string ToolName => "create_primitive";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
            if (string.IsNullOrEmpty(p.type))
            {
                p.type = ToolParamHelper.ExtractString(request.parameters, "type", "Cube");
            }

            PrimitiveType primType;
            switch (p.type.ToLowerInvariant())
            {
                case "cube": primType = PrimitiveType.Cube; break;
                case "sphere": primType = PrimitiveType.Sphere; break;
                case "capsule": primType = PrimitiveType.Capsule; break;
                case "cylinder": primType = PrimitiveType.Cylinder; break;
                case "plane": primType = PrimitiveType.Plane; break;
                case "quad": primType = PrimitiveType.Quad; break;
                default:
                    return BridgeMessage.Error(request.operationId, "INVALID_PRIMITIVE_TYPE", $"Unknown primitive type: {p.type}");
            }

            GameObject go = GameObject.CreatePrimitive(primType);
            string name = string.IsNullOrEmpty(p.name) ? p.type : p.name;
            go.name = name;

            // Apply transform
            Vector3 pos = p.position.ToVector3();
            Vector3 rot = p.rotation.ToVector3();
            Vector3 scale = p.scale.ToVector3();
            if (scale == Vector3.zero) scale = Vector3.one;

            go.transform.position = pos;
            go.transform.eulerAngles = rot;
            go.transform.localScale = scale;

            // Parent
            if (!string.IsNullOrEmpty(p.parent))
            {
                GameObject parentGo = ToolParamHelper.FindTarget(p.parent);
                if (parentGo != null)
                {
                    go.transform.SetParent(parentGo.transform, true);
                }
            }

            Undo.RegisterCreatedObjectUndo(go, "Create Primitive " + name);

            var responseData = new GameObjectResponseData
            {
                objectId = ToolParamHelper.FormatObjectId(go),
                instanceId = go.GetInstanceID(),
                name = go.name,
                position = Vector3Data.FromVector3(go.transform.position),
                rotation = Vector3Data.FromVector3(go.transform.eulerAngles),
                scale = Vector3Data.FromVector3(go.transform.localScale),
                parent = go.transform.parent != null ? go.transform.parent.name : null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    [Serializable]
    internal class EmptyGameObjectParams
    {
        public string name;
        public Vector3Data position;
        public Vector3Data rotation;
        public Vector3Data scale;
        public string parent;
    }

    public class CreateEmptyGameObjectTool : IBridgeTool
    {
        public string ToolName => "create_empty_gameobject";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var p = ToolParamHelper.Parse<EmptyGameObjectParams>(request.parameters);
            string name = string.IsNullOrEmpty(p.name) ? "GameObject" : p.name;

            GameObject go = new GameObject(name);

            Vector3 pos = p.position.ToVector3();
            Vector3 rot = p.rotation.ToVector3();
            Vector3 scale = p.scale.ToVector3();
            if (scale == Vector3.zero) scale = Vector3.one;

            go.transform.position = pos;
            go.transform.eulerAngles = rot;
            go.transform.localScale = scale;

            if (!string.IsNullOrEmpty(p.parent))
            {
                GameObject parentGo = ToolParamHelper.FindTarget(p.parent);
                if (parentGo != null)
                {
                    go.transform.SetParent(parentGo.transform, true);
                }
            }

            Undo.RegisterCreatedObjectUndo(go, "Create Empty GameObject " + name);

            var responseData = new GameObjectResponseData
            {
                objectId = ToolParamHelper.FormatObjectId(go),
                instanceId = go.GetInstanceID(),
                name = go.name,
                position = Vector3Data.FromVector3(go.transform.position),
                rotation = Vector3Data.FromVector3(go.transform.eulerAngles),
                scale = Vector3Data.FromVector3(go.transform.localScale),
                parent = go.transform.parent != null ? go.transform.parent.name : null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    [Serializable]
    internal class SetTransformParams
    {
        public string target;
        public Vector3Data position;
        public Vector3Data rotation;
        public Vector3Data scale;
        public string space;
    }

    public class SetTransformTool : IBridgeTool
    {
        public string ToolName => "set_transform";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var p = ToolParamHelper.Parse<SetTransformParams>(request.parameters);
            if (string.IsNullOrEmpty(p.target))
            {
                p.target = ToolParamHelper.ExtractString(request.parameters, "target");
            }

            GameObject go = ToolParamHelper.FindTarget(p.target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{p.target}'");
            }

            Undo.RecordObject(go.transform, "SetTransform " + go.name);

            bool isLocal = string.Equals(p.space, "Local", StringComparison.OrdinalIgnoreCase);

            if (request.parameters.Contains("\"position\""))
            {
                if (isLocal) go.transform.localPosition = p.position.ToVector3();
                else go.transform.position = p.position.ToVector3();
            }

            if (request.parameters.Contains("\"rotation\""))
            {
                if (isLocal) go.transform.localEulerAngles = p.rotation.ToVector3();
                else go.transform.eulerAngles = p.rotation.ToVector3();
            }

            if (request.parameters.Contains("\"scale\""))
            {
                go.transform.localScale = p.scale.ToVector3();
            }

            var responseData = new GameObjectResponseData
            {
                objectId = ToolParamHelper.FormatObjectId(go),
                instanceId = go.GetInstanceID(),
                name = go.name,
                position = Vector3Data.FromVector3(go.transform.position),
                rotation = Vector3Data.FromVector3(go.transform.eulerAngles),
                scale = Vector3Data.FromVector3(go.transform.localScale),
                parent = go.transform.parent != null ? go.transform.parent.name : null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class DestroyGameObjectTool : IBridgeTool
    {
        public string ToolName => "destroy_gameobject";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}' to destroy");
            }

            string name = go.name;
            Undo.DestroyObjectImmediate(go);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Destroyed GameObject '{name}'",
                target = name
            });

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class SetParentTool : IBridgeTool
    {
        public string ToolName => "set_parent";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string parent = ToolParamHelper.ExtractString(request.parameters, "parent");
            bool worldPositionStays = ToolParamHelper.ExtractBool(request.parameters, "worldPositionStays", true);

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find target GameObject '{target}'");
            }

            Transform newParentTransform = null;
            if (!string.IsNullOrEmpty(parent))
            {
                GameObject parentGo = ToolParamHelper.FindTarget(parent);
                if (parentGo == null)
                {
                    return BridgeMessage.Error(request.operationId, "PARENT_NOT_FOUND", $"Could not find parent GameObject '{parent}'");
                }
                newParentTransform = parentGo.transform;
            }

            Undo.SetTransformParent(go.transform, newParentTransform, "SetParent " + go.name);
            if (!worldPositionStays && newParentTransform != null)
            {
                go.transform.localPosition = Vector3.zero;
                go.transform.localRotation = Quaternion.identity;
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Set parent of '{go.name}' to '{(newParentTransform != null ? newParentTransform.name : "null")}'",
                target = go.name
            });

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateGameObjectTool : IBridgeTool
    {
        public string ToolName => "create_gameobject";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "GameObject");
            bool idempotent = ToolParamHelper.ExtractBool(request.parameters, "idempotent", false)
                || ToolParamHelper.ExtractBool(request.parameters, "findExisting", false);

            if (idempotent)
            {
                GameObject existing = ToolParamHelper.FindTarget(name);
                if (existing != null)
                {
                    var existingData = new GameObjectResponseData
                    {
                        objectId = ToolParamHelper.FormatObjectId(existing),
                        instanceId = existing.GetInstanceID(),
                        name = existing.name,
                        position = Vector3Data.FromVector3(existing.transform.position),
                        rotation = Vector3Data.FromVector3(existing.transform.eulerAngles),
                        scale = Vector3Data.FromVector3(existing.transform.localScale),
                        parent = existing.transform.parent != null ? existing.transform.parent.name : null
                    };
                    return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(existingData));
                }
            }

            string primTypeStr = ToolParamHelper.ExtractString(request.parameters, "primitiveType", null);
            GameObject go;
            if (!string.IsNullOrEmpty(primTypeStr) && !primTypeStr.Equals("none", StringComparison.OrdinalIgnoreCase))
            {
                PrimitiveType primType = PrimitiveType.Cube;
                switch (primTypeStr.ToLowerInvariant())
                {
                    case "sphere": primType = PrimitiveType.Sphere; break;
                    case "capsule": primType = PrimitiveType.Capsule; break;
                    case "cylinder": primType = PrimitiveType.Cylinder; break;
                    case "plane": primType = PrimitiveType.Plane; break;
                    case "quad": primType = PrimitiveType.Quad; break;
                    default: primType = PrimitiveType.Cube; break;
                }
                go = GameObject.CreatePrimitive(primType);
            }
            else
            {
                go = new GameObject();
            }

            go.name = name;

            var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
            go.transform.position = p.position.ToVector3();
            if (request.parameters.Contains("\"rotation\""))
            {
                go.transform.eulerAngles = p.rotation.ToVector3();
            }
            Vector3 scale = p.scale.ToVector3();
            if (scale != Vector3.zero)
            {
                go.transform.localScale = scale;
            }

            string parent = ToolParamHelper.ExtractString(request.parameters, "parent", null);
            if (!string.IsNullOrEmpty(parent))
            {
                GameObject parentGo = ToolParamHelper.FindTarget(parent);
                if (parentGo != null)
                {
                    go.transform.SetParent(parentGo.transform, true);
                }
            }

            string tag = ToolParamHelper.ExtractString(request.parameters, "tag", null);
            if (!string.IsNullOrEmpty(tag))
            {
                try { go.tag = tag; } catch { /* Ignore invalid tag */ }
            }

            string layerStr = ToolParamHelper.ExtractString(request.parameters, "layer", null);
            if (!string.IsNullOrEmpty(layerStr))
            {
                int layer = LayerMask.NameToLayer(layerStr);
                if (layer >= 0) go.layer = layer;
                else if (int.TryParse(layerStr, out int l)) go.layer = l;
            }

            bool active = ToolParamHelper.ExtractBool(request.parameters, "active", true);
            go.SetActive(active);

            Undo.RegisterCreatedObjectUndo(go, "Create GameObject " + name);

            var responseData = new GameObjectResponseData
            {
                objectId = ToolParamHelper.FormatObjectId(go),
                instanceId = go.GetInstanceID(),
                name = go.name,
                position = Vector3Data.FromVector3(go.transform.position),
                rotation = Vector3Data.FromVector3(go.transform.eulerAngles),
                scale = Vector3Data.FromVector3(go.transform.localScale),
                parent = go.transform.parent != null ? go.transform.parent.name : null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class DuplicateGameObjectTool : IBridgeTool
    {
        public string ToolName => "duplicate_gameobject";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}' to duplicate");
            }

            GameObject clone = UnityEngine.Object.Instantiate(go);
            string newName = ToolParamHelper.ExtractString(request.parameters, "newName", null);
            clone.name = string.IsNullOrEmpty(newName) ? go.name + "_Copy" : newName;

            if (go.transform.parent != null)
            {
                clone.transform.SetParent(go.transform.parent, true);
            }

            if (request.parameters.Contains("\"positionOffset\""))
            {
                var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
                clone.transform.position += p.position.ToVector3();
            }

            Undo.RegisterCreatedObjectUndo(clone, "Duplicate GameObject " + go.name);

            var responseData = new GameObjectResponseData
            {
                objectId = ToolParamHelper.FormatObjectId(clone),
                instanceId = clone.GetInstanceID(),
                name = clone.name,
                position = Vector3Data.FromVector3(clone.transform.position),
                rotation = Vector3Data.FromVector3(clone.transform.eulerAngles),
                scale = Vector3Data.FromVector3(clone.transform.localScale),
                parent = clone.transform.parent != null ? clone.transform.parent.name : null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class RenameGameObjectTool : IBridgeTool
    {
        public string ToolName => "rename_gameobject";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string newName = ToolParamHelper.ExtractString(request.parameters, "newName");
            if (string.IsNullOrEmpty(newName))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameter 'newName' is required");
            }

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}' to rename");
            }

            string oldName = go.name;
            Undo.RecordObject(go, "Rename GameObject " + oldName);
            go.name = newName;

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Renamed GameObject from '{oldName}' to '{newName}'",
                target = newName
            });

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class MoveGameObjectTool : IBridgeTool
    {
        public string ToolName => "move_gameobject";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}'");
            }

            bool isLocal = ToolParamHelper.ExtractBool(request.parameters, "isLocal", false);
            Undo.RecordObject(go.transform, "Move GameObject " + go.name);

            if (request.parameters.Contains("\"deltaPosition\""))
            {
                float dx = ToolParamHelper.ExtractFloat(request.parameters, "dx", 0f);
                float dy = ToolParamHelper.ExtractFloat(request.parameters, "dy", 0f);
                float dz = ToolParamHelper.ExtractFloat(request.parameters, "dz", 0f);
                Vector3 delta = new Vector3(dx, dy, dz);
                if (isLocal) go.transform.localPosition += delta;
                else go.transform.position += delta;
            }
            else if (request.parameters.Contains("\"position\""))
            {
                var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
                if (isLocal) go.transform.localPosition = p.position.ToVector3();
                else go.transform.position = p.position.ToVector3();
            }

            var responseData = new GameObjectResponseData
            {
                objectId = ToolParamHelper.FormatObjectId(go),
                instanceId = go.GetInstanceID(),
                name = go.name,
                position = Vector3Data.FromVector3(go.transform.position),
                rotation = Vector3Data.FromVector3(go.transform.eulerAngles),
                scale = Vector3Data.FromVector3(go.transform.localScale),
                parent = go.transform.parent != null ? go.transform.parent.name : null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class FindGameObjectsTool : IBridgeTool
    {
        public string ToolName => "find_gameobjects";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string namePattern = ToolParamHelper.ExtractString(request.parameters, "namePattern", null);
            string tag = ToolParamHelper.ExtractString(request.parameters, "tag", null);
            string layerStr = ToolParamHelper.ExtractString(request.parameters, "layer", null);
            string compType = ToolParamHelper.ExtractString(request.parameters, "componentType", null);

            var allObjects = UnityEngine.Object.FindObjectsOfType<GameObject>();
            var matched = new System.Collections.Generic.List<string>();

            int targetLayer = -1;
            if (!string.IsNullOrEmpty(layerStr))
            {
                targetLayer = LayerMask.NameToLayer(layerStr);
                if (targetLayer < 0) int.TryParse(layerStr, out targetLayer);
            }

            Type compResolved = null;
            if (!string.IsNullOrEmpty(compType))
            {
                compResolved = AddComponentTool.ResolveComponentType(compType);
            }

            foreach (var go in allObjects)
            {
                if (!string.IsNullOrEmpty(namePattern) && !go.name.Contains(namePattern, StringComparison.OrdinalIgnoreCase))
                    continue;
                if (!string.IsNullOrEmpty(tag) && !go.CompareTag(tag))
                    continue;
                if (targetLayer >= 0 && go.layer != targetLayer)
                    continue;
                if (compResolved != null && go.GetComponent(compResolved) == null)
                    continue;

                matched.Add(go.name);
            }

            string json = "{"
                + "\"count\":" + matched.Count + ","
                + "\"objects\":[" + string.Join(",", matched.ConvertAll(n => "\"" + n + "\"")) + "]"
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
