using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;
using System.Threading.Tasks;
using UnityEditor;
using UnityEngine;
using UnityEngine.Networking;

namespace AutonomousUnityAgent.Editor
{
    /// <summary>
    /// Unity EditorWindow for chatting with and controlling the Autonomous Unity Agent.
    /// Communicates with the Spring Boot AgentService API asynchronously.
    /// 
    /// Access via: Window → Autonomous AI → Agent Chat
    /// </summary>
    [InitializeOnLoad]
    public class AIAgentChatWindow : EditorWindow
    {
        static AIAgentChatWindow()
        {
            EditorApplication.delayCall += () =>
            {
                if (!HasOpenInstances<AIAgentChatWindow>())
                {
                    ShowWindow();
                }
            };
        }

        private const string DefaultServerUrl = "https://unity-ai-backend-z2a5.onrender.com";
        private const string SessionPrefKey = "AutonomousAgent_SessionId";

        // State
        private string _serverUrl = DefaultServerUrl;
        private string _sessionId = "session_001";
        private string _inputMessage = "";
        private Vector2 _scrollPosition;

        private bool _isBackendConnected = false;
        private bool _isUnityConnected = false;
        private string _llmProvider = "NVIDIA NIM";
        private bool _isLlmAvailable = false;

        private bool _isRunning = false;
        private string _currentRunId = "";
        private string _currentActivity = "";
        private float _lastStatusCheckTime = 0f;
        private float _lastPollTime = 0f;

        // Phase 6 Lifecycle State
        private string _agentState = "IDLE";
        private string _currentGoal = "";
        private string _currentPlanStep = "";
        private string _pipelineStage = "Idle";
        private string _compilationStatus = "CLEAN";
        private string _runtimeTestStatus = "CLEAN";
        private string _validationStatus = "PENDING";

        // Chat items for display
        [Serializable]
        private class ChatDisplayItem
        {
            public string Role; // USER, ASSISTANT, TOOL, SYSTEM
            public string Content;
            public string ToolName;
            public string ToolStatus; // RUNNING, SUCCESS, FAILED, CANCELLED
            public string ToolArgs;
            public string ToolResult;
            public bool IsFoldedOut = false;
            public string Timestamp;
        }

        private readonly List<ChatDisplayItem> _messages = new List<ChatDisplayItem>();
        private readonly HashSet<string> _seenEvents = new HashSet<string>();

        [MenuItem("Window/Autonomous AI/Agent Chat", false, 100)]
        public static void ShowWindow()
        {
            var window = GetWindow<AIAgentChatWindow>("Agent Chat");
            window.minSize = new Vector2(360, 480);
            window.Show();
        }

        public static void SendPrompt(string prompt, string session = null)
        {
            var window = GetWindow<AIAgentChatWindow>("Agent Chat");
            window.Show();
            if (!string.IsNullOrEmpty(session))
            {
                window._sessionId = session;
            }
            window.SendMessageToAgent(prompt);
        }

        private void OnEnable()
        {
            _serverUrl = EditorPrefs.GetString("AutonomousAgent_RuntimeUrl", DefaultServerUrl);
            if (string.IsNullOrEmpty(_serverUrl) || _serverUrl.Contains("localhost:8080"))
            {
                _serverUrl = DefaultServerUrl;
                EditorPrefs.SetString("AutonomousAgent_RuntimeUrl", _serverUrl);
            }
            _sessionId = EditorPrefs.GetString(SessionPrefKey, "session_001");
            EditorApplication.update += OnEditorUpdate;
            AgentBridge.ShowWindow();
            CheckStatus();
            LoadSessionHistory();
        }

        private void OnDisable()
        {
            EditorApplication.update -= OnEditorUpdate;
        }

