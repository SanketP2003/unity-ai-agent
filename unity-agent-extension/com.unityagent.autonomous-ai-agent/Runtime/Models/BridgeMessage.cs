using System;
using System.Collections.Generic;

namespace AutonomousUnityAgent.Models
{
    /// <summary>
    /// Protocol v1.0 message exchanged between Java backend and Unity bridge.
    /// Mirror of the Java UnityMessage class.
    /// Uses Unity-compatible serialization (no external JSON dependencies required).
    /// </summary>
    [Serializable]
    public class BridgeMessage
    {
        public string protocolVersion = "1.0";
        public string type;
        public string operationId;
        public string tool;
        public string parameters; // JSON string of parameters (parsed per-tool)
        public string data;       // JSON string of response data
        public bool success;
        public string projectId;
        public List<ErrorDetail> errors;
        public List<string> warnings;

        [Serializable]
        public class ErrorDetail
        {
            public string code;
            public string message;

            public ErrorDetail() { }

            public ErrorDetail(string code, string message)
            {
                this.code = code;
                this.message = message;
            }
        }

        // --- Factory Methods ---

        public static BridgeMessage Handshake(string unityVersion, string projectId = null, string projectName = null, string projectPath = null)
        {
            var msg = new BridgeMessage
            {
                type = MessageType.HANDSHAKE,
                operationId = Guid.NewGuid().ToString(),
                protocolVersion = "1.0",
                projectId = projectId,
                data = JsonHelper.ToJson(new HandshakeData
                {
                    client = "unity",
                    extensionVersion = "1.0.0",
                    bridgeVersion = "1.0.0",
                    protocolVersion = "1.0",
                    unityVersion = unityVersion,
                    projectId = projectId,
                    projectName = projectName,
                    projectPath = projectPath,
                    capabilities = new CapabilitiesData()
                })
            };
            return msg;
        }

        public static BridgeMessage ToolResponse(string operationId, string tool, bool success,
            string data, List<ErrorDetail> errors = null, List<string> warnings = null)
        {
            return new BridgeMessage
            {
                type = MessageType.TOOL_RESPONSE,
                operationId = operationId,
                tool = tool,
                success = success,
                data = data,
                errors = errors ?? new List<ErrorDetail>(),
                warnings = warnings ?? new List<string>()
            };
        }

        public static BridgeMessage Pong(string operationId)
        {
            return new BridgeMessage
            {
                type = MessageType.PONG,
                operationId = operationId
            };
        }

        public static BridgeMessage Error(string operationId, string code, string message)
        {
            return new BridgeMessage
            {
                type = MessageType.ERROR,
                operationId = operationId,
                success = false,
                errors = new List<ErrorDetail> { new ErrorDetail(code, message) }
            };
        }

        /// <summary>
        /// Serialize this message to JSON for sending over WebSocket.
        /// </summary>
        public string ToJson()
        {
            return JsonHelper.BridgeMessageToJson(this);
        }

        /// <summary>
        /// Deserialize a JSON string into a BridgeMessage.
        /// </summary>
        public static BridgeMessage FromJson(string json)
        {
            return JsonHelper.BridgeMessageFromJson(json);
        }

        public override string ToString()
        {
            return $"BridgeMessage(type={type}, operationId={operationId}, tool={tool}, success={success})";
        }
    }

    /// <summary>
    /// Protocol message type constants.
    /// </summary>
    public static class MessageType
    {
        public const string HANDSHAKE = "HANDSHAKE";
        public const string HANDSHAKE_ACK = "HANDSHAKE_ACK";
        public const string TOOL_REQUEST = "TOOL_REQUEST";
        public const string TOOL_RESPONSE = "TOOL_RESPONSE";
        public const string EVENT = "EVENT";
        public const string ERROR = "ERROR";
        public const string PING = "PING";
        public const string PONG = "PONG";
    }

    /// <summary>
    /// Connection states matching the Java-side state machine.
    /// </summary>
    public enum ConnectionState
    {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        HANDSHAKING,
        READY,
        ERROR
    }

    // --- Helper data classes for JSON serialization ---

    [Serializable]
    public class HandshakeData
    {
        public string client;
        public string extensionVersion;
        public string bridgeVersion;
        public string protocolVersion;
        public string unityVersion;
        public string projectId;
        public string projectName;
        public string projectPath;
        public CapabilitiesData capabilities;
    }

    [Serializable]
    public class CapabilitiesData
    {
        public bool scriptTools = true;
        public bool compilation = true;
        public bool runtimeTesting = true;
        public bool behaviorTesting = true;
    }

    [Serializable]
    public class ToolResponseData
    {
        public string @object;
        public PositionData position;
    }

