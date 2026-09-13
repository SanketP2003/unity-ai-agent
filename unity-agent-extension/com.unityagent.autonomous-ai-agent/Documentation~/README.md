# Autonomous Unity AI Agent Extension

**Package Name**: `com.unityagent.autonomous-ai-agent`  
**Version**: `1.0.0`  
**Protocol Version**: `1.0`

## Overview
A project-independent, model-agnostic autonomous AI game developer extension for Unity. It connects any Unity project to the Autonomous Unity Agent Runtime via WebSocket Protocol v1.0.

## Installation

### Method 1: Unity Package Manager (via Manifest)
Add to your project's `Packages/manifest.json`:
```json
{
  "dependencies": {
    "com.unityagent.autonomous-ai-agent": "file:../../unity-agent-extension/com.unityagent.autonomous-ai-agent"
  }
}
```

### Method 2: Unity Editor UPM UI
1. Open Unity.
2. In the menu, go to **Window** → **Package Manager**.
3. Click the **+** button in the top left and select **Add package from disk...**.
4. Select the `package.json` file inside `com.unityagent.autonomous-ai-agent`.

## Features
- **Project Discovery**: Automatically detects Unity version, project root, assets directory, compilation state, and build target.
- **Protocol v1.0 Negotiation**: Automatic handshake and capability verification with the Agent Runtime.
- **Model Agnostic**: Works with OpenAI, OpenAI-compatible (NVIDIA NIM, Groq, Together, Ollama, vLLM), or custom endpoints.
- **Autonomous Recovery**: Supports compile error diagnosis, code repair, and runtime test validation.
- **Script Sandbox**: Validates paths and blocks prohibited unsafe APIs before code enters the asset database.
- **Editor Chat & Settings**: Interactive Editor windows under `Window -> Autonomous AI`.
