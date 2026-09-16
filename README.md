# Autonomous Unity AI Agent

[![Unity](https://img.shields.io/badge/Unity-2021.3%20%7C%202022.3%20%7C%202023%20%7C%206-black.svg?logo=unity)](https://unity.com/)
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.md)

An intelligent, project-independent AI copilot and autonomous game builder for the Unity Editor. It converts natural-language instructions into playable scenes, scripts, components, animations, and game mechanics with live perception, error self-correction, and compile safety verification.

---

## 🚀 Adding the Extension to ANY Unity Project

You can install this extension into **any new or existing Unity project** in seconds. No project modifications or messy asset folders required.

### 🌟 Method 1: Install via Unity Package Manager (Git URL) — Recommended
1. Open your Unity project (Unity 2021.3+, 2022.3+, 2023+, or Unity 6).
2. Go to **Window** > **Package Manager**.
3. Click the **`+`** icon in the upper-left and select **Add package from git URL...**
4. Paste this URL and click **Add**:
   ```text
   https://github.com/SanketP2003/unity-ai-agent.git?path=unity-agent-extension/com.unityagent.autonomous-ai-agent
   ```

### 📦 Method 2: Install via Tarball (`.tgz`) Direct Download
1. Download [**`com.unityagent.autonomous-ai-agent-1.0.0.tgz`**](https://github.com/SanketP2003/unity-ai-agent/raw/main/distribution/com.unityagent.autonomous-ai-agent-1.0.0.tgz) from the `distribution/` folder.
2. In Unity, open **Window** > **Package Manager**.
3. Click **`+`** > **Add package from tarball...** and select the `.tgz` file.

### ⚙️ Method 3: Add to `Packages/manifest.json`
Add the following line to your project's `Packages/manifest.json` under `"dependencies"`:
```json
"com.unityagent.autonomous-ai-agent": "https://github.com/SanketP2003/unity-ai-agent.git?path=unity-agent-extension/com.unityagent.autonomous-ai-agent"
```

### 📁 Method 4: Offline ZIP / Add from Disk
1. Download [**`autonomous-ai-agent-extension-v1.0.0.zip`**](https://github.com/SanketP2003/unity-ai-agent/raw/main/distribution/autonomous-ai-agent-extension-v1.0.0.zip).
2. Unzip to any folder, then in Unity: **Window** > **Package Manager** > **`+`** > **Add package from disk...** > select `package.json`.

---

## ⚡ Quick Start

### 1. Prerequisites
- **Java 21 LTS** or newer
- **Maven 3.8+**
- **Unity 2021.3 LTS, 2022.3 LTS, 2023.x, or Unity 6 (6000.x)**

### 2. Start the Backend
Clone the repository and run:
```bash
git clone https://github.com/SanketP2003/unity-ai-agent.git
cd unity-ai-agent/backend
mvn spring-boot:run
```
The server will start at `http://localhost:8080`.

### 3. Open Unity & Build
1. In your Unity project, open **Window** > **Autonomous AI** > **Agent Chat**.
2. Type any prompt (e.g., *"Create a player controller with double jump and particle trail"*).
3. The AI agent will autonomously create assets, configure components, compile, and test in play mode!

---

## 🏗️ Architecture

```text
┌────────────────────────────────────────────────────────┐
│                      Unity Editor                      │
│  Window > Autonomous AI (Chat, Bridge, Settings)       │
│  UPM Package: com.unityagent.autonomous-ai-agent       │
└──────────────────────────┬─────────────────────────────┘
                           │ WebSocket (JSON-RPC Protocol v1.0)
┌──────────────────────────▼─────────────────────────────┐
│                 Spring Boot AI Backend                 │
│  - AgentLoop & Dynamic Planning                        │
│  - 60+ Tool Handlers (Scene, Prefab, UI, Physics, Nav) │
│  - Compilation & Play Mode Safety Gate                 │
│  - Multi-Provider LLM Integration (NVIDIA NIM, OpenAI) │
│  - Project-Isolated SQLite Memory Database             │
└────────────────────────────────────────────────────────┘
```

## 🔒 Key Design Principles
- **Zero Project Pollution**: AI databases, temporary memory caches, logs, and artifacts are never stored in your Unity game assets.
- **Universal Project Compatibility**: Works seamlessly with any Unity project. Stable UUID is saved to `ProjectSettings/AutonomousAgentIdentity.json`.
- **Compile & Play Safety**: Changes are checked with compilation verification and safety rollback gates before runtime execution.
- **Model Agnostic**: Supports NVIDIA NIM, OpenAI GPT-4o, Ollama, vLLM, LM Studio, or any OpenAI-compatible API.

---

## 📖 Documentation
- [Comprehensive Installation Guide (INSTALL.md)](INSTALL.md)
- [UPM Extension Documentation](unity-agent-extension/com.unityagent.autonomous-ai-agent/README.md)
- [Distribution Manifest & Security](distribution/release-manifest.json)