        private void OnEditorUpdate()
        {
            float now = (float)EditorApplication.timeSinceStartup;

            // Poll status every 4 seconds
            if (now - _lastStatusCheckTime > 4.0f)
            {
                _lastStatusCheckTime = now;
                CheckStatus();
            }

            // Poll active run every 1 second
            if (_isRunning && !string.IsNullOrEmpty(_currentRunId) && (now - _lastPollTime > 1.2f))
            {
                _lastPollTime = now;
                PollActiveRun();
            }
        }

        private void OnGUI()
        {
            DrawHeader();
            DrawAutonomousLifecyclePanel();
            DrawQuickActions();
            DrawChatHistory();
            DrawActivityBanner();
            DrawInputArea();
        }

        private void DrawAutonomousLifecyclePanel()
        {
            if (!_isRunning && _agentState == "IDLE") return;

            EditorGUILayout.BeginVertical(EditorStyles.helpBox);
            EditorGUILayout.BeginHorizontal();
            GUI.color = _agentState == "COMPLETED" ? new Color(0.3f, 0.9f, 0.4f) :
                        _agentState == "FAILED" ? new Color(0.9f, 0.3f, 0.3f) : new Color(0.4f, 0.7f, 1f);
            EditorGUILayout.LabelField($"State: {_agentState}", EditorStyles.boldLabel, GUILayout.Width(140));
            GUI.color = Color.white;
            EditorGUILayout.LabelField($"Pipeline: {_pipelineStage}", EditorStyles.boldLabel);
            EditorGUILayout.EndHorizontal();

            if (!string.IsNullOrEmpty(_currentGoal))
            {
                EditorGUILayout.LabelField($"Goal: {_currentGoal}", EditorStyles.miniLabel);
            }
            if (!string.IsNullOrEmpty(_currentPlanStep))
            {
                EditorGUILayout.LabelField($"Step: {_currentPlanStep}", EditorStyles.miniLabel);
            }

            EditorGUILayout.BeginHorizontal();
            EditorGUILayout.LabelField($"Compile: {_compilationStatus}", EditorStyles.miniLabel);
            EditorGUILayout.LabelField($"Runtime: {_runtimeTestStatus}", EditorStyles.miniLabel);
            EditorGUILayout.LabelField($"Validation: {_validationStatus}", EditorStyles.miniLabel);
            EditorGUILayout.EndHorizontal();
            EditorGUILayout.EndVertical();
        }

        private void DrawHeader()
        {
            EditorGUILayout.BeginVertical(EditorStyles.helpBox);
            
            EditorGUILayout.BeginHorizontal();
            GUILayout.Label("Autonomous AI Agent", EditorStyles.boldLabel);
            GUILayout.FlexibleSpace();
            
            // Health indicators
            GUI.color = _isBackendConnected ? new Color(0.2f, 0.8f, 0.2f) : new Color(0.8f, 0.2f, 0.2f);
            GUILayout.Label("● Backend", EditorStyles.miniLabel);
            
            GUI.color = _isUnityConnected ? new Color(0.2f, 0.8f, 0.2f) : new Color(0.8f, 0.2f, 0.2f);
            GUILayout.Label("● Unity", EditorStyles.miniLabel);
            
            GUI.color = _isLlmAvailable ? new Color(0.2f, 0.8f, 0.2f) : new Color(0.8f, 0.5f, 0.2f);
            GUILayout.Label($"● {_llmProvider}", EditorStyles.miniLabel);
            
            GUI.color = Color.white;
            EditorGUILayout.EndHorizontal();

            // Session controls
            EditorGUILayout.BeginHorizontal();
            EditorGUILayout.PrefixLabel("Session ID");
            string newSession = EditorGUILayout.TextField(_sessionId);
            if (newSession != _sessionId && !string.IsNullOrWhiteSpace(newSession))
            {
                _sessionId = newSession.Trim();
                EditorPrefs.SetString(SessionPrefKey, _sessionId);
                _messages.Clear();
                _seenEvents.Clear();
                LoadSessionHistory();
            }

            if (GUILayout.Button("+ New", EditorStyles.miniButton, GUILayout.Width(50)))
            {
                _sessionId = "session_" + Guid.NewGuid().ToString().Substring(0, 8);
                EditorPrefs.SetString(SessionPrefKey, _sessionId);
                _messages.Clear();
                _seenEvents.Clear();
                GUI.FocusControl(null);
            }
            EditorGUILayout.EndHorizontal();

            EditorGUILayout.EndVertical();
        }

