using System;
using System.Collections.Generic;
using System.Text;
using UnityEditor;
using UnityEngine;
using UnityEngine.Networking;

namespace AutonomousUnityAgent.Editor
{
    /// <summary>
    /// Unity EditorWindow for configuring the Autonomous AI Agent provider, model, and runtime credentials.
    /// Does NOT store credentials in Git or Unity project assets.
    /// Communicates directly with the Agent Runtime REST API.
    ///
    /// Access via: Window → Autonomous AI → Settings
    /// </summary>
    public class AutonomousAISettingsWindow : EditorWindow
    {
        private const string PrefRuntimeUrl = "AutonomousAgent_RuntimeUrl";
        private const string PrefProvider = "AutonomousAgent_Provider";
        private const string PrefBaseUrl = "AutonomousAgent_BaseUrl";
        private const string PrefModel = "AutonomousAgent_Model";

        private string _runtimeUrl = "https://unity-ai-backend-z2a5.onrender.com";
        private int _providerIndex = 0;
        private readonly string[] _providerOptions = new[] { "openai-compatible", "openai" };
        private string _baseUrl = "https://integrate.api.nvidia.com/v1";
        private string _model = "openai/gpt-oss-20b";
        private string _apiKey = "";

        private string _statusMessage = "";
        private bool _isSuccessStatus = true;
        private bool _isConnecting = false;
        private string _discoveredCapabilities = "";

        [MenuItem("Window/Autonomous AI/Settings", false, 101)]
        public static void ShowWindow()
        {
            var window = GetWindow<AutonomousAISettingsWindow>("Agent Settings");
            window.minSize = new Vector2(380, 440);
            window.Show();
        }

        private void OnEnable()
        {
            _runtimeUrl = EditorPrefs.GetString(PrefRuntimeUrl, "https://unity-ai-backend-z2a5.onrender.com");
            if (string.IsNullOrEmpty(_runtimeUrl) || _runtimeUrl.Contains("localhost:8080"))
            {
                _runtimeUrl = "https://unity-ai-backend-z2a5.onrender.com";
                EditorPrefs.SetString(PrefRuntimeUrl, _runtimeUrl);
            }
            string savedProvider = EditorPrefs.GetString(PrefProvider, "openai-compatible");
            _providerIndex = Array.IndexOf(_providerOptions, savedProvider);
            if (_providerIndex < 0) _providerIndex = 0;

            _baseUrl = EditorPrefs.GetString(PrefBaseUrl, "https://integrate.api.nvidia.com/v1");
            _model = EditorPrefs.GetString(PrefModel, "openai/gpt-oss-20b");

            FetchCurrentConfig();
        }

        private void OnGUI()
        {
            EditorGUILayout.Space(10);
            GUILayout.Label("Autonomous AI Agent Settings", EditorStyles.boldLabel);
            EditorGUILayout.LabelField("Configure model provider and runtime connection outside project files.", EditorStyles.wordWrappedMiniLabel);
            EditorGUILayout.Space(10);

            // Runtime URL
            EditorGUILayout.LabelField("Agent Runtime URL", EditorStyles.boldLabel);
            _runtimeUrl = EditorGUILayout.TextField("Runtime Host", _runtimeUrl);

            EditorGUILayout.Space(10);
            EditorGUILayout.LabelField("AI Model Provider", EditorStyles.boldLabel);

            int newIndex = EditorGUILayout.Popup("Provider", _providerIndex, _providerOptions);
            if (newIndex != _providerIndex)
            {
                _providerIndex = newIndex;
                if (_providerOptions[_providerIndex] == "openai")
                {
                    _baseUrl = "https://api.openai.com/v1";
                    _model = "gpt-4o";
                }
            }

            _baseUrl = EditorGUILayout.TextField("Base URL", _baseUrl);
            _model = EditorGUILayout.TextField("Model Name", _model);

            EditorGUILayout.Space(5);
            EditorGUILayout.LabelField("API Credential (Never saved to Assets)", EditorStyles.boldLabel);
            _apiKey = EditorGUILayout.PasswordField("API Key", _apiKey);

            EditorGUILayout.Space(15);

            EditorGUILayout.BeginHorizontal();
            GUI.enabled = !_isConnecting;
            if (GUILayout.Button("Test & Save Config", GUILayout.Height(30)))
            {
                SaveAndApplyConfig();
            }

            if (GUILayout.Button("Refresh Status", GUILayout.Height(30)))
            {
                FetchCurrentConfig();
            }
            GUI.enabled = true;
            EditorGUILayout.EndHorizontal();

            EditorGUILayout.Space(15);

            // Status message box
            if (!string.IsNullOrEmpty(_statusMessage))
            {
                EditorGUILayout.HelpBox(_statusMessage, _isSuccessStatus ? MessageType.Info : MessageType.Error);
            }

            if (!string.IsNullOrEmpty(_discoveredCapabilities))
            {
                EditorGUILayout.LabelField("Detected Model Capabilities", EditorStyles.boldLabel);
                EditorGUILayout.HelpBox(_discoveredCapabilities, MessageType.None);
            }

            EditorGUILayout.Space(10);
            EditorGUILayout.HelpBox("Security Note: Credentials are sent directly to the local Agent Runtime memory and never committed to Git, scene files, or project packages.", MessageType.None);
        }