    [Serializable]
    public class PositionData
    {
        public float x;
        public float y;
        public float z;
    }

    /// <summary>
    /// Lightweight JSON helper using Unity's JsonUtility with fallback manual parsing
    /// for fields that JsonUtility doesn't handle well (nested dynamic JSON).
    /// </summary>
    public static class JsonHelper
    {
        public static string ToJson<T>(T obj)
        {
            return UnityEngine.JsonUtility.ToJson(obj);
        }

        [System.Serializable]
        private class Wrapper<T>
        {
            public List<T> items;
        }

        public static List<T> FromJsonList<T>(string json)
        {
            if (string.IsNullOrEmpty(json)) return new List<T>();
            string wrapped = "{\"items\":" + json + "}";
            Wrapper<T> wrapper = UnityEngine.JsonUtility.FromJson<Wrapper<T>>(wrapped);
            return wrapper != null && wrapper.items != null ? wrapper.items : new List<T>();
        }

        public static T FromJson<T>(string json)
        {
            return UnityEngine.JsonUtility.FromJson<T>(json);
        }

        /// <summary>
        /// Custom serializer for BridgeMessage since JsonUtility doesn't handle
        /// null fields and dynamic data well.
        /// </summary>
        public static string BridgeMessageToJson(BridgeMessage msg)
        {
            var parts = new List<string>();
            parts.Add($"\"protocolVersion\":\"{msg.protocolVersion}\"");

            if (msg.type != null)
                parts.Add($"\"type\":\"{msg.type}\"");

            if (msg.operationId != null)
                parts.Add($"\"operationId\":\"{msg.operationId}\"");

            if (msg.tool != null)
                parts.Add($"\"tool\":\"{msg.tool}\"");

            // success is always serialized for responses
            if (msg.type == MessageType.TOOL_RESPONSE || msg.type == MessageType.ERROR ||
                msg.type == MessageType.HANDSHAKE)
            {
                parts.Add($"\"success\":{(msg.success ? "true" : "false")}");
            }

            if (msg.projectId != null)
                parts.Add($"\"projectId\":\"{EscapeJson(msg.projectId)}\"");

            if (msg.data != null)
                parts.Add($"\"data\":{msg.data}");

            if (msg.parameters != null)
                parts.Add($"\"parameters\":{msg.parameters}");

            if (msg.errors != null && msg.errors.Count > 0)
            {
                var errorParts = new List<string>();
                foreach (var err in msg.errors)
                {
                    errorParts.Add($"{{\"code\":\"{EscapeJson(err.code)}\",\"message\":\"{EscapeJson(err.message)}\"}}");
                }
                parts.Add($"\"errors\":[{string.Join(",", errorParts)}]");
            }
            else if (msg.type == MessageType.TOOL_RESPONSE)
            {
                parts.Add("\"errors\":[]");
            }

            if (msg.warnings != null && msg.warnings.Count > 0)
            {
                var warningParts = new List<string>();
                foreach (var w in msg.warnings)
                {
                    warningParts.Add($"\"{EscapeJson(w)}\"");
                }
                parts.Add($"\"warnings\":[{string.Join(",", warningParts)}]");
            }
            else if (msg.type == MessageType.TOOL_RESPONSE)
            {
                parts.Add("\"warnings\":[]");
            }

            return "{" + string.Join(",", parts) + "}";
        }

        /// <summary>
        /// Parse incoming JSON into a BridgeMessage.
        /// Uses a simple approach: parse known top-level fields.
        /// </summary>
        public static BridgeMessage BridgeMessageFromJson(string json)
        {
            var msg = new BridgeMessage();

            msg.protocolVersion = ExtractStringField(json, "protocolVersion") ?? "1.0";
            msg.type = ExtractStringField(json, "type");
            msg.operationId = ExtractStringField(json, "operationId");
            msg.projectId = ExtractStringField(json, "projectId");
            msg.tool = ExtractStringField(json, "tool");
            msg.parameters = ExtractObjectField(json, "parameters");
            msg.data = ExtractObjectField(json, "data");

            string successStr = ExtractRawField(json, "success");
            if (successStr != null)
            {
                msg.success = successStr.Trim().ToLower() == "true";
            }

            // Parse errors array
            string errorsStr = ExtractArrayField(json, "errors");
            if (errorsStr != null && errorsStr != "[]")
            {
                msg.errors = ParseErrorArray(errorsStr);
            }

            // Parse warnings array
            string warningsStr = ExtractArrayField(json, "warnings");
            if (warningsStr != null && warningsStr != "[]")
            {
                msg.warnings = ParseStringArray(warningsStr);
            }

            return msg;
        }