        private void DrawQuickActions()
        {
            EditorGUILayout.BeginHorizontal(EditorStyles.toolbar);
            GUILayout.Label("Quick Actions:", EditorStyles.miniBoldLabel, GUILayout.Width(85));

            GUI.enabled = !_isRunning && _isBackendConnected;

            if (GUILayout.Button("Inspect Scene", EditorStyles.toolbarButton))
            {
                SendMessageToAgent("Inspect the current Unity scene and summarize the hierarchy, important objects, components, and potential issues.");
            }

            if (GUILayout.Button("Fix Errors", EditorStyles.toolbarButton))
            {
                SendMessageToAgent("Inspect console errors and issues in the current Unity project and determine what should be fixed.");
            }

            if (GUILayout.Button("Generate Terrain", EditorStyles.toolbarButton))
            {
                SendMessageToAgent("Generate a terrain environment with ground and obstacles.");
            }

            GUI.enabled = true;
            EditorGUILayout.EndHorizontal();
        }

        private void DrawChatHistory()
        {
            _scrollPosition = EditorGUILayout.BeginScrollView(_scrollPosition, false, true, GUILayout.ExpandHeight(true));

            if (_messages.Count == 0)
            {
                EditorGUILayout.HelpBox("Welcome to the Autonomous Unity Agent.\nAsk the agent to create objects, inspect the hierarchy, add physics, or adjust materials.", UnityEditor.MessageType.Info);
            }

            for (int i = 0; i < _messages.Count; i++)
            {
                var item = _messages[i];
                DrawChatItem(item);
                EditorGUILayout.Space(4);
            }

            EditorGUILayout.EndScrollView();
        }

        private void DrawChatItem(ChatDisplayItem item)
        {
            switch (item.Role)
            {
                case "USER":
                    EditorGUILayout.BeginVertical(EditorStyles.helpBox);
                    GUI.color = new Color(0.4f, 0.7f, 1f);
                    EditorGUILayout.LabelField($"User ({item.Timestamp})", EditorStyles.miniBoldLabel);
                    GUI.color = Color.white;
                    EditorGUILayout.SelectableLabel(item.Content, EditorStyles.wordWrappedLabel, GUILayout.Height(GetTextHeight(item.Content)));
                    EditorGUILayout.EndVertical();
                    break;

                case "ASSISTANT":
                    EditorGUILayout.BeginVertical(EditorStyles.helpBox);
                    GUI.color = new Color(0.4f, 1f, 0.5f);
                    EditorGUILayout.LabelField($"Agent ({item.Timestamp})", EditorStyles.miniBoldLabel);
                    GUI.color = Color.white;
                    EditorGUILayout.SelectableLabel(item.Content, EditorStyles.wordWrappedLabel, GUILayout.Height(GetTextHeight(item.Content)));
                    EditorGUILayout.EndVertical();
                    break;

                case "TOOL":
                    EditorGUILayout.BeginVertical(EditorStyles.helpBox);
                    EditorGUILayout.BeginHorizontal();
                    string statusIcon = item.ToolStatus == "SUCCESS" ? "✓" : item.ToolStatus == "FAILED" ? "✗" : "⚙";
                    item.IsFoldedOut = EditorGUILayout.Foldout(item.IsFoldedOut, $"🔧 {item.ToolName} [{statusIcon} {item.ToolStatus}]", true);
                    EditorGUILayout.EndHorizontal();

                    if (item.IsFoldedOut)
                    {
                        if (!string.IsNullOrEmpty(item.ToolArgs))
                        {
                            EditorGUILayout.LabelField("Arguments:", EditorStyles.miniBoldLabel);
                            EditorGUILayout.TextArea(item.ToolArgs, EditorStyles.textArea);
                        }
                        if (!string.IsNullOrEmpty(item.ToolResult))
                        {
                            EditorGUILayout.LabelField("Result:", EditorStyles.miniBoldLabel);
                            EditorGUILayout.TextArea(item.ToolResult, EditorStyles.textArea);
                        }
                    }
                    EditorGUILayout.EndVertical();
                    break;

                case "SYSTEM":
                    EditorGUILayout.HelpBox(item.Content, UnityEditor.MessageType.Warning);
                    break;
            }
        }

