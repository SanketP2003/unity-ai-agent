using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor
{
    /// <summary>
    /// Manages persistent, stable project identity for Autonomous Game Studio.
    /// Stores the project ID and fingerprint in ProjectSettings/AutonomousAgentIdentity.json
    /// so the identity survives editor restarts, git status checks, and detects project cloning.
    /// </summary>
    public static class ProjectIdentity
    {
        private const string SettingsFileName = "AutonomousAgentIdentity.json";
        private static IdentityData _cachedIdentity;

        [Serializable]
        public class IdentityData
        {
            public string projectId;
            public string projectName;
            public string projectPath;
            public string fingerprint;
            public string createdAt;
            public string unityVersion;
        }

        private static string GetSettingsPath()
        {
            string projectRoot = GetProjectRootPath();
            return Path.Combine(projectRoot, "ProjectSettings", SettingsFileName);
        }

        public static string GetProjectRootPath()
        {
            try
            {
                var dataDir = new DirectoryInfo(Application.dataPath);
                return dataDir.Parent != null ? dataDir.Parent.FullName : Application.dataPath;
            }
            catch
            {
                return Directory.GetCurrentDirectory();
            }
        }

        public static string GetProjectName()
        {
            if (!string.IsNullOrEmpty(PlayerSettings.productName) && PlayerSettings.productName != "DefaultCompany")
            {
                return PlayerSettings.productName;
            }
            string root = GetProjectRootPath();
            return new DirectoryInfo(root).Name;
        }

        /// <summary>
        /// Retrieves the persistent identity, or creates a new stable identity if none exists.
        /// Detects if the project directory was cloned or moved.
        /// </summary>
        public static IdentityData GetOrCreateIdentity()
        {
            if (_cachedIdentity != null && !string.IsNullOrEmpty(_cachedIdentity.projectId))
            {
                return _cachedIdentity;
            }

            string filePath = GetSettingsPath();
            string currentRoot = NormalizePath(GetProjectRootPath());

            if (File.Exists(filePath))
            {
                try
                {
                    string json = File.ReadAllText(filePath);
                    var data = JsonUtility.FromJson<IdentityData>(json);
                    if (data != null && !string.IsNullOrEmpty(data.projectId))
                    {
                        string savedRoot = NormalizePath(data.projectPath);
                        // Check if project was cloned or moved
                        if (!string.IsNullOrEmpty(savedRoot) && !string.Equals(savedRoot, currentRoot, StringComparison.OrdinalIgnoreCase))
                        {
                            Debug.LogWarning($"[ProjectIdentity] Project path changed from '{savedRoot}' to '{currentRoot}'. Clone or move detected. Preserving projectId={data.projectId} with updated path.");
                            data.projectPath = currentRoot;
                            data.fingerprint = ComputeFingerprint(data.projectId, currentRoot, data.createdAt);
                            SaveIdentity(data);
                        }

                        _cachedIdentity = data;
                        return _cachedIdentity;
                    }
                }
                catch (Exception ex)
                {
                    Debug.LogWarning($"[ProjectIdentity] Failed to read identity file: {ex.Message}. Re-generating.");
                }
            }

            // Generate new persistent identity
            string newId = "proj_" + Guid.NewGuid().ToString("N").Substring(0, 12);
            string now = DateTime.UtcNow.ToString("o");
            var newIdentity = new IdentityData
            {
                projectId = newId,
                projectName = GetProjectName(),
                projectPath = currentRoot,
                createdAt = now,
                unityVersion = Application.unityVersion,
                fingerprint = ComputeFingerprint(newId, currentRoot, now)
            };

            SaveIdentity(newIdentity);
            _cachedIdentity = newIdentity;
            Debug.Log($"[ProjectIdentity] Generated new project identity: {newIdentity.projectId} ({newIdentity.projectName})");
            return _cachedIdentity;
        }

        public static string GetOrCreateProjectId()
        {
            return GetOrCreateIdentity().projectId;
        }

        public static void SetProjectId(string newProjectId)
        {
            if (string.IsNullOrEmpty(newProjectId)) return;
            var current = GetOrCreateIdentity();
            current.projectId = newProjectId;
            current.fingerprint = ComputeFingerprint(newProjectId, current.projectPath, current.createdAt);
            SaveIdentity(current);
            _cachedIdentity = current;
        }

        public static void ResetIdentity()
        {
            _cachedIdentity = null;
            string filePath = GetSettingsPath();
            if (File.Exists(filePath))
            {
                try { File.Delete(filePath); } catch { }
            }
            GetOrCreateIdentity();
        }

        private static void SaveIdentity(IdentityData data)
        {
            try
            {
                string filePath = GetSettingsPath();
                string dir = Path.GetDirectoryName(filePath);
                if (!Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }
                string json = JsonUtility.ToJson(data, true);
                File.WriteAllText(filePath, json);
            }
            catch (Exception ex)
            {
                Debug.LogError($"[ProjectIdentity] Failed to save project identity: {ex.Message}");
            }
        }

        private static string NormalizePath(string path)
        {
            if (string.IsNullOrEmpty(path)) return "";
            return path.Replace('\\', '/').TrimEnd('/');
        }

        private static string ComputeFingerprint(string projectId, string projectPath, string createdAt)
        {
            using (var sha = SHA256.Create())
            {
                string raw = $"{projectId}|{projectPath}|{createdAt}";
                byte[] bytes = sha.ComputeHash(Encoding.UTF8.GetBytes(raw));
                var sb = new StringBuilder();
                for (int i = 0; i < bytes.Length; i++)
                {
                    sb.Append(bytes[i].ToString("x2"));
                }
                return sb.ToString();
            }
        }
    }
}
