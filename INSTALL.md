# Installation & Integration Guide: Autonomous Unity AI Agent

This guide describes how **anyone** can download and install the **Autonomous Unity AI Agent** extension into **any Unity project** (personal, team, or production) and connect it to the Autonomous Game Studio backend.

---

## 📋 Compatibility

- **Unity Versions**: Unity 2021.3 LTS, Unity 2022.3 LTS, Unity 2023.x, and Unity 6 (6000.x+).
- **Operating Systems**: Windows 10/11, macOS (Apple Silicon & Intel), Linux (Ubuntu 20.04+).
- **Unity Render Pipelines**: Built-in Render Pipeline, URP (Universal RP), and HDRP.

---

## 📦 How to Install the Extension in Your Unity Project

Choose whichever method fits your workflow:

### Option 1: Unity Package Manager via Git URL (Recommended)
*No files to download manually; Unity pulls the extension directly from GitHub.*

1. Open your Unity project.
2. Go to **Window** > **Package Manager**.
3. In the top-left toolbar, click the **`+`** (Add) button and select **Add package from git URL...**
4. Paste this exact URL:
   ```text
   https://github.com/SanketP2003/unity-ai-agent.git?path=unity-agent-extension/com.unityagent.autonomous-ai-agent
   ```
5. Click **Add**. Unity will resolve dependencies and compile the extension automatically.

---

### Option 2: Direct Tarball (`.tgz`) Package (Offline / Firewall Friendly)
*Ideal for offline setups or developers behind corporate proxies.*

1. Download the latest `.tgz` release package:
   [**com.unityagent.autonomous-ai-agent-1.0.0.tgz**](https://github.com/SanketP2003/unity-ai-agent/raw/main/distribution/com.unityagent.autonomous-ai-agent-1.0.0.tgz)
2. In your Unity Editor, open **Window** > **Package Manager**.
3. Click the **`+`** button and select **Add package from tarball...**
4. Choose the downloaded `.tgz` file.

---

### Option 3: Add to `Packages/manifest.json` (Automated / Team Standard)
*Commit this to your project repository so everyone on your team gets the AI agent automatically.*

1. In your project's root folder, open `Packages/manifest.json`.
2. Add `"com.unityagent.autonomous-ai-agent"` into the `"dependencies"` block:
   ```json
   {
     "dependencies": {
       "com.unityagent.autonomous-ai-agent": "https://github.com/SanketP2003/unity-ai-agent.git?path=unity-agent-extension/com.unityagent.autonomous-ai-agent",
       "com.unity.ugui": "2.0.0"
     }
   }
   ```
3. Return to Unity; it will automatically import the package.

---

### Option 4: Standalone ZIP Archive (Add Package from Disk)
1. Download [**autonomous-ai-agent-extension-v1.0.0.zip**](https://github.com/SanketP2003/unity-ai-agent/raw/main/distribution/autonomous-ai-agent-extension-v1.0.0.zip).
2. Extract the archive to any folder on your computer.
3. In Unity, open **Window** > **Package Manager**.
4. Click **`+`** > **Add package from disk...**
5. Select `package.json` inside the extracted folder.

---

## ⚡ Quick Start: Running the Backend

The extension communicates with the Autonomous Unity Agent backend to generate code, manipulate scenes, and run play-mode verifications.

### 1. Prerequisites
- **Java 21 LTS** or newer
- **Maven 3.8+**

### 2. Run the Backend
From the cloned or downloaded repository:
```bash
cd backend
mvn spring-boot:run
```
The server will start at `http://localhost:8080`.

### 3. Connect from Unity
1. In Unity, open **Window** > **Autonomous AI** > **Agent Bridge** (or **Window** > **Autonomous Agent**).
2. Confirm the status shows **CONNECTED (READY)**.
3. Open **Window** > **Autonomous AI** > **Agent Chat** to prompt the AI agent and begin building your game!

---

## ⚙️ Configuring Custom LLM Models & Providers

You can switch between LLM providers (NVIDIA NIM, OpenAI, Ollama, vLLM, LM Studio) directly in Unity:
1. Open **Window** > **Autonomous AI** > **Settings**.
2. Select your provider or enter custom Base URL and Model name.
3. Enter your API key (if required).  
   *Note: Credentials are sent directly to the local backend memory and are never saved to your Unity `Assets/` or committed to Git.*