        private void DrawActivityBanner()
        {
            if (_isRunning)
            {
                EditorGUILayout.BeginHorizontal(EditorStyles.helpBox);
                GUI.color = new Color(0.4f, 0.7f, 1.0f);
                GUILayout.Label("⚙ " + (string.IsNullOrEmpty(_currentActivity) ? "Thinking..." : _currentActivity), EditorStyles.boldLabel);
                GUI.color = Color.white;
                GUILayout.FlexibleSpace();
                if (GUILayout.Button("Cancel Run", EditorStyles.miniButton, GUILayout.Width(80)))
                {
                    CancelActiveRun();
                }
                EditorGUILayout.EndHorizontal();
            }
        }

        private void DrawInputArea()
        {
            EditorGUILayout.BeginVertical(EditorStyles.helpBox);
            EditorGUILayout.BeginHorizontal();

            GUI.enabled = !_isRunning;
            _inputMessage = EditorGUILayout.TextField(_inputMessage, GUILayout.MinHeight(26));
            
            bool hitEnter = Event.current.type == EventType.KeyDown && 
                            Event.current.keyCode == KeyCode.Return && 
                            !Event.current.shift;

            if ((GUILayout.Button("Send", GUILayout.Width(70), GUILayout.Height(26)) || hitEnter) && !string.IsNullOrWhiteSpace(_inputMessage))
            {
                SendMessageToAgent(_inputMessage.Trim());
                _inputMessage = "";
                GUI.FocusControl(null);
                Event.current.Use();
            }

            GUI.enabled = true;
            EditorGUILayout.EndHorizontal();
            EditorGUILayout.EndVertical();
        }

        private float GetTextHeight(string text)
        {
            if (string.IsNullOrEmpty(text)) return 20f;
            int lines = text.Split('\n').Length;
            return Mathf.Max(24f, lines * 18f + 8f);
        }

        // --- HTTP Requests ---

        private async void CheckStatus()
        {
            try
            {
                using (var req = UnityWebRequest.Get($"{_serverUrl}/api/status"))
                {
                    req.timeout = 3;
                    var op = req.SendWebRequest();
                    while (!op.isDone) await Task.Yield();

                    if (req.result == UnityWebRequest.Result.Success)
                    {
                        _isBackendConnected = true;
                        string json = req.downloadHandler.text;
                        _isUnityConnected = json.Contains("\"connected\":true") || json.Contains("\"ready\":true");
                        _isLlmAvailable = json.Contains("\"available\":true");
                    }
                    else
                    {
                        _isBackendConnected = false;
                        _isUnityConnected = false;
                        _isLlmAvailable = false;
                    }
                }
            }
            catch
            {
                _isBackendConnected = false;
                _isUnityConnected = false;
            }
            Repaint();
        }