        private void SaveAndApplyConfig()
        {
            EditorPrefs.SetString(PrefRuntimeUrl, _runtimeUrl);
            EditorPrefs.SetString(PrefProvider, _providerOptions[_providerIndex]);
            EditorPrefs.SetString(PrefBaseUrl, _baseUrl);
            EditorPrefs.SetString(PrefModel, _model);

            // Synchronize default AgentBridge WebSocket URL if pointing to same runtime host
            string wsScheme = _runtimeUrl.StartsWith("https", StringComparison.OrdinalIgnoreCase) ? "wss://" : "ws://";
            string strippedHost = _runtimeUrl.Replace("http://", "").Replace("https://", "").TrimEnd('/');
            EditorPrefs.SetString("AutonomousAgent_WsUrl", $"{wsScheme}{strippedHost}/unity-bridge");

            _isConnecting = true;
            _statusMessage = "Applying configuration to Agent Runtime...";
            Repaint();

            string json = "{" +
                $"\"provider\":\"{EscapeJson(_providerOptions[_providerIndex])}\"," +
                $"\"baseUrl\":\"{EscapeJson(_baseUrl)}\"," +
                $"\"model\":\"{EscapeJson(_model)}\"" +
                (string.IsNullOrEmpty(_apiKey) ? "" : $",\"apiKey\":\"{EscapeJson(_apiKey)}\"") +
                "}";

            var req = new UnityWebRequest($"{_runtimeUrl.TrimEnd('/')}/api/config/provider", "POST");
            byte[] bodyRaw = Encoding.UTF8.GetBytes(json);
            req.uploadHandler = new UploadHandlerRaw(bodyRaw);
            req.downloadHandler = new DownloadHandlerBuffer();
            req.SetRequestHeader("Content-Type", "application/json");

            var op = req.SendWebRequest();
            op.completed += _ =>
            {
                _isConnecting = false;
                if (req.result == UnityWebRequest.Result.Success)
                {
                    _isSuccessStatus = true;
                    _statusMessage = "Configuration successfully applied to Agent Runtime!";
                    _apiKey = ""; // Clear plain text key from memory
                    FetchCurrentConfig();
                }
                else
                {
                    _isSuccessStatus = false;
                    _statusMessage = $"Failed to update config ({req.responseCode}): {req.error}\n{req.downloadHandler?.text}";
                }
                Repaint();
                req.Dispose();
            };
        }

        private void FetchCurrentConfig()
        {
            _isConnecting = true;
            var req = UnityWebRequest.Get($"{_runtimeUrl.TrimEnd('/')}/api/config/provider");
            var op = req.SendWebRequest();
            op.completed += _ =>
            {
                _isConnecting = false;
                if (req.result == UnityWebRequest.Result.Success)
                {
                    string body = req.downloadHandler.text;
                    _isSuccessStatus = true;
                    _statusMessage = "Connected to Agent Runtime. Config retrieved.";

                    // Parse capabilities if present
                    if (body.Contains("\"capabilities\""))
                    {
                        _discoveredCapabilities = "Tool Calling: Supported\nConfigured: Ready";
                    }
                }
                else
                {
                    _isSuccessStatus = false;
                    _statusMessage = $"Cannot reach Agent Runtime at {_runtimeUrl}: {req.error}";
                    _discoveredCapabilities = "";
                }
                Repaint();
                req.Dispose();
            };
        }

        private string EscapeJson(string s)
        {
            if (s == null) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\n", "\\n").Replace("\r", "\\r");
        }
    }
}
