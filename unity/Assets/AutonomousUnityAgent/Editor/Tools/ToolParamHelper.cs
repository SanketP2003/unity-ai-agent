using System;
using System.Globalization;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace AutonomousUnityAgent.Editor.Tools
{
    /// <summary>
    /// Utility methods for parsing tool parameters and locating GameObjects.
    /// </summary>
    public static class ToolParamHelper
    {
        public static T Parse<T>(string json) where T : class, new()
        {
            if (string.IsNullOrEmpty(json)) return new T();
            try
            {
                return JsonUtility.FromJson<T>(json) ?? new T();
            }
            catch
            {
                return new T();
            }
        }

        public static GameObject FindTarget(string target)
        {
            if (string.IsNullOrEmpty(target)) return null;

            // 1. Try standard GameObject.Find (fast, supports paths for active objects)
            GameObject go = GameObject.Find(target);
            if (go != null) return go;

            // 2. Search scene including inactive GameObjects
            Scene activeScene = SceneManager.GetActiveScene();
            GameObject[] roots = activeScene.GetRootGameObjects();

            foreach (var root in roots)
            {
                if (root.name.Equals(target, StringComparison.OrdinalIgnoreCase))
                    return root;

                Transform found = FindInChildren(root.transform, target);
                if (found != null)
                    return found.gameObject;
            }

            return null;
        }

        private static Transform FindInChildren(Transform parent, string name)
        {
            for (int i = 0; i < parent.childCount; i++)
            {
                Transform child = parent.GetChild(i);
                if (child.name.Equals(name, StringComparison.OrdinalIgnoreCase))
                    return child;

                Transform sub = FindInChildren(child, name);
                if (sub != null)
                    return sub;
            }
            return null;
        }

        public static string ExtractString(string json, string field, string defaultValue = null)
        {
            if (string.IsNullOrEmpty(json)) return defaultValue;
            string pattern = $"\"{field}\":\"";
            int start = json.IndexOf(pattern, StringComparison.Ordinal);
            if (start < 0) return defaultValue;
            start += pattern.Length;
            int end = json.IndexOf("\"", start, StringComparison.Ordinal);
            if (end < 0) return defaultValue;
            return json.Substring(start, end - start);
        }

        public static bool ExtractBool(string json, string field, bool defaultValue = false)
        {
            if (string.IsNullOrEmpty(json)) return defaultValue;
            string pattern = $"\"{field}\":";
            int start = json.IndexOf(pattern, StringComparison.Ordinal);
            if (start < 0) return defaultValue;
            start += pattern.Length;
            while (start < json.Length && char.IsWhiteSpace(json[start])) start++;
            if (start + 4 <= json.Length && json.Substring(start, 4).ToLowerInvariant() == "true") return true;
            if (start + 5 <= json.Length && json.Substring(start, 5).ToLowerInvariant() == "false") return false;
            return defaultValue;
        }

        public static float ExtractFloat(string json, string field, float defaultValue = 0f)
        {
            if (string.IsNullOrEmpty(json)) return defaultValue;
            string pattern = $"\"{field}\":";
            int start = json.IndexOf(pattern, StringComparison.Ordinal);
            if (start < 0) return defaultValue;
            start += pattern.Length;
            while (start < json.Length && char.IsWhiteSpace(json[start])) start++;
            int end = start;
            while (end < json.Length && (char.IsDigit(json[end]) || json[end] == '.' || json[end] == '-')) end++;
            if (end > start && float.TryParse(json.Substring(start, end - start), NumberStyles.Float, CultureInfo.InvariantCulture, out float result))
            {
                return result;
            }
            return defaultValue;
        }

        public static Color ParseColor(string hexOrObj, Color defaultColor)
        {
            if (string.IsNullOrEmpty(hexOrObj)) return defaultColor;
            if (hexOrObj.StartsWith("#"))
            {
                if (ColorUtility.TryParseHtmlString(hexOrObj, out Color c))
                    return c;
            }
            return defaultColor;
        }
    }
}
