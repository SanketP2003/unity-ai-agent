using System;
using System.Threading.Tasks;
using AutonomousUnityAgent.Editor.Tools;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

// Alias to resolve ambiguity between AutonomousUnityAgent.Models.MessageType and UnityEditor.MessageType
using BridgeMessageType = AutonomousUnityAgent.Models.MessageType;

namespace AutonomousUnityAgent.Editor
{
    /// <summary>
    /// Main Editor window for the Autonomous Unity Agent bridge.
    /// Manages WebSocket connection to the Java backend, handles Protocol v1.0
    /// message exchange, and dispatches tool requests on the main thread.
    ///
    /// Access via: Window → Autonomous Agent
    /// </summary>
    public class AgentBridge : EditorWindow
    {
        // Connection configuration
        private string _serverUrl = "ws://localhost:8080/unity-bridge";
        private int _serverPort = 8080;

        // State
        private ConnectionState _connectionState = ConnectionState.DISCONNECTED;
        private WebSocketClient _webSocket;
        private ToolDispatcher _toolDispatcher;
        private DateTime _lastMessageTime;
        private int _pendingOperations;
        private string _statusMessage = "";

        // Constants
        private const string BridgeVersion = "0.1.0";
        private const float ReconnectDelay = 5f;

        [MenuItem("Window/Autonomous Agent")]
        public static void ShowWindow()
        {
            var window = GetWindow<AgentBridge>("Autonomous Agent");
            window.minSize = new Vector2(300, 400);
        }

        private void OnEnable()
        {
            // Initialize tool dispatcher and register tools
            _toolDispatcher = new ToolDispatcher();

            // Phase 4: Acceptance test tool
            _toolDispatcher.Register(new CreateTestCubeTool());

            // Phase 5: Scene & Hierarchy tools
            _toolDispatcher.Register(new GetSceneHierarchyTool());
            _toolDispatcher.Register(new CreateSceneTool());
            _toolDispatcher.Register(new SaveSceneTool());

            // Phase 5: GameObject & Transform tools
            _toolDispatcher.Register(new CreatePrimitiveTool());
            _toolDispatcher.Register(new CreateEmptyGameObjectTool());
            _toolDispatcher.Register(new SetTransformTool());
            _toolDispatcher.Register(new DestroyGameObjectTool());
            _toolDispatcher.Register(new SetParentTool());

            // Phase 5: Component & Physics tools
            _toolDispatcher.Register(new AddComponentTool());
            _toolDispatcher.Register(new SetComponentPropertyTool());
            _toolDispatcher.Register(new RemoveComponentTool());

            // Phase 5: Material & Visual tools
            _toolDispatcher.Register(new SetMaterialColorTool());
            _toolDispatcher.Register(new CreateMaterialTool());
            _toolDispatcher.Register(new ApplyMaterialTool());

            // Phase 5: Lighting & Camera tools
            _toolDispatcher.Register(new CreateLightTool());
            _toolDispatcher.Register(new CreateCameraTool());

            // Phase 5: Diagnostic & Simulation tools
            _toolDispatcher.Register(new SetPlayModeTool());
            _toolDispatcher.Register(new GetConsoleLogsTool());
            GetConsoleLogsTool.EnsureHooked();

            // Hook into the Editor update loop to process messages on the main thread
            EditorApplication.update += OnEditorUpdate;

            Debug.Log($"[AgentBridge] Initialized with {_toolDispatcher.Count} tools.");
        }

        private void OnDisable()
        {
            EditorApplication.update -= OnEditorUpdate;
            DisconnectSync();
            Debug.Log("[AgentBridge] Shut down.");
        }

        // --- Main thread message processing ---

