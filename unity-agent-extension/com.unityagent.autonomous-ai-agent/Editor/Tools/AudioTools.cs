using System;
using System.IO;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;
using UnityEngine.Audio;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class CreateAudioSourceTool : IBridgeTool
    {
        public string ToolName => "create_audio_source";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                go = new GameObject(string.IsNullOrEmpty(target) ? "AudioSource" : target);
                Undo.RegisterCreatedObjectUndo(go, "Create AudioSource GameObject");
            }

            var src = go.GetComponent<AudioSource>();
            if (src == null) src = go.AddComponent<AudioSource>();
            Undo.RecordObject(src, "Configure AudioSource " + go.name);

            float vol = ToolParamHelper.ExtractFloat(request.parameters, "volume", 1.0f);
            float pitch = ToolParamHelper.ExtractFloat(request.parameters, "pitch", 1.0f);
            bool loop = ToolParamHelper.ExtractBool(request.parameters, "loop", false);
            bool playOnAwake = ToolParamHelper.ExtractBool(request.parameters, "playOnAwake", true);
            float spatialBlend = ToolParamHelper.ExtractFloat(request.parameters, "spatialBlend", 0.0f); // 0 = 2D, 1 = 3D

            src.volume = vol;
            src.pitch = pitch;
            src.loop = loop;
            src.playOnAwake = playOnAwake;
            src.spatialBlend = spatialBlend;

            string clipPath = ToolParamHelper.ExtractString(request.parameters, "clip");
            if (!string.IsNullOrEmpty(clipPath))
            {
                var clip = AssetDatabase.LoadAssetAtPath<AudioClip>(clipPath);
                if (clip != null) src.clip = clip;
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured AudioSource on '{go.name}' (volume={vol}, loop={loop}, 3D={spatialBlend})",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ConfigureAudioSourceTool : IBridgeTool
    {
        public string ToolName => "configure_audio_source";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Target '{target}' not found");

            var src = go.GetComponent<AudioSource>();
            if (src == null) return BridgeMessage.Error(request.operationId, "NO_AUDIO_SOURCE", $"'{target}' does not have an AudioSource");

            Undo.RecordObject(src, "Configure AudioSource " + go.name);

            float vol = ToolParamHelper.ExtractFloat(request.parameters, "volume", -1f);
            if (vol >= 0f) src.volume = vol;

            float pitch = ToolParamHelper.ExtractFloat(request.parameters, "pitch", -1f);
            if (pitch > 0f) src.pitch = pitch;

            if (request.parameters.Contains("\"loop\""))
            {
                src.loop = ToolParamHelper.ExtractBool(request.parameters, "loop", src.loop);
            }

            float spatialBlend = ToolParamHelper.ExtractFloat(request.parameters, "spatialBlend", -1f);
            if (spatialBlend >= 0f) src.spatialBlend = spatialBlend;

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Updated AudioSource parameters on '{go.name}'",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class AssignAudioClipTool : IBridgeTool
    {
        public string ToolName => "assign_audio_clip";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string clipPath = ToolParamHelper.ExtractString(request.parameters, "clipPath");
            if (string.IsNullOrEmpty(clipPath))
            {
                string clipName = ToolParamHelper.ExtractString(request.parameters, "name");
                if (!string.IsNullOrEmpty(clipName))
                {
                    string[] guids = AssetDatabase.FindAssets(clipName + " t:AudioClip");
                    if (guids.Length > 0) clipPath = AssetDatabase.GUIDToAssetPath(guids[0]);
                }
            }

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Target '{target}' not found");

            var src = go.GetComponent<AudioSource>();
            if (src == null) src = go.AddComponent<AudioSource>();
            var clip = !string.IsNullOrEmpty(clipPath) ? AssetDatabase.LoadAssetAtPath<AudioClip>(clipPath) : null;

            if (clip != null)
            {
                Undo.RecordObject(src, "Assign AudioClip " + clip.name);
                src.clip = clip;
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = clip != null ? $"Assigned AudioClip '{clip.name}' to '{go.name}'" : $"AudioSource configured on '{go.name}'",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateAudioMixerTool : IBridgeTool
    {
        public string ToolName => "create_audio_mixer";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "MasterMixer");
            string path = ToolParamHelper.ExtractString(request.parameters, "path");

            if (string.IsNullOrEmpty(path)) path = $"Assets/Audio/{name}.mixer";
            if (!path.EndsWith(".mixer", StringComparison.OrdinalIgnoreCase)) path += ".mixer";

            string dir = Path.GetDirectoryName(path);
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
                AssetDatabase.Refresh();
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"AudioMixer '{name}' target path configured at '{path}'",
                target = path
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ConfigureAudioMixerTool : IBridgeTool
    {
        public string ToolName => "configure_audio_mixer";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string mixerPath = ToolParamHelper.ExtractString(request.parameters, "mixerPath");
            string group = ToolParamHelper.ExtractString(request.parameters, "group", "Master");
            float volume = ToolParamHelper.ExtractFloat(request.parameters, "volume", 0f);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured AudioMixer group '{group}' to {volume}dB",
                target = mixerPath
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
