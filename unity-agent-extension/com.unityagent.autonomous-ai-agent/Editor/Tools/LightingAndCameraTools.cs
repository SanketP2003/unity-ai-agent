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

    public class ConfigureLightTool : IBridgeTool
    {
        public string ToolName => "configure_light";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find Light GameObject '{target}'");
            }

            Light light = go.GetComponent<Light>();
            if (light == null)
            {
                return BridgeMessage.Error(request.operationId, "NO_LIGHT", $"GameObject '{target}' does not have a Light component");
            }

            Undo.RecordObject(light, "Configure Light " + go.name);

            string colorStr = ToolParamHelper.ExtractString(request.parameters, "color");
            if (!string.IsNullOrEmpty(colorStr) && ColorUtility.TryParseHtmlString(colorStr, out Color col))
            {
                light.color = col;
            }

            float intensity = ToolParamHelper.ExtractFloat(request.parameters, "intensity", -1f);
            if (intensity >= 0f) light.intensity = intensity;

            float range = ToolParamHelper.ExtractFloat(request.parameters, "range", -1f);
            if (range >= 0f) light.range = range;

            string shadows = ToolParamHelper.ExtractString(request.parameters, "shadows");
            if (!string.IsNullOrEmpty(shadows))
            {
                if (shadows.Equals("Soft", StringComparison.OrdinalIgnoreCase)) light.shadows = LightShadows.Soft;
                else if (shadows.Equals("Hard", StringComparison.OrdinalIgnoreCase)) light.shadows = LightShadows.Hard;
                else if (shadows.Equals("None", StringComparison.OrdinalIgnoreCase)) light.shadows = LightShadows.None;
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured light '{go.name}'",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetLightsTool : IBridgeTool
    {
        public string ToolName => "get_lights";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var lights = UnityEngine.Object.FindObjectsOfType<Light>();
            var lightList = new System.Collections.Generic.List<string>();

            foreach (var l in lights)
            {
                lightList.Add("{"
                    + "\"name\":\"" + l.gameObject.name + "\","
                    + "\"type\":\"" + l.type.ToString() + "\","
                    + "\"color\":\"#" + ColorUtility.ToHtmlStringRGBA(l.color) + "\","
                    + "\"intensity\":" + l.intensity.ToString("F2", System.Globalization.CultureInfo.InvariantCulture) + ","
                    + "\"range\":" + l.range.ToString("F2", System.Globalization.CultureInfo.InvariantCulture)
                    + "}");
            }

            string json = "{"
                + "\"count\":" + lights.Length + ","
                + "\"lights\":[" + string.Join(",", lightList) + "]"
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ConfigureEnvironmentLightingTool : IBridgeTool
    {
        public string ToolName => "configure_environment_lighting";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string colorStr = ToolParamHelper.ExtractString(request.parameters, "ambientColor");
            if (!string.IsNullOrEmpty(colorStr) && ColorUtility.TryParseHtmlString(colorStr, out Color col))
            {
                RenderSettings.ambientLight = col;
            }

            float intensity = ToolParamHelper.ExtractFloat(request.parameters, "ambientIntensity", -1f);
            if (intensity >= 0f)
            {
                RenderSettings.ambientIntensity = intensity;
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = "Environment lighting configured",
                target = "RenderSettings"
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ConfigureFogTool : IBridgeTool
    {
        public string ToolName => "configure_fog";

        public BridgeMessage Execute(BridgeMessage request)
        {
            bool enabled = ToolParamHelper.ExtractBool(request.parameters, "enabled", true);
            RenderSettings.fog = enabled;

            string colorStr = ToolParamHelper.ExtractString(request.parameters, "fogColor");
            if (!string.IsNullOrEmpty(colorStr) && ColorUtility.TryParseHtmlString(colorStr, out Color col))
            {
                RenderSettings.fogColor = col;
            }

            float density = ToolParamHelper.ExtractFloat(request.parameters, "fogDensity", -1f);
            if (density >= 0f)
            {
                RenderSettings.fogDensity = density;
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Fog set to {(enabled ? "enabled" : "disabled")}",
                target = "RenderSettings.fog"
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ConfigureCameraTool : IBridgeTool
    {
        public string ToolName => "configure_camera";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target", "Main Camera");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Camera '{target}' not found");
            }

            Camera cam = go.GetComponent<Camera>();
            if (cam == null)
            {
                return BridgeMessage.Error(request.operationId, "NO_CAMERA", $"'{target}' does not have a Camera component");
            }

            Undo.RecordObject(cam, "Configure Camera " + go.name);

            float fov = ToolParamHelper.ExtractFloat(request.parameters, "fieldOfView", -1f);
            if (fov > 0f) cam.fieldOfView = fov;

            float near = ToolParamHelper.ExtractFloat(request.parameters, "nearClipPlane", -1f);
            if (near > 0f) cam.nearClipPlane = near;

            float far = ToolParamHelper.ExtractFloat(request.parameters, "farClipPlane", -1f);
            if (far > 0f) cam.farClipPlane = far;

            if (request.parameters.Contains("\"orthographic\""))
            {
                cam.orthographic = ToolParamHelper.ExtractBool(request.parameters, "orthographic", false);
            }

            float orthoSize = ToolParamHelper.ExtractFloat(request.parameters, "orthographicSize", -1f);
            if (orthoSize > 0f) cam.orthographicSize = orthoSize;

            string bgStr = ToolParamHelper.ExtractString(request.parameters, "backgroundColor");
            if (!string.IsNullOrEmpty(bgStr) && ColorUtility.TryParseHtmlString(bgStr, out Color bg))
            {
                cam.backgroundColor = bg;
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured Camera '{go.name}'",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class SetActiveCameraTool : IBridgeTool
    {
        public string ToolName => "set_active_camera";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find Camera '{target}'");
            }

            Camera cam = go.GetComponent<Camera>();
            if (cam == null)
            {
                return BridgeMessage.Error(request.operationId, "NO_CAMERA", $"'{target}' does not have Camera component");
            }

            bool isMain = ToolParamHelper.ExtractBool(request.parameters, "isMainCamera", true);
            if (isMain)
            {
                // Remove MainCamera tag from other cameras
                var cams = UnityEngine.Object.FindObjectsOfType<Camera>();
                foreach (var c in cams)
                {
                    if (c.CompareTag("MainCamera")) c.tag = "Untagged";
                }
                go.tag = "MainCamera";
            }
            cam.enabled = true;

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Set '{go.name}' as active camera (isMainCamera={isMain})",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class FollowTargetTool : IBridgeTool
    {
        public string ToolName => "follow_target";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string cameraName = ToolParamHelper.ExtractString(request.parameters, "camera", "Main Camera");
            string targetName = ToolParamHelper.ExtractString(request.parameters, "target");

            GameObject camGo = ToolParamHelper.FindTarget(cameraName);
            if (camGo == null) return BridgeMessage.Error(request.operationId, "CAMERA_NOT_FOUND", $"Camera '{cameraName}' not found");

            GameObject targetGo = ToolParamHelper.FindTarget(targetName);
            if (targetGo == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Target '{targetName}' not found");

            float ox = ToolParamHelper.ExtractFloat(request.parameters, "offsetX", 0f);
            float oy = ToolParamHelper.ExtractFloat(request.parameters, "offsetY", 5f);
            float oz = ToolParamHelper.ExtractFloat(request.parameters, "offsetZ", -10f);

            // Parent camera or set offset position looking at target
            Undo.RecordObject(camGo.transform, "Follow Target " + targetGo.name);
            camGo.transform.position = targetGo.transform.position + new Vector3(ox, oy, oz);
            camGo.transform.LookAt(targetGo.transform.position);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Positioned camera '{camGo.name}' to track '{targetGo.name}' with offset ({ox},{oy},{oz})",
                target = camGo.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class LookAtTargetTool : IBridgeTool
    {
        public string ToolName => "look_at_target";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string cameraName = ToolParamHelper.ExtractString(request.parameters, "camera", null)
                ?? ToolParamHelper.ExtractString(request.parameters, "cameraTarget", null)
                ?? ToolParamHelper.ExtractString(request.parameters, "cameraName", "Main Camera");
            string targetName = ToolParamHelper.ExtractString(request.parameters, "target", null)
                ?? ToolParamHelper.ExtractString(request.parameters, "lookAtTarget", null)
                ?? ToolParamHelper.ExtractString(request.parameters, "targetName", null);

            GameObject camGo = ToolParamHelper.FindTarget(cameraName);
            if (camGo == null) return BridgeMessage.Error(request.operationId, "CAMERA_NOT_FOUND", $"Camera '{cameraName}' not found");

            GameObject targetGo = ToolParamHelper.FindTarget(targetName);
            if (targetGo == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Target '{targetName}' not found");

            Undo.RecordObject(camGo.transform, "LookAt " + targetGo.name);
            camGo.transform.LookAt(targetGo.transform);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Camera '{camGo.name}' aimed at '{targetGo.name}'",
                target = camGo.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetCameraInfoTool : IBridgeTool
    {
        public string ToolName => "get_camera_info";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target", null);
            Camera cam = null;
            if (!string.IsNullOrEmpty(target))
            {
                GameObject go = ToolParamHelper.FindTarget(target);
                if (go != null) cam = go.GetComponent<Camera>();
            }
            if (cam == null) cam = Camera.main ?? UnityEngine.Object.FindObjectOfType<Camera>();

            if (cam == null)
            {
                return BridgeMessage.Error(request.operationId, "NO_CAMERA", "No Camera found in scene");
            }

            string json = "{"
                + "\"cameraName\":\"" + cam.gameObject.name + "\","
                + "\"tag\":\"" + cam.gameObject.tag + "\","
                + "\"fieldOfView\":" + cam.fieldOfView.ToString("F1", System.Globalization.CultureInfo.InvariantCulture) + ","
                + "\"orthographic\":" + (cam.orthographic ? "true" : "false") + ","
                + "\"nearClipPlane\":" + cam.nearClipPlane.ToString("F2", System.Globalization.CultureInfo.InvariantCulture) + ","
                + "\"farClipPlane\":" + cam.farClipPlane.ToString("F1", System.Globalization.CultureInfo.InvariantCulture) + ","
                + "\"position\":\"" + cam.transform.position.ToString() + "\""
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