        private void OnEditorUpdate()
        {
            if (_webSocket == null) return;

            // Check for connection loss
            if (_connectionState != ConnectionState.DISCONNECTED &&
                _connectionState != ConnectionState.CONNECTING &&
                !_webSocket.IsConnected)
            {
                Debug.LogWarning("[AgentBridge] Connection lost.");
                _connectionState = ConnectionState.DISCONNECTED;
                _statusMessage = "Connection lost";
                Repaint();
            }

            // Process incoming messages on the main thread
            while (_webSocket.TryDequeueMessage(out string json))
            {
                _lastMessageTime = DateTime.Now;
                ProcessMessage(json);
                Repaint();
            }
        }

        private void ProcessMessage(string json)
        {
            BridgeMessage msg;
            try
            {
                msg = BridgeMessage.FromJson(json);
            }
            catch (Exception ex)
            {
                Debug.LogError($"[AgentBridge] Failed to parse message: {ex.Message}");
                return;
            }

            Debug.Log($"[AgentBridge] Received: type={msg.type}, operationId={msg.operationId}");

            switch (msg.type)
            {
                case BridgeMessageType.HANDSHAKE_ACK:
                    HandleHandshakeAck(msg);
                    break;

                case BridgeMessageType.TOOL_REQUEST:
                    HandleToolRequest(msg);
                    break;

                case BridgeMessageType.PING:
                    HandlePing(msg);
                    break;

                case BridgeMessageType.ERROR:
                    HandleError(msg);
                    break;

                default:
                    Debug.LogWarning($"[AgentBridge] Unhandled message type: {msg.type}");
                    break;
            }
        }

        // --- Protocol handlers ---

        private void HandleHandshakeAck(BridgeMessage msg)
        {
            if (_connectionState == ConnectionState.HANDSHAKING)
            {
                _connectionState = ConnectionState.READY;
                _statusMessage = "Connected and ready";
                Debug.Log("[AgentBridge] Handshake complete — bridge is READY");
            }
        }

        private void HandleToolRequest(BridgeMessage msg)
        {
            if (_connectionState != ConnectionState.READY)
            {
                Debug.LogWarning("[AgentBridge] Received TOOL_REQUEST but not READY");
                SendMessage(BridgeMessage.Error(msg.operationId, "NOT_READY",
                    "Bridge is not ready"));
                return;
            }

            _pendingOperations++;
            BridgeMessage response = _toolDispatcher.Dispatch(msg);
            _pendingOperations--;

            SendMessage(response);
        }

        private void HandlePing(BridgeMessage msg)
        {
            SendMessage(BridgeMessage.Pong(msg.operationId));
        }

        private void HandleError(BridgeMessage msg)
        {
            string errorInfo = msg.errors != null && msg.errors.Count > 0
                ? msg.errors[0].message : "Unknown error";
            Debug.LogError($"[AgentBridge] Server error: {errorInfo}");
            _statusMessage = $"Error: {errorInfo}";
        }

        // --- Connection management ---

        private async void Connect()
        {
            if (_connectionState != ConnectionState.DISCONNECTED) return;

            _connectionState = ConnectionState.CONNECTING;
            _statusMessage = "Connecting...";
            Repaint();

            try
            {
                _webSocket?.Dispose();
                _webSocket = new WebSocketClient();
                await _webSocket.ConnectAsync(_serverUrl);

                _connectionState = ConnectionState.CONNECTED;
                _statusMessage = "Connected, sending handshake...";
                Repaint();

                // Send HANDSHAKE
                _connectionState = ConnectionState.HANDSHAKING;
                SendMessage(BridgeMessage.Handshake(Application.unityVersion));

                _statusMessage = "Handshaking...";
                Repaint();
            }
            catch (Exception ex)
            {
                _connectionState = ConnectionState.ERROR;
                _statusMessage = $"Connection failed: {ex.Message}";
                Debug.LogError($"[AgentBridge] {_statusMessage}");
                Repaint();
            }
        }

        private async void Disconnect()
        {
            if (_webSocket != null)
            {
                await _webSocket.DisconnectAsync();
                _webSocket.Dispose();
                _webSocket = null;
            }
            _connectionState = ConnectionState.DISCONNECTED;
            _statusMessage = "Disconnected";
            _pendingOperations = 0;
            Repaint();
        }

