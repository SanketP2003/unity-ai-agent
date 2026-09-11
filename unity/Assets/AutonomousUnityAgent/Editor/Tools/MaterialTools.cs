using System;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class SetMaterialColorTool : IBridgeTool
    {
        public string ToolName => "set_material_color";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}'");
            }

            Renderer renderer = go.GetComponent<Renderer>();
            if (renderer == null)
            {
                return BridgeMessage.Error(request.operationId, "NO_RENDERER", $"GameObject '{target}' does not have a Renderer component");
            }

            string colorStr = ToolParamHelper.ExtractString(request.parameters, "color");
            Color color = Color.white;

            if (!string.IsNullOrEmpty(colorStr) && colorStr.StartsWith("#"))
            {
                ColorUtility.TryParseHtmlString(colorStr, out color);
            }
            else if (request.parameters.Contains("\"color\":{"))
            {
                var colData = ToolParamHelper.Parse<ColorData>(request.parameters);
                color = colData.ToColor();
            }

            Undo.RecordObject(renderer, "Set Material Color " + go.name);
            renderer.material.color = color;

            var responseData = new MaterialResponseData
            {
                target = go.name,
                materialName = renderer.material.name,
                assetPath = null,
                colorHex = "#" + ColorUtility.ToHtmlStringRGBA(color)
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class CreateMaterialTool : IBridgeTool
    {
        public string ToolName => "create_material";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "NewMaterial");
            string shaderName = ToolParamHelper.ExtractString(request.parameters, "shader");

            Shader shader = null;
            if (!string.IsNullOrEmpty(shaderName))
            {
                shader = Shader.Find(shaderName);
            }

            if (shader == null)
            {
                // Fallback sequence: URP Lit -> Standard -> Diffuse
                shader = Shader.Find("Universal Render Pipeline/Lit") ??
                         Shader.Find("Standard") ??
                         Shader.Find("Diffuse") ??
                         Shader.Find("Unlit/Color");
            }

            if (shader == null)
            {
                return BridgeMessage.Error(request.operationId, "SHADER_NOT_FOUND", "Could not find a suitable default shader (URP Lit, Standard, or Diffuse)");
            }

            var material = new Material(shader);
            material.name = name;

            string colorStr = ToolParamHelper.ExtractString(request.parameters, "color");
            if (!string.IsNullOrEmpty(colorStr))
            {
                Color color = ToolParamHelper.ParseColor(colorStr, Color.white);
                material.color = color;
            }

            float metallic = ToolParamHelper.ExtractFloat(request.parameters, "metallic", -1f);
            if (metallic >= 0f && material.HasProperty("_Metallic"))
            {
                material.SetFloat("_Metallic", metallic);
            }

            float smoothness = ToolParamHelper.ExtractFloat(request.parameters, "smoothness", -1f);
            if (smoothness >= 0f && material.HasProperty("_Smoothness"))
            {
                material.SetFloat("_Smoothness", smoothness);
            }
            else if (smoothness >= 0f && material.HasProperty("_Glossiness"))
            {
                material.SetFloat("_Glossiness", smoothness);
            }

            if (!AssetDatabase.IsValidFolder("Assets/Materials"))
            {
                AssetDatabase.CreateFolder("Assets", "Materials");
            }

            string assetPath = $"Assets/Materials/{name}.mat";
            AssetDatabase.CreateAsset(material, assetPath);
            AssetDatabase.SaveAssets();

            var responseData = new MaterialResponseData
            {
                target = null,
                materialName = name,
                assetPath = assetPath,
                colorHex = material.HasProperty("_Color") ? "#" + ColorUtility.ToHtmlStringRGBA(material.color) : null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }

    public class ApplyMaterialTool : IBridgeTool
    {
        public string ToolName => "apply_material";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string materialPath = ToolParamHelper.ExtractString(request.parameters, "materialPath");

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}'");
            }

            Renderer renderer = go.GetComponent<Renderer>();
            if (renderer == null)
            {
                return BridgeMessage.Error(request.operationId, "NO_RENDERER", $"GameObject '{target}' does not have a Renderer component");
            }

            if (!materialPath.StartsWith("Assets/"))
            {
                materialPath = "Assets/Materials/" + materialPath;
            }
            if (!materialPath.EndsWith(".mat", StringComparison.OrdinalIgnoreCase))
            {
                materialPath += ".mat";
            }

            var mat = AssetDatabase.LoadAssetAtPath<Material>(materialPath);
            if (mat == null)
            {
                return BridgeMessage.Error(request.operationId, "MATERIAL_NOT_FOUND", $"Could not load Material at path '{materialPath}'");
            }

            Undo.RecordObject(renderer, "Apply Material " + mat.name);
            renderer.sharedMaterial = mat;

            var responseData = new MaterialResponseData
            {
                target = go.name,
                materialName = mat.name,
                assetPath = materialPath,
                colorHex = mat.HasProperty("_Color") ? "#" + ColorUtility.ToHtmlStringRGBA(mat.color) : null
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }
}
