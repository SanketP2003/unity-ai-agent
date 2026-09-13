using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using AutonomousUnityAgent.Editor.Security;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{

    [Serializable]
    internal class CreateScriptResponse
    {
        public string path;
        public string hash;
        public long sizeBytes;
        public bool created;
    }

    [Serializable]
    internal class ReadScriptResponse
    {
        public string path;
        public string content;
        public int lineCount;
        public string hash;
        public bool exists;
    }

    [Serializable]
    internal class UpdateScriptResponse
    {
        public string path;
        public string previousHash;
        public string newHash;
        public bool changed;
    }

    [Serializable]
    internal class DeleteScriptResponse
    {
        public string path;
        public bool deleted;
        public string warning;
    }

    [Serializable]
    internal class ScriptItemData
    {
        public string path;
        public string name;
        public long sizeBytes;
        public string hash;
        public string lastModified;
    }

    [Serializable]
    internal class ListScriptsResponse
    {
        public int count;
        public List<ScriptItemData> scripts = new List<ScriptItemData>();
    }

    public class CreateScriptTool : IBridgeTool
    {
        public string ToolName => "create_script";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string rawPath = ToolParamHelper.ExtractString(request.parameters, "path", null);
            string content = ToolParamHelper.ExtractString(request.parameters, "content", null);

            string pathErr = ScriptSecurityHelper.ValidatePath(rawPath, out string normalizedPath);
            if (pathErr != null)
            {
                return BridgeMessage.Error(request.operationId, "INVALID_PATH", pathErr);
            }

            string contentErr = ScriptSecurityHelper.ValidateContent(content);
            if (contentErr != null)
            {
                return BridgeMessage.Error(request.operationId, "SCRIPT_SAFETY_VIOLATION", contentErr);
            }

            string fullPath = Path.Combine(Application.dataPath, normalizedPath.Substring("Assets/".Length));
            string dir = Path.GetDirectoryName(fullPath);
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
            }

            if (File.Exists(fullPath))
            {
                return BridgeMessage.Error(request.operationId, "SCRIPT_ALREADY_EXISTS",
                    $"Script already exists at '{normalizedPath}'. Use update_script to modify existing scripts.");
            }

            File.WriteAllText(fullPath, content, Encoding.UTF8);
            AssetDatabase.ImportAsset(normalizedPath, ImportAssetOptions.ForceSynchronousImport);
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);

            string hash = ScriptSecurityHelper.ComputeHash(content);
            var fi = new FileInfo(fullPath);

            var resp = new CreateScriptResponse
            {
                path = normalizedPath,
                hash = hash,
                sizeBytes = fi.Length,
                created = true
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(resp));
        }
    }

    public class ReadScriptTool : IBridgeTool
    {
        public string ToolName => "read_script";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string rawPath = ToolParamHelper.ExtractString(request.parameters, "path", null);
            string pathErr = ScriptSecurityHelper.ValidatePath(rawPath, out string normalizedPath);
            if (pathErr != null)
            {
                return BridgeMessage.Error(request.operationId, "INVALID_PATH", pathErr);
            }

            string fullPath = Path.Combine(Application.dataPath, normalizedPath.Substring("Assets/".Length));
            if (!File.Exists(fullPath))
            {
                return BridgeMessage.Error(request.operationId, "FILE_NOT_FOUND", $"Script not found at '{normalizedPath}'");
            }

            string content = File.ReadAllText(fullPath, Encoding.UTF8);
            string hash = ScriptSecurityHelper.ComputeHash(content);
            int lines = content.Split('\n').Length;

            var resp = new ReadScriptResponse
            {
                path = normalizedPath,
                content = content,
                lineCount = lines,
                hash = hash,
                exists = true
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(resp));
        }
    }

    public class UpdateScriptTool : IBridgeTool
    {
        public string ToolName => "update_script";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string rawPath = ToolParamHelper.ExtractString(request.parameters, "path", null);
            string previousHash = ToolParamHelper.ExtractString(request.parameters, "previousHash", null);
            if (string.IsNullOrEmpty(previousHash))
            {
                previousHash = ToolParamHelper.ExtractString(request.parameters, "expectedHash", null);
            }
            string content = ToolParamHelper.ExtractString(request.parameters, "content", null);

            string pathErr = ScriptSecurityHelper.ValidatePath(rawPath, out string normalizedPath);
            if (pathErr != null)
            {
                return BridgeMessage.Error(request.operationId, "INVALID_PATH", pathErr);
            }

            string contentErr = ScriptSecurityHelper.ValidateContent(content);
            if (contentErr != null)
            {
                return BridgeMessage.Error(request.operationId, "SCRIPT_SAFETY_VIOLATION", contentErr);
            }

            string fullPath = Path.Combine(Application.dataPath, normalizedPath.Substring("Assets/".Length));
            if (!File.Exists(fullPath))
            {
                return BridgeMessage.Error(request.operationId, "FILE_NOT_FOUND", $"Script not found at '{normalizedPath}'");
            }

            string existingContent = File.ReadAllText(fullPath, Encoding.UTF8);
            string currentHash = ScriptSecurityHelper.ComputeHash(existingContent);

            // Optimistic concurrency verification
            if (!string.IsNullOrEmpty(previousHash) && !string.Equals(previousHash.Trim(), currentHash.Trim(), StringComparison.OrdinalIgnoreCase))
            {
                return BridgeMessage.Error(request.operationId, "SCRIPT_CONFLICT",
                    $"Optimistic concurrency conflict for '{normalizedPath}'. Expected previousHash: {previousHash}, actual currentHash: {currentHash}. Read the file again before updating.");
            }

            File.WriteAllText(fullPath, content, Encoding.UTF8);
            AssetDatabase.ImportAsset(normalizedPath, ImportAssetOptions.ForceSynchronousImport);
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);

            string newHash = ScriptSecurityHelper.ComputeHash(content);

            var resp = new UpdateScriptResponse
            {
                path = normalizedPath,
                previousHash = currentHash,
                newHash = newHash,
                changed = true
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(resp));
        }
    }

    public class DeleteScriptTool : IBridgeTool
    {
        public string ToolName => "delete_script";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string rawPath = ToolParamHelper.ExtractString(request.parameters, "path", null);
            string pathErr = ScriptSecurityHelper.ValidatePath(rawPath, out string normalizedPath);
            if (pathErr != null)
            {
                return BridgeMessage.Error(request.operationId, "INVALID_PATH", pathErr);
            }

            // Protect framework and internal editor scripts
            if (normalizedPath.StartsWith("Assets/AutonomousUnityAgent/"))
            {
                return BridgeMessage.Error(request.operationId, "PROTECTED_SCRIPT",
                    "Cannot delete internal AutonomousUnityAgent framework scripts.");
            }

            string fullPath = Path.Combine(Application.dataPath, normalizedPath.Substring("Assets/".Length));
            if (!File.Exists(fullPath))
            {
                return BridgeMessage.Error(request.operationId, "FILE_NOT_FOUND", $"Script not found at '{normalizedPath}'");
            }

            // Inspect if script is attached to scene objects
            string scriptName = Path.GetFileNameWithoutExtension(fullPath);
            string warning = null;
            var allObjects = GameObject.FindObjectsOfType<GameObject>();
            int attachedCount = 0;
            foreach (var go in allObjects)
            {
                var comp = go.GetComponent(scriptName);
                if (comp != null)
                {
                    attachedCount++;
                }
            }

            if (attachedCount > 0)
            {
                warning = $"Script '{scriptName}' was attached to {attachedCount} scene GameObject(s). Deleting it will leave missing component references.";
            }

            AssetDatabase.DeleteAsset(normalizedPath);

            var resp = new DeleteScriptResponse
            {
                path = normalizedPath,
                deleted = true,
                warning = warning
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(resp));
        }
    }

    public class ListScriptsTool : IBridgeTool
    {
        public string ToolName => "list_scripts";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string rawPath = ToolParamHelper.ExtractString(request.parameters, "path", "Assets");
            bool recursive = ToolParamHelper.ExtractBool(request.parameters, "recursive", true);

            string pathErr = null;
            string normalizedPath = "Assets";
            if (!string.Equals(rawPath, "Assets", StringComparison.OrdinalIgnoreCase))
            {
                pathErr = ScriptSecurityHelper.ValidatePath(rawPath + "/dummy.cs", out string testPath);
                if (pathErr == null)
                {
                    normalizedPath = rawPath.Trim().Replace('\\', '/');
                }
            }

            string rootDir = Application.dataPath;
            if (normalizedPath.StartsWith("Assets/"))
            {
                rootDir = Path.Combine(Application.dataPath, normalizedPath.Substring("Assets/".Length));
            }

            var resp = new ListScriptsResponse();
            if (Directory.Exists(rootDir))
            {
                SearchOption opt = recursive ? SearchOption.AllDirectories : SearchOption.TopDirectoryOnly;
                string[] files = Directory.GetFiles(rootDir, "*.cs", opt);
                foreach (string f in files)
                {
                    string relative = "Assets" + f.Substring(Application.dataPath.Length).Replace('\\', '/');
                    // Skip internal AutonomousUnityAgent framework scripts
                    if (relative.StartsWith("Assets/AutonomousUnityAgent/"))
                    {
                        continue;
                    }

                    var fi = new FileInfo(f);
                    string content = File.ReadAllText(f, Encoding.UTF8);
                    resp.scripts.Add(new ScriptItemData
                    {
                        path = relative,
                        name = Path.GetFileName(f),
                        sizeBytes = fi.Length,
                        hash = ScriptSecurityHelper.ComputeHash(content),
                        lastModified = fi.LastWriteTimeUtc.ToString("o")
                    });
                }
            }
            resp.count = resp.scripts.Count;

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(resp));
        }
    }
}
