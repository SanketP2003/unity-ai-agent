# Autonomous Unity AI Agent Extension

**Unity Package Manager (UPM) Package: `com.unityagent.autonomous-ai-agent`**  
Compatible with **Unity 2021.3 LTS, 2022.3 LTS, 2023.x, and Unity 6 (6000.x+)**.

Enables safe, protocol-compliant bidirectional communication between any Unity Editor project and the Autonomous AI Agent backend.

---

## 🚀 How Anyone Can Add This Extension to Any Unity Project

You can install this extension into **any new or existing Unity project** using any of the four methods below:

### Method 1: Git URL in Unity Package Manager (Recommended — No Manual Download Needed)

1. In your Unity project, open the Package Manager:  
   **Window** > **Package Manager**
2. Click the **`+`** (plus) icon in the top-left corner of the window.
3. Select **Add package from git URL...**
4. Paste the following URL:
   ```text
   https://github.com/SanketP2003/unity-ai-agent.git?path=unity-agent-extension/com.unityagent.autonomous-ai-agent
   ```
5. Click **Add**. Unity will automatically download and install the package!

---

### Method 2: Tarball (`.tgz`) Direct Download

1. Download [`com.unityagent.autonomous-ai-agent-1.0.0.tgz`](https://github.com/SanketP2003/unity-ai-agent/raw/main/distribution/com.unityagent.autonomous-ai-agent-1.0.0.tgz) from the repository's `distribution/` folder or GitHub Releases.
2. In Unity, open **Window** > **Package Manager**.
3. Click the **`+`** icon > **Add package from tarball...**
4. Select the downloaded `.tgz` file. Unity installs it instantly with zero external dependencies.

---

### Method 3: Direct `Packages/manifest.json` Dependency

Open your project's `Packages/manifest.json` in a text editor and add the following entry inside `"dependencies"`:

```json
{
  "dependencies": {
    "com.unityagent.autonomous-ai-agent": "https://github.com/SanketP2003/unity-ai-agent.git?path=unity-agent-extension/com.unityagent.autonomous-ai-agent",
    ...
  }
}
```

Save the file and switch back to Unity; it will automatically resolve and import the package.

---

### Method 4: Offline ZIP / Add from Disk

1. Download [`autonomous-ai-agent-extension-v1.0.0.zip`](https://github.com/SanketP2003/unity-ai-agent/raw/main/distribution/autonomous-ai-agent-extension-v1.0.0.zip) and unzip it to any directory.
2. In Unity, open **Window** > **Package Manager**.
3. Click the **`+`** icon > **Add package from disk...**
4. Select `package.json` inside the extracted folder.

---

## 🎮 Using the AI Agent Extension

Once installed in your Unity project:

1. **Open the Agent Chat**:  
   Navigate to **Window** > **Autonomous AI** > **Agent Chat** to talk directly to the agent, prompt game mechanics, and watch it build scenes in real time.
2. **Open the Agent Bridge**:  
   Navigate to **Window** > **Autonomous AI** > **Agent Bridge** (or **Window** > **Autonomous Agent**) to check the WebSocket connection status and tool registry.
3. **Configure Settings & Model Providers**:  
   Navigate to **Window** > **Autonomous AI** > **Settings** to configure runtime host, AI provider (NVIDIA NIM, OpenAI, or custom OpenAI-compatible endpoints), and credentials securely without touching project assets.

---

## 🛡️ Architecture & Security

- **Zero Project Pollution**: AI databases, temporary LLM caches, logs, and artifacts are never stored in your Unity project.
- **Persistent Project Identity**: Stable UUID assigned in `ProjectSettings/AutonomousAgentIdentity.json`.
- **Protocol v1.0 Compliant**: Over 60 native Unity editor and runtime tools, safe scene management, play mode orchestration, and compilation verification.
- **Clean Uninstallation**: Can be safely removed at any time via Unity Package Manager without leaving orphaned files in `Assets/`.
