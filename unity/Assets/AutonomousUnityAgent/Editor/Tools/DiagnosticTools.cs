using System;
using System.Collections.Generic;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class SetPlayModeTool : IBridgeTool
    {
        public string ToolName => "set_play_mode";

        public BridgeMessage Execute(BridgeMessage request)
        {
            bool play = ToolParamHelper.ExtractBool(request.parameters, "play", false);
            EditorApplication.isPlaying = play;

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Editor play mode set to: {play}",
                target = play ? "Playing" : "EditMode"
            });

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetConsoleLogsTool : IBridgeTool
    {
        public string ToolName => "get_console_logs";

        private const int MaxLogHistory = 200;
        private static readonly List<LogEntryData> LogBuffer = new List<LogEntryData>();
        private static bool _isHooked = false;

        public static void EnsureHooked()
        {
            if (!_isHooked)
            {
                Application.logMessageReceived += OnLogMessageReceived;
                _isHooked = true;
            }
        }

        private static void OnLogMessageReceived(string condition, string stackTrace, UnityEngine.LogType type)
        {
            lock (LogBuffer)
            {
                if (LogBuffer.Count >= MaxLogHistory)
                {
                    LogBuffer.RemoveAt(0);
                }

                LogBuffer.Add(new LogEntryData
                {
                    type = type.ToString(),
                    message = condition,
                    stackTrace = (type == UnityEngine.LogType.Error || type == UnityEngine.LogType.Exception) ? stackTrace : null,
                    timestamp = DateTime.Now.ToString("HH:mm:ss.fff")
                });
            }
        }

        public BridgeMessage Execute(BridgeMessage request)
        {
            EnsureHooked();

            int count = (int)ToolParamHelper.ExtractFloat(request.parameters, "count", 50f);
            if (count <= 0) count = 50;

            string filterType = ToolParamHelper.ExtractString(request.parameters, "logType", "All");

            var responseData = new LogsResponseData();

            lock (LogBuffer)
            {
                int startIndex = Math.Max(0, LogBuffer.Count - count);
                for (int i = startIndex; i < LogBuffer.Count; i++)
                {
                    LogEntryData entry = LogBuffer[i];
                    if (filterType == "All" || string.Equals(entry.type, filterType, StringComparison.OrdinalIgnoreCase))
                    {
                        responseData.logs.Add(entry);
                    }
                }
            }

            responseData.count = responseData.logs.Count;

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }
}
