using System;
using System.Collections.Generic;
using UnityEngine;

namespace AutonomousUnityAgent.Models
{
    [Serializable]
    public class Vector3Data
    {
        public float x;
        public float y;
        public float z;

        public Vector3Data() { }

        public Vector3Data(float x, float y, float z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vector3 ToVector3() => new Vector3(x, y, z);

        public static Vector3Data FromVector3(Vector3 v) => new Vector3Data(v.x, v.y, v.z);
    }

    [Serializable]
    public class ColorData
    {
        public float r;
        public float g;
        public float b;
        public float a = 1f;

        public ColorData() { }

        public ColorData(float r, float g, float b, float a = 1f)
        {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        public Color ToColor() => new Color(r, g, b, a <= 0.001f ? 1f : a);

        public static ColorData FromColor(Color c) => new ColorData(c.r, c.g, c.b, c.a);
    }

    [Serializable]
    public class HierarchyNodeData
    {
        public string objectId;
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
        public string objectId;
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
        public string objectId;
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

    [Serializable]
    public class ActiveSceneResponseData
    {
        public string name;
        public string path;
        public bool isLoaded;
        public bool isDirty;
        public int rootCount;
        public int buildIndex;
    }

    [Serializable]
    public class TransformData
    {
        public Vector3Data position;
        public Vector3Data rotation;
        public Vector3Data scale;
        public Vector3Data localPosition;
        public Vector3Data localRotation;
        public Vector3Data localScale;
    }

    [Serializable]
    public class SelectedObjectResponseData
    {
        public bool hasSelection;
        public string objectId;
        public string name;
        public string hierarchyPath;
        public bool activeSelf;
        public string scene;
        public List<string> components = new List<string>();
        public TransformData transform;
        public string parent;
        public int childCount;
        public string message;
    }

    [Serializable]
    public class ComponentInfoData
    {
        public string type;
        public bool enabled;
    }

    [Serializable]
    public class ObjectComponentsResponseData
    {
        public string objectId;
        public string name;
        public string scene;
        public List<string> components = new List<string>();
        public List<ComponentInfoData> componentDetails = new List<ComponentInfoData>();
    }

    [Serializable]
    public class ObjectTransformResponseData
    {
        public string objectId;
        public string name;
        public Vector3Data position;
        public Vector3Data rotation;
        public Vector3Data scale;
        public Vector3Data localPosition;
        public Vector3Data localRotation;
        public Vector3Data localScale;
        public Vector3Data forward;
        public Vector3Data up;
        public Vector3Data right;
        public string parent;
        public string parentObjectId;
        public int childCount;
    }

    [Serializable]
    public class PlayModeStateResponseData
    {
        public bool isPlaying;
        public bool isPaused;
        public bool isCompiling;
        public string state;
    }

    [Serializable]
    public class ConsoleErrorsResponseData
    {
        public int errorCount;
        public List<LogEntryData> errors = new List<LogEntryData>();
    }
}
