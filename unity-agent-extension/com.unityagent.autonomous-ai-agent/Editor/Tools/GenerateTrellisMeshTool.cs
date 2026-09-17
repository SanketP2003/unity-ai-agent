#pragma warning disable CS0618
using System;
using System.IO;
using System.Net;
using System.Text;
using System.Diagnostics;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;
using Debug = UnityEngine.Debug;

namespace AutonomousUnityAgent.Editor.Tools
{
    /// <summary>
    /// Autonomous tool for generating high-detail 3D assets via Microsoft TRELLIS / TRELLIS 2
    /// from text prompts or images, and instantiating them directly into the Unity active scene.
    /// </summary>
    public class GenerateTrellisMeshTool : IBridgeTool
    {
        public string ToolName => "generate_trellis_mesh";

        private const string MicroserviceUrl = "http://127.0.0.1:8765/generate";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string prompt = ToolParamHelper.ExtractString(request.parameters, "prompt", "");
            string imagePath = ToolParamHelper.ExtractString(request.parameters, "image_path",
                ToolParamHelper.ExtractString(request.parameters, "imagePath", ""));
            string assetName = ToolParamHelper.ExtractString(request.parameters, "asset_name",
                ToolParamHelper.ExtractString(request.parameters, "assetName", "TrellisAsset"));

            // Clean asset name for valid filename and GameObject name
            assetName = CleanFileName(assetName);
            if (string.IsNullOrEmpty(assetName)) assetName = "TrellisModel_" + DateTime.Now.ToString("HHmmss");

