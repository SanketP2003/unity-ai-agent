using System;
using System.IO;
using UnityEditor;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace AutonomousUnityAgent.Editor
{
    /// <summary>
    /// Dynamic project discovery for the Autonomous Unity Agent extension.
    /// Discovers environment, engine version, project filesystem paths, compilation state,
    /// and active scene state without any hardcoded project paths or user names.
    /// </summary>
    public static class ProjectDiscovery
    {
        public const string ExtensionVersion = "1.0.0";
        public const string ProtocolVersion = "1.0";

        [Serializable]
        public class ProjectInfo
        {
            public string unityVersion;
            public string projectRoot;
            public string assetsRoot;
            public string packagesRoot;
            public string activeScene;
            public string activeScenePath;
            public string compilationState; // COMPILED, COMPILING, FAILED
            public bool playMode;
            public string buildTarget;
            public string agentExtensionVersion;
            public string protocolVersion;
        }

        public static ProjectInfo GetCurrentProjectInfo()
        {
            string assets = Application.dataPath.Replace('\\', '/');
            string projectRoot = "";
            try
            {
                var parent = Directory.GetParent(Application.dataPath);
                if (parent != null)
                {
                    projectRoot = parent.FullName.Replace('\\', '/');
                }
            }
            catch (Exception)
            {
                projectRoot = assets;
            }

            string packagesRoot = Path.Combine(projectRoot, "Packages").Replace('\\', '/');
            var scene = SceneManager.GetActiveScene();

            string compilationState = "COMPILED";
            if (EditorApplication.isCompiling)
            {
                compilationState = "COMPILING";
            }
            else if (EditorUtility.scriptCompilationFailed)
            {
                var errors = AutonomousUnityAgent.Editor.Tools.GetConsoleLogsTool.GetLogs(50, l => l.type == "Error" && (l.message.Contains("error CS") || l.message.Contains("Compilation error")));
                compilationState = errors.Count > 0 ? "FAILED" : "COMPILED";
            }

            return new ProjectInfo
            {
                unityVersion = Application.unityVersion,
                projectRoot = projectRoot,
                assetsRoot = assets,
                packagesRoot = packagesRoot,
                activeScene = !string.IsNullOrEmpty(scene.name) ? scene.name : "Untitled",
                activeScenePath = !string.IsNullOrEmpty(scene.path) ? scene.path : "",
                compilationState = compilationState,
                playMode = EditorApplication.isPlaying,
                buildTarget = EditorUserBuildSettings.activeBuildTarget.ToString(),
                agentExtensionVersion = ExtensionVersion,
                protocolVersion = ProtocolVersion
            };
        }
    }
}