        private static string EscapeJson(string s)
        {
            if (s == null) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
                    .Replace("\n", "\\n").Replace("\r", "\\r")
                    .Replace("\t", "\\t");
        }

        private static int FindColonAfterKey(string json, string field)
        {
            if (string.IsNullOrEmpty(json) || string.IsNullOrEmpty(field)) return -1;
            int keyIndex = json.IndexOf($"\"{field}\"");
            if (keyIndex < 0) return -1;
            return json.IndexOf(':', keyIndex + field.Length + 2);
        }

        private static string ExtractStringField(string json, string field)
        {
            int colonIndex = FindColonAfterKey(json, field);
            if (colonIndex < 0) return null;
            int quoteStart = json.IndexOf('"', colonIndex + 1);
            if (quoteStart < 0) return null;
            for (int i = colonIndex + 1; i < quoteStart; i++)
            {
                if (!char.IsWhiteSpace(json[i])) return null;
            }
            int quoteEnd = json.IndexOf('"', quoteStart + 1);
            while (quoteEnd > 0 && json[quoteEnd - 1] == '\\')
            {
                quoteEnd = json.IndexOf('"', quoteEnd + 1);
            }
            if (quoteEnd < 0) return null;
            return json.Substring(quoteStart + 1, quoteEnd - quoteStart - 1);
        }

        private static string ExtractRawField(string json, string field)
        {
            int colonIndex = FindColonAfterKey(json, field);
            if (colonIndex < 0) return null;
            int start = colonIndex + 1;
            while (start < json.Length && char.IsWhiteSpace(json[start])) start++;
            if (start >= json.Length) return null;
            int end = start;
            while (end < json.Length && json[end] != ',' && json[end] != '}' && !char.IsWhiteSpace(json[end])) end++;
            return json.Substring(start, end - start).Trim();
        }

        private static string ExtractObjectField(string json, string field)
        {
            int colonIndex = FindColonAfterKey(json, field);
            if (colonIndex < 0) return null;
            int start = colonIndex + 1;
            while (start < json.Length && char.IsWhiteSpace(json[start])) start++;
            if (start >= json.Length) return null;
            if (json[start] == 'n') return null; // null
            if (json[start] != '{') return null;

            int depth = 0;
            int end = start;
            for (; end < json.Length; end++)
            {
                if (json[end] == '{') depth++;
                else if (json[end] == '}') { depth--; if (depth == 0) { end++; break; } }
            }

            return json.Substring(start, end - start);
        }

        private static string ExtractArrayField(string json, string field)
        {
            int colonIndex = FindColonAfterKey(json, field);
            if (colonIndex < 0) return null;
            int start = colonIndex + 1;
            while (start < json.Length && char.IsWhiteSpace(json[start])) start++;
            if (start >= json.Length || json[start] != '[') return null;

            int depth = 0;
            int end = start;
            for (; end < json.Length; end++)
            {
                if (json[end] == '[') depth++;
                else if (json[end] == ']') { depth--; if (depth == 0) { end++; break; } }
            }

            return json.Substring(start, end - start);
        }

        private static List<BridgeMessage.ErrorDetail> ParseErrorArray(string arrayJson)
        {
            var result = new List<BridgeMessage.ErrorDetail>();
            // Simple parser: find each {...} block in the array
            int i = 0;
            while (i < arrayJson.Length)
            {
                int objStart = arrayJson.IndexOf('{', i);
                if (objStart < 0) break;
                int depth = 0;
                int objEnd = objStart;
                for (; objEnd < arrayJson.Length; objEnd++)
                {
                    if (arrayJson[objEnd] == '{') depth++;
                    else if (arrayJson[objEnd] == '}') { depth--; if (depth == 0) { objEnd++; break; } }
                }
                string objJson = arrayJson.Substring(objStart, objEnd - objStart);
                string code = ExtractStringField(objJson, "code");
                string message = ExtractStringField(objJson, "message");
                result.Add(new BridgeMessage.ErrorDetail(code, message));
                i = objEnd;
            }
            return result;
        }

        private static List<string> ParseStringArray(string arrayJson)
        {
            var result = new List<string>();
            // Simple: find quoted strings in array
            int i = 1; // skip opening [
            while (i < arrayJson.Length - 1)
            {
                int qStart = arrayJson.IndexOf('"', i);
                if (qStart < 0) break;
                int qEnd = arrayJson.IndexOf('"', qStart + 1);
                if (qEnd < 0) break;
                result.Add(arrayJson.Substring(qStart + 1, qEnd - qStart - 1));
                i = qEnd + 1;
            }
            return result;
        }
    }
}