        private void DisconnectSync()
        {
            if (_webSocket != null)
            {
                _webSocket.Dispose();
                _webSocket = null;
            }
            _connectionState = ConnectionState.DISCONNECTED;
            _pendingOperations = 0;
        }

        private void Reconnect()
        {
            Disconnect();
            // Small delay before reconnecting
            EditorApplication.delayCall += Connect;
        }

        private void SendMessage(BridgeMessage msg)
        {
            if (_webSocket == null || !_webSocket.IsConnected)
            {
                Debug.LogWarning("[AgentBridge] Cannot send message: not connected");
                return;
            }

            string json = msg.ToJson();
            Debug.Log($"[AgentBridge] Sending: {json}");
            _webSocket.Send(json);
        }

        // --- Editor Window GUI ---

        private void OnGUI()
        {
            EditorGUILayout.Space(10);
            EditorGUILayout.LabelField("Autonomous Unity Agent", EditorStyles.boldLabel);
            EditorGUILayout.Space(5);

            DrawConnectionSection();
            EditorGUILayout.Space(10);
            DrawInfoSection();
            EditorGUILayout.Space(10);
            DrawControlSection();
        }

        private void DrawConnectionSection()
        {
            EditorGUILayout.LabelField("Connection", EditorStyles.boldLabel);

            // Status with color
            Color statusColor = _connectionState switch
            {
                ConnectionState.READY => Color.green,
                ConnectionState.CONNECTING or ConnectionState.HANDSHAKING => Color.yellow,
                ConnectionState.ERROR => Color.red,
                _ => Color.gray
            };

            GUI.contentColor = statusColor;
            EditorGUILayout.LabelField("Status:", _connectionState.ToString());
            GUI.contentColor = Color.white;

            if (!string.IsNullOrEmpty(_statusMessage))
            {
                EditorGUILayout.HelpBox(_statusMessage, UnityEditor.MessageType.None);
            }

            EditorGUILayout.Space(5);

            // Server URL
            _serverUrl = EditorGUILayout.TextField("Server URL:", _serverUrl);
        }

        private void DrawInfoSection()
        {
            EditorGUILayout.LabelField("Info", EditorStyles.boldLabel);

            EditorGUILayout.LabelField("Backend:", $"localhost:{_serverPort}");
            EditorGUILayout.LabelField("Protocol:", "1.0");
            EditorGUILayout.LabelField("Unity:", Application.unityVersion);
            EditorGUILayout.LabelField("Bridge:", BridgeVersion);
            EditorGUILayout.LabelField("Pending Operations:", _pendingOperations.ToString());
            EditorGUILayout.LabelField("Registered Tools:", _toolDispatcher?.Count.ToString() ?? "0");

            if (_lastMessageTime != default)
            {
                EditorGUILayout.LabelField("Last Message:", _lastMessageTime.ToString("HH:mm:ss"));
            }
        }

        private void DrawControlSection()
        {
            EditorGUILayout.LabelField("Controls", EditorStyles.boldLabel);

            EditorGUILayout.BeginHorizontal();

            bool canConnect = _connectionState == ConnectionState.DISCONNECTED ||
                              _connectionState == ConnectionState.ERROR;
            bool canDisconnect = _connectionState != ConnectionState.DISCONNECTED;

            GUI.enabled = canConnect;
            if (GUILayout.Button("Connect", GUILayout.Height(30)))
            {
                Connect();
            }

            GUI.enabled = canDisconnect;
            if (GUILayout.Button("Disconnect", GUILayout.Height(30)))
            {
                Disconnect();
            }

            GUI.enabled = canDisconnect;
            if (GUILayout.Button("Reconnect", GUILayout.Height(30)))
            {
                Reconnect();
            }

            GUI.enabled = true;
            EditorGUILayout.EndHorizontal();
        }
    }
}
