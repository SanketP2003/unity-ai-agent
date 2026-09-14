# Frequently Asked Questions & Known Limitations

## Frequently Asked Questions (FAQ)

### Q: Can the autonomous agent approve its own releases?
**A: No.** By architectural design and security invariant, LLMs/agents cannot call approval endpoints. The system validates reviewer credentials and rejects any user containing `agent`, `llm`, or `autonomous-builder`. Only human reviewers with `ADMIN` or `REVIEWER` roles can authorize a release.

### Q: What happens if an executable file is modified after release publication?
**A: The tampering attack is detected immediately.** `ArtifactManager` verifies the file's current SHA-256 hash against its recorded registration hash. Any discrepancy transitions the artifact status to `CORRUPTED`, locks all downstream deployment workflows, and sounds security alarms.

### Q: Does the backend store my OpenAI or NVIDIA API key?
**A: No.** Plaintext API keys are never written to SQLite, Git, configuration files, export packages, or logs. Keys are held ephemerally in JVM memory for the duration of the session or read from host environment variables.

### Q: Can I run this completely offline without cloud LLMs?
**A: Yes.** You can connect the studio to a local Ollama instance running `llama3.3:70b` or `qwen2.5-coder:32b` via `http://localhost:11434/v1`.

### Q: What Unity versions are supported?
**A: Unity 2022.3 LTS and Unity 2023.2 LTS** are officially validated. Unity 6 Preview is supported experimentally.

---

## Known Limitations & Best Practices

1. **Unity Domain Reload Latency**: When scripts are modified, Unity unloads and reloads C# AppDomains. This takes between 1.5 to 5 seconds depending on project size. The backend handles this gracefully via `WAITING_FOR_UNITY`, but rapid back-to-back script writes are throttled to ensure compilation stability.
2. **Complex Shader Graph Generation**: The current agent version specializes in standard C# scripts, physics components, UI systems, audio, and materials. Visual Shader Graphs (.shadergraph) must currently be authored manually or pre-bundled in project templates.
3. **Asset Store Packages**: External third-party packages must be imported into the Unity project beforehand. The agent can then reference and instantiate their scripts and prefabs autonomously.
