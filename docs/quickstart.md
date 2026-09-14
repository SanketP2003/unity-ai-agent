# Quickstart Guide — 5-Minute Launch

Get up and running with Autonomous Game Studio in under 5 minutes.

## Prerequisites
- Java 21+ Runtime (OpenJDK 21, Temurin 21, or Oracle JDK 21)
- Unity Editor 2022.3 LTS or 2023.2+
- AI Provider API Key (NVIDIA NIM, OpenAI, or local Ollama/vLLM)

---

## Step 1: Start the Backend Studio
From the repository root:
```bash
cd backend
mvn spring-boot:run
```
Or run the packaged fat JAR:
```bash
java -jar target/autonomous-unity-agent-0.1.0.jar
```
The studio server will start on `http://localhost:8080`.

---

## Step 2: Open the Web Studio
Open your browser and navigate to:
```text
http://localhost:8080
```
Click **🚀 Setup Wizard** in the top navigation bar.

---

## Step 3: Run the First-Run Setup Wizard
1. **System Requirements**: Check that Java 21, memory, free disk, and SQLite JDBC are green.
2. **AI Provider**: Select your provider profile (e.g. *NVIDIA NIM* or *OpenAI*). Enter your API key and click **Test Provider Connection**.
3. **Unity Connection**: Verify that your Unity Editor is running with the `com.unityagent.autonomous-ai-agent` package imported. The badge will flip to **CONNECTED**.
4. **Security Audit**: Click **Run Zero-Secret Audit** to verify zero credentials exist in repository storage.

---

## Step 4: Describe Your Game
Type your game prompt into the natural language console, for example:
> *"Create a 2D retro platformer where a player character collects 5 gems across 3 floating platforms, avoids red spike hazards, and displays a victory UI banner when all gems are collected."*

Press **Send (Enter)**.

---

## Step 5: Watch the Autonomous Pipeline
The studio will automatically execute the 7-stage autonomous pipeline:
1. **PLANNING**: Decomposing mechanics, components, and scene hierarchy.
2. **BUILDING**: Creating GameObjects, 2D colliders, rigidbodies, and C# player controller scripts.
3. **COMPILING**: Verifying C# code against the Unity Roslyn compiler.
4. **DIAGNOSING**: Detecting any syntax or type errors.
5. **REPAIRING**: Automatically patching script errors without human intervention.
6. **TESTING**: Entering Play Mode and running automated physics and input assertions.
7. **VALIDATING**: Authoritative verification by `CompletionGate`.

---

## Step 6: Review & Publish Release
Once validated:
1. Navigate to **Releases & Artifacts** in the sidebar.
2. Verify the generated executable binary, size, and SHA-256 hash.
3. As an authorized reviewer, click **Approve Release**.
4. Click **Publish Release** to finalize an immutable distribution bundle.
