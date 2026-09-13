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
            Material mat = renderer.sharedMaterial != null
                ? new Material(renderer.sharedMaterial)
                : new Material(Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard") ?? Shader.Find("Diffuse") ?? Shader.Find("Unlit/Color"));
            mat.color = color;
            renderer.sharedMaterial = mat;

            var responseData = new MaterialResponseData
            {
                target = go.name,
                materialName = mat.name,
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
            if (string.IsNullOrEmpty(materialPath)) materialPath = ToolParamHelper.ExtractString(request.parameters, "materialAssetPath");
            if (string.IsNullOrEmpty(materialPath)) materialPath = ToolParamHelper.ExtractString(request.parameters, "materialName");
            if (string.IsNullOrEmpty(materialPath)) materialPath = ToolParamHelper.ExtractString(request.parameters, "path");

            if (string.IsNullOrEmpty(materialPath))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameter 'materialPath' or 'materialAssetPath' is required");
            }

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

    public class SetMaterialPropertyTool : IBridgeTool
    {
        public string ToolName => "set_material_property";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string materialPath = ToolParamHelper.ExtractString(request.parameters, "materialPath");
            if (string.IsNullOrEmpty(materialPath)) materialPath = ToolParamHelper.ExtractString(request.parameters, "materialAssetPath");
            string propertyName = ToolParamHelper.ExtractString(request.parameters, "property");
            if (string.IsNullOrEmpty(propertyName)) propertyName = ToolParamHelper.ExtractString(request.parameters, "propertyName");

            Material mat = null;
            if (!string.IsNullOrEmpty(materialPath))
            {
                if (!materialPath.StartsWith("Assets/")) materialPath = "Assets/Materials/" + materialPath;
                if (!materialPath.EndsWith(".mat", StringComparison.OrdinalIgnoreCase)) materialPath += ".mat";
                mat = AssetDatabase.LoadAssetAtPath<Material>(materialPath);
            }
            else if (!string.IsNullOrEmpty(target))
            {
                GameObject go = ToolParamHelper.FindTarget(target);
                if (go != null)
                {
                    Renderer r = go.GetComponent<Renderer>();
                    if (r != null) mat = r.sharedMaterial;
                }
            }

            if (mat == null)
            {
                return BridgeMessage.Error(request.operationId, "MATERIAL_NOT_FOUND", "Could not resolve Material from target or materialPath");
            }

            Undo.RecordObject(mat, "Set Material Property " + propertyName);

            string colorStr = ToolParamHelper.ExtractString(request.parameters, "color");
            if (!string.IsNullOrEmpty(colorStr) && ColorUtility.TryParseHtmlString(colorStr, out Color col))
            {
                if (mat.HasProperty(propertyName)) mat.SetColor(propertyName, col);
                else mat.color = col;
            }
            else
            {
                float floatVal = ToolParamHelper.ExtractFloat(request.parameters, "value", -9999f);
                if (floatVal != -9999f)
                {
                    mat.SetFloat(propertyName, floatVal);
                }
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Set property '{propertyName}' on material '{mat.name}'",
                target = mat.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetMaterialPropertiesTool : IBridgeTool
    {
        public string ToolName => "get_material_properties";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string materialPath = ToolParamHelper.ExtractString(request.parameters, "materialPath");
            if (string.IsNullOrEmpty(materialPath)) materialPath = ToolParamHelper.ExtractString(request.parameters, "materialAssetPath");

            Material mat = null;
            if (!string.IsNullOrEmpty(materialPath))
            {
                if (!materialPath.StartsWith("Assets/")) materialPath = "Assets/Materials/" + materialPath;
                if (!materialPath.EndsWith(".mat", StringComparison.OrdinalIgnoreCase)) materialPath += ".mat";
                mat = AssetDatabase.LoadAssetAtPath<Material>(materialPath);
            }
            else if (!string.IsNullOrEmpty(target))
            {
                GameObject go = ToolParamHelper.FindTarget(target);
                if (go != null)
                {
                    Renderer r = go.GetComponent<Renderer>();
                    if (r != null) mat = r.sharedMaterial;
                }
            }

            if (mat == null)
            {
                return BridgeMessage.Error(request.operationId, "MATERIAL_NOT_FOUND", "Could not find Material to inspect");
            }

            string colorHex = mat.HasProperty("_Color") ? "#" + ColorUtility.ToHtmlStringRGBA(mat.color) :
                              mat.HasProperty("_BaseColor") ? "#" + ColorUtility.ToHtmlStringRGBA(mat.GetColor("_BaseColor")) : "#FFFFFF";

            string json = "{"
                + "\"materialName\":\"" + mat.name + "\","
                + "\"shader\":\"" + (mat.shader != null ? mat.shader.name : "null") + "\","
                + "\"color\":\"" + colorHex + "\","
                + "\"metallic\":" + (mat.HasProperty("_Metallic") ? mat.GetFloat("_Metallic").ToString("F2", System.Globalization.CultureInfo.InvariantCulture) : "0") + ","
                + "\"smoothness\":" + (mat.HasProperty("_Smoothness") ? mat.GetFloat("_Smoothness").ToString("F2", System.Globalization.CultureInfo.InvariantCulture) : "0")
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class AssignMaterialTool : IBridgeTool
    {
        public string ToolName => "assign_material";

        public BridgeMessage Execute(BridgeMessage request)
        {
            // Delegate to ApplyMaterialTool logic
            var applyTool = new ApplyMaterialTool();
            return applyTool.Execute(request);
        }
    }

    public class SetRendererMaterialTool : IBridgeTool
    {
        public string ToolName => "set_renderer_material";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            int slot = (int)ToolParamHelper.ExtractFloat(request.parameters, "slotIndex", 0f);
            string materialPath = ToolParamHelper.ExtractString(request.parameters, "materialPath");
            if (string.IsNullOrEmpty(materialPath)) materialPath = ToolParamHelper.ExtractString(request.parameters, "materialAssetPath");
            if (string.IsNullOrEmpty(materialPath)) materialPath = ToolParamHelper.ExtractString(request.parameters, "materialName");
            if (string.IsNullOrEmpty(materialPath)) materialPath = ToolParamHelper.ExtractString(request.parameters, "path");

            if (string.IsNullOrEmpty(materialPath))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameter 'materialPath' or 'materialAssetPath' is required");
            }

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}'");
            }

            Renderer renderer = go.GetComponent<Renderer>();
            if (renderer == null)
            {
                return BridgeMessage.Error(request.operationId, "NO_RENDERER", $"GameObject '{target}' does not have a Renderer");
            }

            if (!materialPath.StartsWith("Assets/")) materialPath = "Assets/Materials/" + materialPath;
            if (!materialPath.EndsWith(".mat", StringComparison.OrdinalIgnoreCase)) materialPath += ".mat";

            Material mat = AssetDatabase.LoadAssetAtPath<Material>(materialPath);
            if (mat == null)
            {
                return BridgeMessage.Error(request.operationId, "MATERIAL_NOT_FOUND", $"Could not load Material at '{materialPath}'");
            }

            Undo.RecordObject(renderer, "Set Renderer Material Slot " + slot);
            Material[] mats = renderer.sharedMaterials;
            if (slot >= 0 && slot < mats.Length)
            {
                mats[slot] = mat;
                renderer.sharedMaterials = mats;
            }
            else
            {
                renderer.sharedMaterial = mat;
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Assigned material '{mat.name}' to slot {slot} on '{go.name}'",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateShaderGraphAssetTool : IBridgeTool
    {
        public string ToolName => "create_shader_graph_asset";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "CustomShaderMaterial");
            string shaderName = ToolParamHelper.ExtractString(request.parameters, "shaderName", "Universal Render Pipeline/Lit");

            Shader shader = Shader.Find(shaderName) ?? Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
            Material mat = new Material(shader);

            if (!AssetDatabase.IsValidFolder("Assets/Materials"))
            {
                AssetDatabase.CreateFolder("Assets", "Materials");
            }

            string path = $"Assets/Materials/{name}.mat";
            AssetDatabase.CreateAsset(mat, path);
            AssetDatabase.SaveAssets();

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created shader asset material at '{path}' with shader '{shader.name}'",
                target = path
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
