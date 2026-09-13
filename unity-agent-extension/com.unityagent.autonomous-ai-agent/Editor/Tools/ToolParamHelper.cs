#pragma warning disable CS0618
using System;
using System.Globalization;
using AutonomousUnityAgent.Models;
using UnityEditor;
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

        public static string FormatObjectId(GameObject go)
        {
            return UnityObjectIdentity.FormatObjectId(go);
        }

        public static string GetHierarchyPath(GameObject go)
        {
            if (go == null) return null;
            string path = go.name;
            Transform current = go.transform.parent;
            while (current != null)
            {
                path = current.name + "/" + path;
                current = current.parent;
            }
            return path;
        }

        public static GameObject ResolveTargetFromRequest(string parameters)
        {
            if (string.IsNullOrEmpty(parameters)) return null;
            string target = ExtractString(parameters, "objectId");
            if (string.IsNullOrEmpty(target))
            {
                target = ExtractString(parameters, "target");
            }
            return FindTarget(target);
        }

        public static GameObject FindTarget(string target)
        {
            if (string.IsNullOrEmpty(target)) return null;

            // 1. Try resolving by objectId abstraction (separating AI objectId from transient engine InstanceID)
            if (UnityObjectIdentity.TryParseInstanceId(target, out int id))
            {
                var obj = EditorUtility.InstanceIDToObject(id);
                if (obj is GameObject goById) return goById;
                if (obj is Component compById) return compById.gameObject;
            }

            // 2. Try standard GameObject.Find (fast, supports paths for active objects)
            GameObject go = GameObject.Find(target);
            if (go != null) return go;

            // 3. Search scene including inactive GameObjects
            Scene activeScene = SceneManager.GetActiveScene();
            GameObject[] roots = activeScene.GetRootGameObjects();

            foreach (var root in roots)
            {
                if (root.name.Equals(target, StringComparison.OrdinalIgnoreCase) ||
                    FormatObjectId(root).Equals(target, StringComparison.OrdinalIgnoreCase))
                    return root;

                Transform found = FindInChildren(root.transform, target);
                if (found != null)
                    return found.gameObject;
            }

            return null;
        }

        private static Transform FindInChildren(Transform parent, string target)
        {
            for (int i = 0; i < parent.childCount; i++)
            {
                Transform child = parent.GetChild(i);
                if (child.name.Equals(target, StringComparison.OrdinalIgnoreCase) ||
                    FormatObjectId(child.gameObject).Equals(target, StringComparison.OrdinalIgnoreCase))
                    return child;

                Transform sub = FindInChildren(child, target);
                if (sub != null)
                    return sub;
            }
            return null;
        }

        private static int FindColonAfterKey(string json, string field)
        {
            if (string.IsNullOrEmpty(json) || string.IsNullOrEmpty(field)) return -1;
            int keyIndex = json.IndexOf($"\"{field}\"", StringComparison.Ordinal);
            if (keyIndex < 0) return -1;
            return json.IndexOf(':', keyIndex + field.Length + 2);
        }

        public static string ExtractString(string json, string field, string defaultValue = null)
        {
            int colonIndex = FindColonAfterKey(json, field);
            if (colonIndex < 0) return defaultValue;
            int quoteStart = json.IndexOf('"', colonIndex + 1);
            if (quoteStart < 0) return defaultValue;
            for (int i = colonIndex + 1; i < quoteStart; i++)
            {
                if (!char.IsWhiteSpace(json[i])) return defaultValue;
            }
            int quoteEnd = json.IndexOf('"', quoteStart + 1);
            while (quoteEnd > 0 && json[quoteEnd - 1] == '\\')
            {
                quoteEnd = json.IndexOf('"', quoteEnd + 1);
            }
            if (quoteEnd < 0) return defaultValue;
            string raw = json.Substring(quoteStart + 1, quoteEnd - quoteStart - 1);
            return UnescapeJsonString(raw);
        }

        public static string UnescapeJsonString(string s)
        {
            if (string.IsNullOrEmpty(s)) return s;
            var sb = new System.Text.StringBuilder(s.Length);
            for (int i = 0; i < s.Length; i++)
            {
                if (s[i] == '\\' && i + 1 < s.Length)
                {
                    char next = s[i + 1];
                    switch (next)
                    {
                        case 'n': sb.Append('\n'); i++; break;
                        case 'r': sb.Append('\r'); i++; break;
                        case 't': sb.Append('\t'); i++; break;
                        case '"': sb.Append('"'); i++; break;
                        case '\\': sb.Append('\\'); i++; break;
                        case '/': sb.Append('/'); i++; break;
                        default: sb.Append('\\'); break;
                    }
                }
                else
                {
                    sb.Append(s[i]);
                }
            }
            return sb.ToString();
        }

        public static bool ExtractBool(string json, string field, bool defaultValue = false)
        {
            int colonIndex = FindColonAfterKey(json, field);
            if (colonIndex < 0) return defaultValue;
            int start = colonIndex + 1;
            while (start < json.Length && char.IsWhiteSpace(json[start])) start++;
            if (start + 4 <= json.Length && json.Substring(start, 4).ToLowerInvariant() == "true") return true;
            if (start + 5 <= json.Length && json.Substring(start, 5).ToLowerInvariant() == "false") return false;
            return defaultValue;
        }

        public static float ExtractFloat(string json, string field, float defaultValue = 0f)
        {
            int colonIndex = FindColonAfterKey(json, field);
            if (colonIndex < 0) return defaultValue;
            int start = colonIndex + 1;
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
