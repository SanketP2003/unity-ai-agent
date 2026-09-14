# Unity Editor Extension & Package Guide

The Autonomous Unity Agent Extension (`com.unityagent.autonomous-ai-agent`) bridges the Unity Editor with the Autonomous Game Studio.

## Package Structure

```text
Packages/com.unityagent.autonomous-ai-agent/
├── package.json
├── Editor/
│   ├── AgentWebSocketClient.cs        # WebSocket client connection & heartbeats
│   ├── CommandDispatcher.cs           # Dispatches execute_script, inspect_scene, etc.
│   ├── HierarchyInspector.cs          # Dumps active scene graph to JSON
│   ├── CompilerMonitor.cs             # Captures Roslyn compilation errors & warnings
│   ├── PlayModeRunner.cs              # Controls Play Mode & assertion verification
│   ├── BuildPipelineController.cs     # Triggers BuildPlayer for standalone artifacts
│   └── AgentSettingsWindow.cs         # Editor GUI for bridge configuration
└── Runtime/
    └── BehavioralAssertionLogger.cs   # In-game test harness logging assertions
```

---

## Editor Integration & GUI

When installed, the package adds menu items to the Unity top bar:
- **Autonomous Agent -> Bridge Settings**: Configures host, port, and auto-reconnect intervals.
- **Autonomous Agent -> Connection Status**: Displays live ping latency, in-flight command count, and session ID.
- **Autonomous Agent -> Force Recompile**: Flushes Unity script cache and runs Roslyn compilation check.

---

## Automatic Domain Reload Synchronization

When scripts are generated dynamically:
1. `CompilerMonitor` tracks script asset changes in `Assets/Scripts/`.
2. Unity executes its internal compilation and domain reload.
3. `AgentWebSocketClient` uses Unity's `[InitializeOnLoad]` attribute to automatically re-establish the WebSocket connection as soon as domain reloading finishes.
4. It signals `READY` back to the Spring Boot backend with the latest assembly version token.
