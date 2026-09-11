using System;
using System.Collections.Generic;
using UnityEngine;

namespace AutonomousUnityAgent.Models
{
    [Serializable]
    public struct Vector3Data
    {
        public float x;
        public float y;
        public float z;

        public Vector3 ToVector3() => new Vector3(x, y, z);

        public static Vector3Data FromVector3(Vector3 v) => new Vector3Data
        {
            x = v.x,
            y = v.y,
            z = v.z
        };
    }

    [Serializable]
    public struct ColorData
    {
        public float r;
        public float g;
        public float b;
        public float a;

        public Color ToColor() => new Color(r, g, b, a <= 0.001f ? 1f : a);

        public static ColorData FromColor(Color c) => new ColorData
        {
            r = c.r,
            g = c.g,
            b = c.b,
            a = c.a
        };
    }

    [Serializable]
    public class HierarchyNodeData
    {
        public int instanceId;
        public string name;
        public string tag;
        public int layer;
        public bool activeSelf;
        public Vector3Data position;
        public Vector3Data rotation;
        public Vector3Data scale;
        public List<string> components = new List<string>();
        public List<HierarchyNodeData> children = new List<HierarchyNodeData>();
    }

    [Serializable]
    public class SceneHierarchyResponseData
    {
        public string sceneName;
        public int rootCount;
        public List<HierarchyNodeData> roots = new List<HierarchyNodeData>();
    }

    [Serializable]
    public class GameObjectResponseData
    {
        public int instanceId;
        public string name;
        public Vector3Data position;
        public Vector3Data rotation;
        public Vector3Data scale;
        public string parent;
    }

    [Serializable]
    public class ComponentResponseData
    {
        public string target;
        public string componentType;
        public string action;
        public string details;
    }

    [Serializable]
    public class MaterialResponseData
    {
        public string target;
        public string materialName;
        public string assetPath;
        public string colorHex;
    }

    [Serializable]
    public class LogEntryData
    {
        public string type;
        public string message;
        public string stackTrace;
        public string timestamp;
    }

    [Serializable]
    public class LogsResponseData
    {
        public int count;
        public List<LogEntryData> logs = new List<LogEntryData>();
    }

    [Serializable]
    public class SimpleSuccessData
    {
        public string message;
        public string target;
    }
}