        private async void SendMessageToAgent(string message)
        {
            if (string.IsNullOrWhiteSpace(message) || _isRunning) return;

            _messages.Add(new ChatDisplayItem
            {
                Role = "USER",
                Content = message,
                Timestamp = DateTime.Now.ToString("HH:mm:ss")
            });

            _isRunning = true;
            _currentActivity = "Initiating run...";
            _scrollPosition.y = float.MaxValue;
            Repaint();

            try
            {
                string payload = "{\"sessionId\":\"" + EscapeJson(_sessionId) + "\",\"message\":\"" + EscapeJson(message) + "\",\"async\":true}";
                using (var req = new UnityWebRequest($"{_serverUrl}/api/agent/run", "POST"))
                {
                    byte[] bodyRaw = Encoding.UTF8.GetBytes(payload);
                    req.uploadHandler = new UploadHandlerRaw(bodyRaw);
                    req.downloadHandler = new DownloadHandlerBuffer();
                    req.SetRequestHeader("Content-Type", "application/json");

                    var op = req.SendWebRequest();
                    while (!op.isDone) await Task.Yield();

                    if (req.result == UnityWebRequest.Result.Success)
                    {
                        string respText = req.downloadHandler.text;
                        // Extract agentRunId
                        _currentRunId = ExtractJsonString(respText, "agentRunId");
                        _currentActivity = "Thinking...";
                    }
                    else if (req.responseCode == 409)
                    {
                        _isRunning = false;
                        _messages.Add(new ChatDisplayItem
                        {
                            Role = "SYSTEM",
                            Content = "Conflict: Another agent run is already active for this session.",
                            Timestamp = DateTime.Now.ToString("HH:mm:ss")
                        });
                    }
                    else
                    {
                        _isRunning = false;
                        _messages.Add(new ChatDisplayItem
                        {
                            Role = "SYSTEM",
                            Content = "Failed to start agent run: " + req.error,
                            Timestamp = DateTime.Now.ToString("HH:mm:ss")
                        });
                    }
                }
            }
            catch (Exception e)
            {
                _isRunning = false;
                _messages.Add(new ChatDisplayItem
                {
                    Role = "SYSTEM",
                    Content = "Error: " + e.Message,
                    Timestamp = DateTime.Now.ToString("HH:mm:ss")
                });
            }
            Repaint();
        }

