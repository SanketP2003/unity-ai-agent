# Unity Editor Bridge Guide

The Unity Editor Bridge provides real-time, bidirectional communication between the Spring Boot backend and the running Unity Editor instance.

## Communication Architecture

```text
Spring Boot Backend                     Unity Editor (C# Package)
  [UnityConnection]                       [AgentWebSocketClient]
          |                                          |
          |-------- JSON-RPC Command --------------->|
          |         (e.g., execute_script,           |
          |          create_gameobject)              |
          |                                          |
          |<------- JSON-RPC Response ---------------|
          |         (success / compile errors /      |
          |          scene hierarchy)                |
          |                                          |
          |<======= Heartbeat Ping/Pong =============>|
```

- **Protocol**: WebSockets over HTTP/1.1 (`ws://localhost:8080/ws/unity`)
- **Serialization**: JSON-RPC 2.0 style command/response format
- **Heartbeat Interval**: 5000 ms
- **Timeout**: 30,000 ms for normal commands, 120,000 ms for domain reloads and builds

---

## Domain Reload & Disconnection Handling

When C# scripts are created or modified:
1. Unity triggers an internal **Domain Reload** (AppDomain tear-down and rebuild).
2. The WebSocket connection drops momentarily as C# assemblies are unloaded.
3. The backend `UnityConnection` detects the transient disconnect and enters `WAITING_FOR_UNITY` state rather than failing the run.
4. Once assemblies recompile, Unity reconnects automatically.
5. In-flight requests are preserved or cleanly timed out, preventing thread deadlocks or leaked futures.

---

## Bridge Tool Specifications

The bridge exposes authoritative Unity Editor APIs:

### 1. `execute_script`
Executes C# code or creates/modifies script assets in `Assets/Scripts/`. Triggers Roslyn compilation and returns compiler diagnostics.

### 2. `create_gameobject`
Instantiates GameObjects with specified primitives, transforms, and component attachments.

### 3. `inspect_scene`
Dumps the active scene hierarchy, including all root and child GameObjects, attached components, colliders, and physics properties.

### 4. `compile_scripts`
Forces Unity to compile all pending scripts and returns structured compiler messages (Errors, Warnings).

### 5. `play_mode`
Controls Unity Play Mode (`START`, `STOP`, `PAUSE`) and streams runtime logs and assertion failures back to the backend.

### 6. `build_player`
Triggers Unity's `BuildPipeline.BuildPlayer` to generate standalone binaries (`.exe`, `.app`, `.x86_64`) for release packaging.
