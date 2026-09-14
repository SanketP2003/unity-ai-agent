using System;
using System.IO;
using System.Collections.Generic;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class BuildProjectTool : IBridgeTool
    {
        public string ToolName => "build_project";

        public static BuildReport LastBuildReport { get; private set; }

        public BridgeMessage Execute(BridgeMessage request)
        {
            string outputPath = ToolParamHelper.ExtractString(request.parameters, "outputPath");
            if (string.IsNullOrEmpty(outputPath))
            {
                outputPath = "Builds/Windows/Game.exe";
            }

            string dir = Path.GetDirectoryName(outputPath);
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
            }

            // Collect scenes in build
            var scenePaths = new List<string>();
            foreach (var s in EditorBuildSettings.scenes)
            {
                if (s.enabled && File.Exists(s.path)) scenePaths.Add(s.path);
            }

            if (scenePaths.Count == 0)
            {
                // Fallback to active scene
                var active = UnityEngine.SceneManagement.SceneManager.GetActiveScene();
                if (!string.IsNullOrEmpty(active.path)) scenePaths.Add(active.path);
            }

            if (scenePaths.Count == 0)
            {
                return BridgeMessage.Error(request.operationId, "NO_SCENES", "No valid scenes configured in EditorBuildSettings or active scene");
            }

            string platformStr = ToolParamHelper.ExtractString(request.parameters, "platform");
            BuildTarget target = BuildTarget.StandaloneWindows64;
            if (!string.IsNullOrEmpty(platformStr))
            {
                string norm = platformStr.ToUpperInvariant().Trim();
                if (norm == "LINUX" || norm == "STANDALONELINUX64") target = BuildTarget.StandaloneLinux64;
                else if (norm == "OSX" || norm == "STANDALONEOSX" || norm == "MACOS") target = BuildTarget.StandaloneOSX;
                else if (norm == "ANDROID") target = BuildTarget.Android;
                else if (norm == "WEBGL") target = BuildTarget.WebGL;
                else if (norm == "IOS") target = BuildTarget.iOS;
                else target = BuildTarget.StandaloneWindows64;
            }

            BuildPlayerOptions buildOptions = new BuildPlayerOptions
            {
                scenes = scenePaths.ToArray(),
                locationPathName = outputPath,
                target = target,
                options = ToolParamHelper.ExtractBool(request.parameters, "development", false)
                    ? BuildOptions.Development
                    : BuildOptions.None
            };

            BuildReport report = BuildPipeline.BuildPlayer(buildOptions);
            LastBuildReport = report;

            bool success = report.summary.result == BuildResult.Succeeded;

            string json = "{"
                + "\"result\":\"" + report.summary.result.ToString() + "\","
                + "\"totalErrors\":" + report.summary.totalErrors + ","
                + "\"totalWarnings\":" + report.summary.totalWarnings + ","
                + "\"totalSize\":" + report.summary.totalSize + ","
                + "\"outputPath\":\"" + report.summary.outputPath + "\","
                + "\"durationMs\":" + (long)report.summary.totalTime.TotalMilliseconds
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, success, json);
        }
    }

    public class GetBuildResultTool : IBridgeTool
    {
        public string ToolName => "get_build_result";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var report = BuildProjectTool.LastBuildReport;
            if (report == null)
            {
                return BridgeMessage.Error(request.operationId, "NO_REPORT", "No build has been executed yet");
            }

            string json = "{"
                + "\"result\":\"" + report.summary.result.ToString() + "\","
                + "\"totalErrors\":" + report.summary.totalErrors + ","
                + "\"totalWarnings\":" + report.summary.totalWarnings + ","
                + "\"outputPath\":\"" + report.summary.outputPath + "\""
                + "}";
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ValidateBuildSettingsTool : IBridgeTool
    {
        public string ToolName => "validate_build_settings";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var scenes = EditorBuildSettings.scenes;
            int validCount = 0;
            var missing = new List<string>();

            foreach (var s in scenes)
            {
                if (File.Exists(s.path)) validCount++;
                else missing.Add(s.path);
            }

            bool valid = validCount > 0 && missing.Count == 0;
            string json = "{"
                + "\"valid\":" + (valid ? "true" : "false") + ","
                + "\"validSceneCount\":" + validCount + ","
                + "\"missingScenes\":[" + string.Join(",", missing.ConvertAll(m => "\"" + m + "\"")) + "]"
                + "}";
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetPlatformCapabilitiesTool : IBridgeTool
    {
        public string ToolName => "get_platform_capabilities";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var list = new List<string>();

            // Windows
            bool winSupp = BuildPipeline.IsBuildTargetSupported(BuildTargetGroup.Standalone, BuildTarget.StandaloneWindows64);
            list.Add("{\"platform\":\"WINDOWS\",\"supported\":" + (winSupp ? "true" : "false") + ",\"moduleInstalled\":" + (winSupp ? "true" : "false") + ",\"buildAvailable\":" + (winSupp ? "true" : "false") + "}");

            // Linux
            bool linuxSupp = BuildPipeline.IsBuildTargetSupported(BuildTargetGroup.Standalone, BuildTarget.StandaloneLinux64);
            list.Add("{\"platform\":\"LINUX\",\"supported\":" + (linuxSupp ? "true" : "false") + ",\"moduleInstalled\":" + (linuxSupp ? "true" : "false") + ",\"buildAvailable\":" + (linuxSupp ? "true" : "false") + "}");

            // OSX
            bool osxSupp = BuildPipeline.IsBuildTargetSupported(BuildTargetGroup.Standalone, BuildTarget.StandaloneOSX);
            list.Add("{\"platform\":\"OSX\",\"supported\":" + (osxSupp ? "true" : "false") + ",\"moduleInstalled\":" + (osxSupp ? "true" : "false") + ",\"buildAvailable\":" + (osxSupp ? "true" : "false") + "}");

            // Android
            bool androidSupp = BuildPipeline.IsBuildTargetSupported(BuildTargetGroup.Android, BuildTarget.Android);
            list.Add("{\"platform\":\"ANDROID\",\"supported\":" + (androidSupp ? "true" : "false") + ",\"moduleInstalled\":" + (androidSupp ? "true" : "false") + ",\"buildAvailable\":" + (androidSupp ? "true" : "false") + "}");

            // WebGL
            bool webglSupp = BuildPipeline.IsBuildTargetSupported(BuildTargetGroup.WebGL, BuildTarget.WebGL);
            list.Add("{\"platform\":\"WEBGL\",\"supported\":" + (webglSupp ? "true" : "false") + ",\"moduleInstalled\":" + (webglSupp ? "true" : "false") + ",\"buildAvailable\":" + (webglSupp ? "true" : "false") + "}");

            // iOS
            bool iosSupp = BuildPipeline.IsBuildTargetSupported(BuildTargetGroup.iOS, BuildTarget.iOS);
            list.Add("{\"platform\":\"IOS\",\"supported\":" + (iosSupp ? "true" : "false") + ",\"moduleInstalled\":" + (iosSupp ? "true" : "false") + ",\"buildAvailable\":" + (iosSupp ? "true" : "false") + "}");

            string json = "{\"capabilities\":[" + string.Join(",", list) + "]}";
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
