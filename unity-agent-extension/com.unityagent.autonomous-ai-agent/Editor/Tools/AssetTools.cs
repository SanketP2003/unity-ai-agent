using System;
using System.IO;
using System.Collections.Generic;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class ListAssetsTool : IBridgeTool
    {
        public string ToolName => "list_assets";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string folder = ToolParamHelper.ExtractString(request.parameters, "path", "Assets");
            string filter = ToolParamHelper.ExtractString(request.parameters, "filter", null);

            string[] guids = string.IsNullOrEmpty(filter)
                ? AssetDatabase.FindAssets("", new[] { folder })
                : AssetDatabase.FindAssets(filter, new[] { folder });

            var assetList = new List<string>();
            foreach (var guid in guids)
            {
                string p = AssetDatabase.GUIDToAssetPath(guid);
                if (!string.IsNullOrEmpty(p)) assetList.Add("\"" + p + "\"");
                if (assetList.Count >= 100) break; // Limit payload
            }

            string json = "{"
                + "\"folder\":\"" + folder + "\","
                + "\"count\":" + assetList.Count + ","
                + "\"assets\":[" + string.Join(",", assetList) + "]"
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class FindAssetTool : IBridgeTool
    {
        public string ToolName => "find_asset";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string query = ToolParamHelper.ExtractString(request.parameters, "query");
            string type = ToolParamHelper.ExtractString(request.parameters, "type", null);

            string searchStr = query;
            if (!string.IsNullOrEmpty(type)) searchStr += " t:" + type;

            string[] guids = AssetDatabase.FindAssets(searchStr);
            var results = new List<string>();
            foreach (var guid in guids)
            {
                results.Add("\"" + AssetDatabase.GUIDToAssetPath(guid) + "\"");
                if (results.Count >= 50) break;
            }

            string json = "{"
                + "\"query\":\"" + searchStr + "\","
                + "\"count\":" + results.Count + ","
                + "\"results\":[" + string.Join(",", results) + "]"
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetAssetInfoTool : IBridgeTool
    {
        public string ToolName => "get_asset_info";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string path = ToolParamHelper.ExtractString(request.parameters, "path", ToolParamHelper.ExtractString(request.parameters, "assetPath", null));
            if (string.IsNullOrEmpty(path)) return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameter 'path' or 'assetPath' is required");

            string guid = AssetDatabase.AssetPathToGUID(path);
            if (string.IsNullOrEmpty(guid)) return BridgeMessage.Error(request.operationId, "ASSET_NOT_FOUND", $"Asset not found at '{path}'");

            Type assetType = AssetDatabase.GetMainAssetTypeAtPath(path);
            long size = 0;
            if (File.Exists(path)) size = new FileInfo(path).Length;

            string json = "{"
                + "\"path\":\"" + path + "\","
                + "\"guid\":\"" + guid + "\","
                + "\"type\":\"" + (assetType != null ? assetType.Name : "Unknown") + "\","
                + "\"sizeBytes\":" + size
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateAssetFolderTool : IBridgeTool
    {
        public string ToolName => "create_asset_folder";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string parent = ToolParamHelper.ExtractString(request.parameters, "parent", "Assets");
            string newFolder = ToolParamHelper.ExtractString(request.parameters, "name", ToolParamHelper.ExtractString(request.parameters, "folderName", null));

            if (string.IsNullOrEmpty(newFolder)) return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameter 'name' or 'folderName' is required");

            string guid = AssetDatabase.CreateFolder(parent, newFolder);
            string createdPath = AssetDatabase.GUIDToAssetPath(guid);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created asset folder '{createdPath}'",
                target = createdPath
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class MoveAssetTool : IBridgeTool
    {
        public string ToolName => "move_asset";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string from = ToolParamHelper.ExtractString(request.parameters, "from", ToolParamHelper.ExtractString(request.parameters, "sourcePath", null));
            string to = ToolParamHelper.ExtractString(request.parameters, "to", ToolParamHelper.ExtractString(request.parameters, "destinationPath", null));

            if (string.IsNullOrEmpty(from) || string.IsNullOrEmpty(to))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameters 'from' and 'to' are required");
            }

            string err = AssetDatabase.MoveAsset(from, to);
            if (!string.IsNullOrEmpty(err))
            {
                return BridgeMessage.Error(request.operationId, "MOVE_FAILED", err);
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Moved asset from '{from}' to '{to}'",
                target = to
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class RenameAssetTool : IBridgeTool
    {
        public string ToolName => "rename_asset";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string path = ToolParamHelper.ExtractString(request.parameters, "path", ToolParamHelper.ExtractString(request.parameters, "assetPath", null));
            string newName = ToolParamHelper.ExtractString(request.parameters, "newName", ToolParamHelper.ExtractString(request.parameters, "name", null));

            if (string.IsNullOrEmpty(path) || string.IsNullOrEmpty(newName))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameters 'path' and 'newName' are required");
            }

            string err = AssetDatabase.RenameAsset(path, newName);
            if (!string.IsNullOrEmpty(err))
            {
                return BridgeMessage.Error(request.operationId, "RENAME_FAILED", err);
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Renamed asset at '{path}' to '{newName}'",
                target = newName
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class DeleteAssetTool : IBridgeTool
    {
        public string ToolName => "delete_asset";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string path = ToolParamHelper.ExtractString(request.parameters, "path", ToolParamHelper.ExtractString(request.parameters, "assetPath", null));
            if (string.IsNullOrEmpty(path)) return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameter 'path' or 'assetPath' is required");

            // Security protection: Protect Packages/, ProjectSettings/, Library/, Temp/
            if (path.StartsWith("Packages", StringComparison.OrdinalIgnoreCase) ||
                path.StartsWith("ProjectSettings", StringComparison.OrdinalIgnoreCase) ||
                path.StartsWith("Library", StringComparison.OrdinalIgnoreCase) ||
                path.StartsWith("Temp", StringComparison.OrdinalIgnoreCase) ||
                path.Contains(".."))
            {
                return BridgeMessage.Error(request.operationId, "PROTECTED_PATH", $"Cannot delete assets in protected system directory: '{path}'");
            }

            bool deleted = AssetDatabase.DeleteAsset(path);
            if (!deleted)
            {
                return BridgeMessage.Error(request.operationId, "DELETE_FAILED", $"Failed to delete asset at '{path}'");
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Deleted asset at '{path}'",
                target = path
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