            if (string.IsNullOrEmpty(prompt) && string.IsNullOrEmpty(imagePath))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM",
                    "Parameter 'prompt' or 'image_path' is required for TRELLIS 3D generation.");
            }

            // Transform parameters
            var p = ToolParamHelper.Parse<PrimitiveParams>(request.parameters);
            Vector3 pos = p.position.ToVector3();
            Vector3 rot = p.rotation.ToVector3();
            Vector3 scale = p.scale.ToVector3();
            if (scale == Vector3.zero) scale = Vector3.one;

            bool addCollider = ToolParamHelper.ExtractBool(request.parameters, "add_collider",
                ToolParamHelper.ExtractBool(request.parameters, "addCollider", true));
            string parent = ToolParamHelper.ExtractString(request.parameters, "parent", null);

            // Ensure destination directory
            string relativeDir = "Assets/Models/Generated";
            string fullDir = Path.Combine(Application.dataPath, "Models/Generated");
            if (!Directory.Exists(fullDir))
            {
                Directory.CreateDirectory(fullDir);
            }

            string targetGlbRelative = $"{relativeDir}/{assetName}.glb";
            string targetGlbFull = Path.Combine(Application.dataPath, $"Models/Generated/{assetName}.glb").Replace("\\", "/");
            string targetObjRelative = $"{relativeDir}/{assetName}.obj";
            string targetObjFull = Path.Combine(Application.dataPath, $"Models/Generated/{assetName}.obj").Replace("\\", "/");

            Debug.Log($"[GenerateTrellisMeshTool] Initiating TRELLIS generation for '{assetName}' (Prompt: '{prompt}', Image: '{imagePath}')...");

            // 1. Attempt generation via local microservice first, then CLI fallback
            bool generationSuccess = false;
            string generationError = "";

            try
            {
                generationSuccess = CallTrellisMicroservice(prompt, imagePath, targetGlbFull, assetName, out generationError);
            }
            catch (Exception ex)
            {
                generationError = ex.Message;
            }

            if (!generationSuccess)
            {
                Debug.LogWarning($"[GenerateTrellisMeshTool] Microservice uncontactable or returned error ({generationError}). Attempting Python script fallback...");
                generationSuccess = RunTrellisCli(prompt, imagePath, targetGlbFull, out generationError);
            }

            if (!generationSuccess || (!File.Exists(targetGlbFull) && !File.Exists(targetObjFull)))
            {
                return BridgeMessage.Error(request.operationId, "TRELLIS_GENERATION_FAILED",
                    $"TRELLIS 3D generation failed: {generationError}");
            }

            // 2. Import generated assets into Unity AssetDatabase
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);

            // Determine which model file to instantiate (.glb or .obj)
            string chosenAssetPath = File.Exists(targetGlbFull) ? targetGlbRelative : targetObjRelative;
            AssetDatabase.ImportAsset(chosenAssetPath, ImportAssetOptions.ForceUpdate);

            GameObject modelPrefab = AssetDatabase.LoadAssetAtPath<GameObject>(chosenAssetPath);
            if (modelPrefab == null && File.Exists(targetObjFull))
            {
                // Fallback to OBJ if GLB requires gltfast package and wasn't natively recognized
                chosenAssetPath = targetObjRelative;
                AssetDatabase.ImportAsset(chosenAssetPath, ImportAssetOptions.ForceUpdate);
                modelPrefab = AssetDatabase.LoadAssetAtPath<GameObject>(chosenAssetPath);
            }

            if (modelPrefab == null)
            {
                return BridgeMessage.Error(request.operationId, "IMPORT_FAILED",
                    $"3D asset was generated at '{chosenAssetPath}', but Unity could not import it as a GameObject prefab. If using .glb, ensure 'com.unity.cloud.gltfast' is installed, or use .obj format.");
            }

            // 3. Instantiate into the active scene
            GameObject instance = (GameObject)PrefabUtility.InstantiatePrefab(modelPrefab);
            if (instance == null)
            {
                instance = UnityEngine.Object.Instantiate(modelPrefab);
            }

            instance.name = assetName;
            instance.transform.position = pos;
            instance.transform.eulerAngles = rot;
            instance.transform.localScale = scale;

            // Optional parenting
            if (!string.IsNullOrEmpty(parent))
            {
                GameObject parentGo = ToolParamHelper.FindTarget(parent);
                if (parentGo != null)
                {
                    instance.transform.SetParent(parentGo.transform, true);
                }
            }

            // 4. Attach colliders if requested
            int colliderCount = 0;
            if (addCollider)
            {
                colliderCount = EnsureColliders(instance);
            }

            Undo.RegisterCreatedObjectUndo(instance, "Generate Trellis 3D Model");
            Selection.activeGameObject = instance;

            string objectId = ToolParamHelper.FormatObjectId(instance);
            int vertexCount = CountVertices(instance);

            string responseJson = "{" +
                $"\"objectId\":\"{objectId}\"," +
                $"\"name\":\"{instance.name}\"," +
                $"\"assetPath\":\"{chosenAssetPath}\"," +
                $"\"position\":{{\"x\":{pos.x},\"y\":{pos.y},\"z\":{pos.z}}}," +
                $"\"rotation\":{{\"x\":{rot.x},\"y\":{rot.y},\"z\":{rot.z}}}," +
                $"\"scale\":{{\"x\":{scale.x},\"y\":{scale.y},\"z\":{scale.z}}}," +
                $"\"vertices\":{vertexCount}," +
                $"\"collidersAdded\":{colliderCount}," +
                $"\"message\":\"Successfully generated and placed detailed TRELLIS 3D model '{assetName}' in scene.\"" +
                "}";

            Debug.Log($"[GenerateTrellisMeshTool] Created '{instance.name}' ({objectId}) with {vertexCount} vertices at {pos}.");
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, responseJson);
        }

        private bool CallTrellisMicroservice(string prompt, string imagePath, string outputPath, string assetName, out string error)
        {
            error = "";
            try
            {
                var req = (HttpWebRequest)WebRequest.Create(MicroserviceUrl);
                req.Method = "POST";
                req.ContentType = "application/json";
                req.Timeout = 180000; // 3 minutes timeout for 3D diffusion

                string json = "{" +
                    $"\"prompt\":\"{EscapeJson(prompt)}\"," +
                    $"\"image_path\":\"{EscapeJson(imagePath)}\"," +
                    $"\"output_path\":\"{EscapeJson(outputPath)}\"," +
                    $"\"asset_name\":\"{EscapeJson(assetName)}\"" +
                    "}";

                byte[] bytes = Encoding.UTF8.GetBytes(json);
                req.ContentLength = bytes.Length;
                using (var stream = req.GetRequestStream())
                {
                    stream.Write(bytes, 0, bytes.Length);
                }

                using (var res = (HttpWebResponse)req.GetResponse())
                using (var reader = new StreamReader(res.GetResponseStream()))
                {
                    string respJson = reader.ReadToEnd();
                    if (respJson.Contains("\"success\":true") || respJson.Contains("\"success\": true"))
                    {
                        return true;
                    }
                    error = respJson;
                    return false;
                }
            }
            catch (WebException wex)
            {
                error = wex.Message;
                return false;
            }
        }

        private bool RunTrellisCli(string prompt, string imagePath, string outputPath, out string error)
        {
            error = "";
            try
            {
                // Resolve python script path
                string scriptPath = Path.Combine(Directory.GetCurrentDirectory(), "autonomous-unity-agent/tools/trellis_bridge_service.py");
                if (!File.Exists(scriptPath))
                {
                    // Look in parent or Desktop
                    string desktopPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Desktop), "AI Agent Unity/autonomous-unity-agent/tools/trellis_bridge_service.py");
                    if (File.Exists(desktopPath)) scriptPath = desktopPath;
                }

                if (!File.Exists(scriptPath))
                {
                    error = $"Trellis bridge script not found at '{scriptPath}'";
                    return false;
                }

                var psi = new ProcessStartInfo
                {
                    FileName = "python",
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true
                };

                var sbArgs = new StringBuilder();
                sbArgs.Append($"\"{scriptPath}\"");
                if (!string.IsNullOrEmpty(prompt)) sbArgs.Append($" --prompt \"{prompt}\"");
                if (!string.IsNullOrEmpty(imagePath)) sbArgs.Append($" --image \"{imagePath}\"");
                sbArgs.Append($" --output \"{outputPath}\"");

                psi.Arguments = sbArgs.ToString();
                Debug.Log($"[GenerateTrellisMeshTool] Launching: python {psi.Arguments}");

                using (var proc = Process.Start(psi))
                {
                    if (proc == null)
                    {
                        error = "Failed to launch python process.";
                        return false;
                    }

                    string stdout = proc.StandardOutput.ReadToEnd();
                    string stderr = proc.StandardError.ReadToEnd();
                    proc.WaitForExit(180000); // 3 minutes max

                    if (proc.ExitCode != 0)
                    {
                        error = string.IsNullOrEmpty(stderr) ? stdout : stderr;
                        return false;
                    }

                    return File.Exists(outputPath);
                }
            }
            catch (Exception ex)
            {
                error = ex.Message;
                return false;
            }
        }

        private int EnsureColliders(GameObject go)
        {
            int added = 0;
            var filters = go.GetComponentsInChildren<MeshFilter>();
            if (filters != null && filters.Length > 0)
            {
                foreach (var mf in filters)
                {
                    if (mf.gameObject.GetComponent<Collider>() == null && mf.sharedMesh != null)
                    {
                        var mc = mf.gameObject.AddComponent<MeshCollider>();
                        mc.sharedMesh = mf.sharedMesh;
                        added++;
                    }
                }
            }
            else if (go.GetComponent<Collider>() == null)
            {
                go.AddComponent<BoxCollider>();
                added++;
            }
            return added;
        }

        private int CountVertices(GameObject go)
        {
            int count = 0;
            var filters = go.GetComponentsInChildren<MeshFilter>();
            if (filters != null)
            {
                foreach (var f in filters)
                {
                    if (f.sharedMesh != null) count += f.sharedMesh.vertexCount;
                }
            }
            return count;
        }

        private string CleanFileName(string name)
        {
            if (string.IsNullOrEmpty(name)) return "";
            foreach (char c in Path.GetInvalidFileNameChars())
            {
                name = name.Replace(c, '_');
            }
            return name.Replace(" ", "_");
        }

        private string EscapeJson(string s)
        {
            if (string.IsNullOrEmpty(s)) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\n", "\\n").Replace("\r", "\\r");
        }
    }
}
