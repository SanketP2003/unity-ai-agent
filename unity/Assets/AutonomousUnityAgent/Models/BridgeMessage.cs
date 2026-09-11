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

        public static BridgeMessage Handshake(string unityVersion)
        {
            var msg = new BridgeMessage
            {
                type = MessageType.HANDSHAKE,
                operationId = Guid.NewGuid().ToString(),
                data = JsonHelper.ToJson(new HandshakeData
                {
                    client = "unity",
                    bridgeVersion = "0.1.0",
                    unityVersion = unityVersion
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
        public string bridgeVersion;
        public string unityVersion;
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

        private static string ExtractStringField(string json, string field)
        {
            string pattern = $"\"{field}\":\"";
            int start = json.IndexOf(pattern);
            if (start < 0) return null;
            start += pattern.Length;
            int end = json.IndexOf("\"", start);
            if (end < 0) return null;
            return json.Substring(start, end - start);
        }

        private static string ExtractRawField(string json, string field)
        {
            string pattern = $"\"{field}\":";
            int start = json.IndexOf(pattern);
            if (start < 0) return null;
            start += pattern.Length;

            // Skip whitespace
            while (start < json.Length && char.IsWhiteSpace(json[start])) start++;

            // Read until comma, closing brace, or end
            int end = start;
            while (end < json.Length && json[end] != ',' && json[end] != '}') end++;
            return json.Substring(start, end - start).Trim();
        }

        private static string ExtractObjectField(string json, string field)
        {
            string pattern = $"\"{field}\":";
            int start = json.IndexOf(pattern);
            if (start < 0) return null;
            start += pattern.Length;

            // Skip whitespace
            while (start < json.Length && char.IsWhiteSpace(json[start])) start++;

            if (start >= json.Length) return null;

            char firstChar = json[start];
            if (firstChar == 'n') // null
            {
                return null;
            }

            if (firstChar != '{') return null;

            // Find matching closing brace
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
            string pattern = $"\"{field}\":";
            int start = json.IndexOf(pattern);
            if (start < 0) return null;
            start += pattern.Length;

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
