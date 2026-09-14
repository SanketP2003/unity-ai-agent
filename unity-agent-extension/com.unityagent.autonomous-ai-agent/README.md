# Autonomous Unity AI Agent Extension

Unity Package Manager (UPM) package for **Autonomous Game Studio**.
Enables safe, protocol-compliant bidirectional communication between Unity Editor and the Autonomous AI Agent backend.

## Architecture & Separation of Concerns

```text
UNITY PROJECT          = THE USER'S GAME (Assets, Scenes, legitimate AI-generated game code)
AUTONOMOUS GAME STUDIO = AI DEVELOPMENT INFRASTRUCTURE (Backend server, SQLite DB, memories, logs, artifacts)
```

- **Zero Infrastructure Pollution**: The Studio infrastructure, databases, LLM cache, logs, and artifacts are never stored in your Unity project.
- **Persistent Project Identity**: Stable UUID assigned in `ProjectSettings/AutonomousAgentIdentity.json`.
- **Protocol v1.0 Compliant**: Full tool dispatch, safe scene management, Play Mode orchestration, and compilation verification.

## Installation

### Via Unity Package Manager (Local File Path)
1. Open Unity Package Manager (`Window` > `Package Manager`).
2. Click `+` and choose **Add package from disk...**
3. Select `package.json` inside `autonomous-unity-agent/unity-agent-extension/com.unityagent.autonomous-ai-agent/`.

### Via Packages/manifest.json
Add to `"dependencies"`:
```json
"com.unityagent.autonomous-ai-agent": "file:../../autonomous-unity-agent/unity-agent-extension/com.unityagent.autonomous-ai-agent"
```

## Features
- Over 60 native Unity editor and runtime tools.
- Strict isolation: Each command verified against current project ID.
- Clean uninstallation: Safe removal without leaving orphaned files in user Assets.