        private async void PollActiveRun()
        {
            if (string.IsNullOrEmpty(_currentRunId)) return;

            try
            {
                using (var req = UnityWebRequest.Get($"{_serverUrl}/api/agent/run/{_currentRunId}"))
                {
                    req.timeout = 3;
                    var op = req.SendWebRequest();
                    while (!op.isDone) await Task.Yield();

                    if (req.result == UnityWebRequest.Result.Success)
                    {
                        string json = req.downloadHandler.text;
                        string status = ExtractJsonString(json, "status");
                        string activity = ExtractJsonString(json, "currentActivity");
                        string state = ExtractJsonString(json, "agentState");
                        if (!string.IsNullOrEmpty(state))
                        {
                            _agentState = state;
                        }

                        if (!string.IsNullOrEmpty(activity))
                        {
                            _currentActivity = activity;
                            if (activity.Contains("Compil"))
                            {
                                _compilationStatus = "COMPILING";
                                _pipelineStage = "Compiling";
                            }
                            else if (activity.Contains("Compilation succeeded"))
                            {
                                _compilationStatus = "CLEAN";
                            }
                            else if (activity.Contains("Compilation failed"))
                            {
                                _compilationStatus = "ERRORS";
                            }
                            else if (activity.Contains("Runtime") || activity.Contains("Play Mode"))
                            {
                                _runtimeTestStatus = "TESTING";
                                _pipelineStage = "Testing";
                            }
                            else if (activity.Contains("Validat"))
                            {
                                _validationStatus = "VALIDATING";
                                _pipelineStage = "Validating";
                            }
                            else if (activity.Contains("verified") || activity.Contains("satisfied"))
                            {
                                _validationStatus = "SATISFIED";
                            }
                            else if (activity.Contains("Planning"))
                            {
                                _pipelineStage = "Planning";
                            }
                            else if (activity.Contains("Executing") || activity.Contains("Calling create_") || activity.Contains("Calling set_"))
                            {
                                _pipelineStage = "Building";
                            }
                        }

                        // Check completion
                        if (status == "COMPLETED")
                        {
                            _isRunning = false;
                            _currentRunId = "";
                            _agentState = "COMPLETED";
                            _pipelineStage = "Completed";
                            LoadSessionHistory();
                        }
                        else if (status == "FAILED" || status == "CANCELLED")
                        {
                            _isRunning = false;
                            _currentRunId = "";
                            _agentState = status;
                            _pipelineStage = status;
                            string err = ExtractJsonString(json, "errorMessage");
                            _messages.Add(new ChatDisplayItem
                            {
                                Role = "SYSTEM",
                                Content = $"Agent run ended with status: {status} {(string.IsNullOrEmpty(err) ? "" : "(" + err + ")")}",
                                Timestamp = DateTime.Now.ToString("HH:mm:ss")
                            });
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Debug.LogWarning("Agent poll error: " + ex.Message);
            }
            Repaint();
        }

        private async void CancelActiveRun()
        {
            if (string.IsNullOrEmpty(_currentRunId)) return;
            try
            {
                using (var req = new UnityWebRequest($"{_serverUrl}/api/agent/run/{_currentRunId}/cancel", "POST"))
                {
                    req.downloadHandler = new DownloadHandlerBuffer();
                    var op = req.SendWebRequest();
                    while (!op.isDone) await Task.Yield();
                }
            }
            catch (Exception ex)
            {
                Debug.LogWarning("Cancel run error: " + ex.Message);
            }
        }

        private async void LoadSessionHistory()
        {
            try
            {
                using (var req = UnityWebRequest.Get($"{_serverUrl}/api/agent/session/{_sessionId}"))
                {
                    req.timeout = 3;
                    var op = req.SendWebRequest();
                    while (!op.isDone) await Task.Yield();

                    if (req.result == UnityWebRequest.Result.Success)
                    {
                        string json = req.downloadHandler.text;
                        // Simple parser for messages in JSON
                        // Updates messages if session data exists
                        ParseSessionMessages(json);
                    }
                }
            }
            catch (Exception ex)
            {
                Debug.LogWarning("Load session history error: " + ex.Message);
            }
            Repaint();
        }

        private void ParseSessionMessages(string json)
        {
            if (string.IsNullOrEmpty(json) || !json.Contains("\"messages\":")) return;
            
            // Clear current list to re-sync
            _messages.Clear();

            // Extract assistant and user messages
            int msgIdx = json.IndexOf("\"messages\":");
            if (msgIdx < 0) return;

            string msgsSection = json.Substring(msgIdx);
            string[] items = msgsSection.Split(new[] { "{\"role\":\"" }, StringSplitOptions.RemoveEmptyEntries);

            for (int i = 1; i < items.Length; i++)
            {
                string block = items[i];
                int roleEnd = block.IndexOf("\"");
                if (roleEnd < 0) continue;
                string role = block.Substring(0, roleEnd);

                string content = "";
                int contentKeyIdx = block.IndexOf("\"content\":\"");
                if (contentKeyIdx >= 0)
                {
                    int start = contentKeyIdx + 11;
                    int end = block.IndexOf("\"", start);
                    if (end > start)
                    {
                        content = block.Substring(start, end - start)
                            .Replace("\\n", "\n")
                            .Replace("\\\"", "\"");
                    }
                }

                if (!string.IsNullOrEmpty(content))
                {
                    _messages.Add(new ChatDisplayItem
                    {
                        Role = role,
                        Content = content,
                        Timestamp = DateTime.Now.ToString("HH:mm:ss")
                    });
                }
            }
        }

        private string EscapeJson(string str)
        {
            if (string.IsNullOrEmpty(str)) return "";
            return str.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\n", "\\n").Replace("\r", "\\r");
        }

        private string ExtractJsonString(string json, string key)
        {
            if (string.IsNullOrEmpty(json)) return "";
            string search = $"\"{key}\":\"";
            int idx = json.IndexOf(search);
            if (idx < 0) return "";
            int start = idx + search.Length;
            int end = json.IndexOf("\"", start);
            if (end < 0) return "";
            return json.Substring(start, end - start);
        }
    }
}
