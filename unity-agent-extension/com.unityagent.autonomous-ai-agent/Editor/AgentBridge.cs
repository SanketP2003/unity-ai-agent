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
    [InitializeOnLoad]
    public class AgentBridge : EditorWindow
    {
        private static float _lastAutoCheckTime = 0f;

        [InitializeOnLoadMethod]
        private static void AutoStartBridge()
        {
            EditorApplication.delayCall += () =>
            {
                if (!HasOpenInstances<AgentBridge>())
                {
                    ShowWindow();
                }
            };
        }

        static AgentBridge()
        {
            EditorApplication.update += EnsureRunning;
            EditorApplication.delayCall += EnsureRunning;
        }

        private static void EnsureRunning()
        {
            if (EditorApplication.timeSinceStartup - _lastAutoCheckTime > 2.0f)
            {
                _lastAutoCheckTime = (float)EditorApplication.timeSinceStartup;
                if (!HasOpenInstances<AgentBridge>())
                {
                    ShowWindow();
                }
            }
        }

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
        private string _projectId = "";

        // Constants
        private const string BridgeVersion = "0.1.0";
        private const float ReconnectDelay = 3f;
        private float _lastReconnectAttemptTime = 0f;

        [MenuItem("Window/Autonomous Agent")]
        public static void ShowWindow()
        {
            var window = GetWindow<AgentBridge>("Autonomous Agent");
            window.minSize = new Vector2(300, 400);
            window.Show();
        }

        private void OnEnable()
        {
            Application.runInBackground = true;
            // Initialize persistent project identity
            var identity = ProjectIdentity.GetOrCreateIdentity();
            _projectId = identity.projectId;

            // Initialize tool dispatcher and register tools
            _toolDispatcher = new ToolDispatcher();

            // Phase 4: Acceptance test tool
            _toolDispatcher.Register(new CreateTestCubeTool());

            // Scene & Hierarchy tools
            _toolDispatcher.Register(new GetSceneHierarchyTool());
            _toolDispatcher.Register(new GetActiveSceneTool());
            _toolDispatcher.Register(new CreateSceneTool());
            _toolDispatcher.Register(new SaveSceneTool());

            // Perception tools
            _toolDispatcher.Register(new GetSelectedObjectTool());
            _toolDispatcher.Register(new GetObjectComponentsTool());
            _toolDispatcher.Register(new GetObjectTransformTool());
            _toolDispatcher.Register(new GetPlayModeStateTool());
            _toolDispatcher.Register(new GetConsoleErrorsTool());

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

            // Phase 6: Script Management tools
            _toolDispatcher.Register(new CreateScriptTool());
            _toolDispatcher.Register(new ReadScriptTool());
            _toolDispatcher.Register(new UpdateScriptTool());
            _toolDispatcher.Register(new DeleteScriptTool());
            _toolDispatcher.Register(new ListScriptsTool());

            // Phase 6: Compilation & Project tools
            _toolDispatcher.Register(new GetProjectInfoTool());
            _toolDispatcher.Register(new CompileProjectTool());

            // Phase 6: Runtime & Validation tools
            _toolDispatcher.Register(new EnterPlayModeTool());
            _toolDispatcher.Register(new ExitPlayModeTool());
            _toolDispatcher.Register(new RunGameTestTool());
            _toolDispatcher.Register(new ValidateGameStateTool());

            // Phase 7A: Scene & GameObject Expansion tools
            _toolDispatcher.Register(new OpenSceneTool());
            _toolDispatcher.Register(new GetSceneInfoTool());
            _toolDispatcher.Register(new CreateGameObjectTool());
            _toolDispatcher.Register(new DuplicateGameObjectTool());
            _toolDispatcher.Register(new RenameGameObjectTool());
            _toolDispatcher.Register(new MoveGameObjectTool());
            _toolDispatcher.Register(new FindGameObjectsTool());

            // Phase 7B: Generic Component System tools
            _toolDispatcher.Register(new GetComponentTool());
            _toolDispatcher.Register(new GetComponentPropertiesTool());

            // Phase 7C: Prefab System tools
            _toolDispatcher.Register(new CreatePrefabTool());
            _toolDispatcher.Register(new OpenPrefabTool());
            _toolDispatcher.Register(new UpdatePrefabTool());
            _toolDispatcher.Register(new InstantiatePrefabTool());
            _toolDispatcher.Register(new GetPrefabInfoTool());
            _toolDispatcher.Register(new ApplyPrefabChangesTool());

            // Phase 7D: Material & Visual Expansion tools
            _toolDispatcher.Register(new SetMaterialPropertyTool());
            _toolDispatcher.Register(new GetMaterialPropertiesTool());
            _toolDispatcher.Register(new AssignMaterialTool());
            _toolDispatcher.Register(new SetRendererMaterialTool());
            _toolDispatcher.Register(new CreateShaderGraphAssetTool());

            // Phase 7E & 7F: Lighting & Camera tools
            _toolDispatcher.Register(new ConfigureLightTool());
            _toolDispatcher.Register(new GetLightsTool());
            _toolDispatcher.Register(new ConfigureEnvironmentLightingTool());
            _toolDispatcher.Register(new ConfigureFogTool());
            _toolDispatcher.Register(new ConfigureCameraTool());
            _toolDispatcher.Register(new SetActiveCameraTool());
            _toolDispatcher.Register(new FollowTargetTool());
            _toolDispatcher.Register(new LookAtTargetTool());
            _toolDispatcher.Register(new GetCameraInfoTool());

            // Phase 7G: UI System tools
            _toolDispatcher.Register(new CreateCanvasTool());
            _toolDispatcher.Register(new CreatePanelTool());
            _toolDispatcher.Register(new CreateTextTool());
            _toolDispatcher.Register(new CreateImageTool());
            _toolDispatcher.Register(new CreateButtonTool());
            _toolDispatcher.Register(new CreateSliderTool());
            _toolDispatcher.Register(new CreateProgressBarTool());
            _toolDispatcher.Register(new CreateUIElementTool());
            _toolDispatcher.Register(new SetUIPropertyTool());
            _toolDispatcher.Register(new SetUILayoutTool());
            _toolDispatcher.Register(new BindUIEventTool());

            // Phase 7H: Input tools
            _toolDispatcher.Register(new CreateInputActionTool());
            _toolDispatcher.Register(new ConfigureInputActionTool());
            _toolDispatcher.Register(new GetInputActionsTool());
            _toolDispatcher.Register(new BindInputActionTool());

            // Phase 7I: Animation tools
            _toolDispatcher.Register(new CreateAnimatorControllerTool());
            _toolDispatcher.Register(new CreateAnimationStateTool());
            _toolDispatcher.Register(new SetAnimationParameterTool());
            _toolDispatcher.Register(new CreateAnimationTransitionTool());
            _toolDispatcher.Register(new AssignAnimatorControllerTool());
            _toolDispatcher.Register(new GetAnimatorInfoTool());

            // Phase 7J: Audio tools
            _toolDispatcher.Register(new CreateAudioSourceTool());
            _toolDispatcher.Register(new ConfigureAudioSourceTool());
            _toolDispatcher.Register(new AssignAudioClipTool());
            _toolDispatcher.Register(new CreateAudioMixerTool());
            _toolDispatcher.Register(new ConfigureAudioMixerTool());

            // Phase 7K: Physics tools
            _toolDispatcher.Register(new ConfigureRigidbodyTool());
            _toolDispatcher.Register(new ConfigureColliderTool());
            _toolDispatcher.Register(new CreatePhysicsMaterialTool());
            _toolDispatcher.Register(new ConfigureJointTool());
            _toolDispatcher.Register(new SetGravityTool());

            // Phase 7L: Navigation tools
            _toolDispatcher.Register(new ConfigureNavigationTool());
            _toolDispatcher.Register(new BuildNavigationTool());
            _toolDispatcher.Register(new CreateNavMeshAgentTool());
            _toolDispatcher.Register(new ConfigureNavMeshAgentTool());

            // Phase 7N: Asset Management tools
            _toolDispatcher.Register(new ListAssetsTool());
            _toolDispatcher.Register(new FindAssetTool());
            _toolDispatcher.Register(new GetAssetInfoTool());
            _toolDispatcher.Register(new CreateAssetFolderTool());
            _toolDispatcher.Register(new MoveAssetTool());
            _toolDispatcher.Register(new RenameAssetTool());
            _toolDispatcher.Register(new DeleteAssetTool());

            // Phase 7O: Project Configuration tools
            _toolDispatcher.Register(new GetProjectSettingsTool());
            _toolDispatcher.Register(new GetBuildSettingsTool());
            _toolDispatcher.Register(new SetBuildSettingTool());
            _toolDispatcher.Register(new GetLayersTool());
            _toolDispatcher.Register(new SetLayerTool());
            _toolDispatcher.Register(new GetTagsTool());
            _toolDispatcher.Register(new CreateTagTool());

            // Phase 7P / Phase 12: Build Pipeline tools
            _toolDispatcher.Register(new BuildProjectTool());
            _toolDispatcher.Register(new GetBuildResultTool());
            _toolDispatcher.Register(new ValidateBuildSettingsTool());
            _toolDispatcher.Register(new GetPlatformCapabilitiesTool());

            // Hook into the Editor update loop to process messages on the main thread
            EditorApplication.update += OnEditorUpdate;
            EditorApplication.delayCall += Connect;

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
            if (_connectionState == ConnectionState.DISCONNECTED || _connectionState == ConnectionState.ERROR)
            {
                if (EditorApplication.timeSinceStartup - _lastReconnectAttemptTime > ReconnectDelay)
                {
                    _lastReconnectAttemptTime = (float)EditorApplication.timeSinceStartup;
                    _connectionState = ConnectionState.DISCONNECTED;
                    Connect();
                }
                return;
            }

            if (_webSocket == null) return;


            // Check for connection loss
            if (_connectionState != ConnectionState.DISCONNECTED &&
                _connectionState != ConnectionState.CONNECTING &&
                !_webSocket.IsConnected)
            {
                Debug.LogWarning("[AgentBridge] Connection lost.");
                _connectionState = ConnectionState.DISCONNECTED;
                _statusMessage = "Connection lost";
                _lastReconnectAttemptTime = (float)EditorApplication.timeSinceStartup;
                Repaint();
                return;
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
                if (!string.IsNullOrEmpty(msg.projectId))
                {
                    _projectId = msg.projectId;
                    ProjectIdentity.SetProjectId(_projectId);
                }
                _connectionState = ConnectionState.READY;
                _statusMessage = string.IsNullOrEmpty(_projectId) ? "Connected and ready" : $"Connected ({_projectId})";
                Debug.Log($"[AgentBridge] Handshake complete — bridge is READY for projectId={_projectId}");
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

            // Phase 14: Reject misrouted requests targeted at a different project
            if (!string.IsNullOrEmpty(msg.projectId) && !string.IsNullOrEmpty(_projectId) && msg.projectId != _projectId)
            {
                Debug.LogError($"[AgentBridge] Project mismatch: incoming={msg.projectId}, bridge={_projectId}");
                SendMessage(BridgeMessage.Error(msg.operationId, "PROJECT_MISMATCH",
                    $"Tool request targeted for project '{msg.projectId}', but bridge is connected to '{_projectId}'"));
                return;
            }

            _pendingOperations++;
            BridgeMessage response = _toolDispatcher.Dispatch(msg);
            _pendingOperations--;

            if (response != null)
            {
                response.projectId = _projectId;
                SendMessage(response);
            }
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

                // Send HANDSHAKE with persistent project identity
                _connectionState = ConnectionState.HANDSHAKING;
                var identity = ProjectIdentity.GetOrCreateIdentity();
                _projectId = identity.projectId;
                SendMessage(BridgeMessage.Handshake(Application.unityVersion, _projectId, identity.projectName, identity.projectPath));

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
            EditorGUILayout.LabelField("Project Name:", ProjectIdentity.GetProjectName());
            EditorGUILayout.LabelField("Project ID:", string.IsNullOrEmpty(_projectId) ? ProjectIdentity.GetOrCreateProjectId() : _projectId);
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
