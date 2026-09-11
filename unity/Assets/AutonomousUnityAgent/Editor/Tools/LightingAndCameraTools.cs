#pragma warning disable CS0618
using System;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class CreateLightTool : IBridgeTool
    {
        public string ToolName => "create_light";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string lightTypeStr = ToolParamHelper.ExtractString(request.parameters, "lightType", "Directional");
            LightType lightType;
            switch (lightTypeStr.ToLowerInvariant())
            {
                case "directional": lightType = LightType.Directional; break;
                case "point": lightType = LightType.Point; break;
                case "spot": lightType = LightType.Spot; break;
                default:
                    return BridgeMessage.Error(request.operationId, "INVALID_LIGHT_TYPE", $"Unknown light type: {lightTypeStr}");
            }

            string name = ToolParamHelper.ExtractString(request.parameters, "name", lightTypeStr + " Light");
            var go = new GameObject(name);
            Light light = go.AddComponent<Light>();
            light.type = lightType;

            string colorStr = ToolParamHelper.ExtractString(request.parameters, "color");
            if (!string.IsNullOrEmpty(colorStr))
            {
                light.color = ToolParamHelper.ParseColor(colorStr, Color.white);
            }

            float intensity = ToolParamHelper.ExtractFloat(request.parameters, "intensity", -1f);
            if (intensity >= 0f)
            {
                light.intensity = intensity;
            }

            float range = ToolParamHelper.ExtractFloat(request.parameters, "range", -1f);
            if (range >= 0f && (lightType == LightType.Point || lightType == LightType.Spot))
            {
                light.range = range;
            }

            // Transform
            if (request.parameters.Contains("\"position\""))
            {
                var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
                go.transform.position = p.position.ToVector3();
            }
            else if (lightType == LightType.Directional)
            {
                go.transform.position = new Vector3(0, 3, 0);
            }

            if (request.parameters.Contains("\"rotation\""))
            {
                var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
                go.transform.eulerAngles = p.rotation.ToVector3();
            }
            else if (lightType == LightType.Directional)
            {
                go.transform.eulerAngles = new Vector3(50, -30, 0);
            }

            Undo.RegisterCreatedObjectUndo(go, "Create Light " + name);

            var responseData = new GameObjectResponseData
            {
                instanceId = go.GetInstanceID(),
                name = go.name,
                position = Vector3Data.FromVector3(go.transform.position),
                rotation = Vector3Data.FromVector3(go.transform.eulerAngles),
                scale = Vector3Data.FromVector3(go.transform.localScale),
                parent = null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class CreateCameraTool : IBridgeTool
    {
        public string ToolName => "create_camera";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "Main Camera");
            bool isMain = ToolParamHelper.ExtractBool(request.parameters, "isMainCamera", true);

            var go = new GameObject(name);
            Camera cam = go.AddComponent<Camera>();

            if (isMain)
            {
                go.tag = "MainCamera";
                if (go.GetComponent<AudioListener>() == null)
                {
                    go.AddComponent<AudioListener>();
                }
            }

            float fov = ToolParamHelper.ExtractFloat(request.parameters, "fieldOfView", -1f);
            if (fov > 0f)
            {
                cam.fieldOfView = fov;
            }

            string bgColStr = ToolParamHelper.ExtractString(request.parameters, "backgroundColor");
            if (!string.IsNullOrEmpty(bgColStr))
            {
                cam.backgroundColor = ToolParamHelper.ParseColor(bgColStr, Color.black);
            }

            if (request.parameters.Contains("\"position\""))
            {
                var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
                go.transform.position = p.position.ToVector3();
            }
            else
            {
                go.transform.position = new Vector3(0, 1, -10);
            }

            if (request.parameters.Contains("\"rotation\""))
            {
                var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
                go.transform.eulerAngles = p.rotation.ToVector3();
            }

            Undo.RegisterCreatedObjectUndo(go, "Create Camera " + name);

            var responseData = new GameObjectResponseData
            {
                instanceId = go.GetInstanceID(),
                name = go.name,
                position = Vector3Data.FromVector3(go.transform.position),
                rotation = Vector3Data.FromVector3(go.transform.eulerAngles),
                scale = Vector3Data.FromVector3(go.transform.localScale),
                parent = null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }
}
