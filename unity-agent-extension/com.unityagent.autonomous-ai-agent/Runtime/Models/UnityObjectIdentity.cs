using System;
using UnityEngine;

namespace AutonomousUnityAgent.Models
{
    /// <summary>
    /// Separates the AI agent's logical objectId abstraction from Unity's transient engine InstanceID.
    /// In Unity, InstanceID is session-scoped and changes across Editor restarts or scene reloads.
    /// This abstraction allows a permanent persistent GUID strategy to be introduced later
    /// without modifying the bridge protocol or AgentLoop.
    /// </summary>
    public static class UnityObjectIdentity
    {
        public const string Prefix = "obj_";

        public static string FormatObjectId(GameObject go)
        {
            if (go == null) return null;
#pragma warning disable CS0618
            return $"{Prefix}{go.GetInstanceID()}";
#pragma warning restore CS0618
        }

        public static string FormatObjectId(int instanceId)
        {
            return $"{Prefix}{instanceId}";
        }

        public static bool TryParseInstanceId(string objectId, out int instanceId)
        {
            instanceId = 0;
            if (string.IsNullOrEmpty(objectId)) return false;

            if (objectId.StartsWith(Prefix, StringComparison.OrdinalIgnoreCase))
            {
                return int.TryParse(objectId.Substring(Prefix.Length), out instanceId);
            }

            return int.TryParse(objectId, out instanceId);
        }
    }
}
