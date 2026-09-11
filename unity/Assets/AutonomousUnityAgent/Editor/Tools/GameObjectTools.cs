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
}
