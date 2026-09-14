# Diagnostics & Troubleshooting Guide

This guide outlines diagnostic procedures, error code resolutions, and troubleshooting workflows for Autonomous Game Studio.

## Diagnostic Export Bundle

When investigating anomalies or reporting issues, generate a sanitized diagnostics bundle:
```bash
POST /api/product/diagnostics/export
```
The resulting zip bundle contains:
- Host environment metrics (OS, Java version, CPU cores, RAM, free disk).
- Active Unity connection status and ping latency.
- Recent SQLite operational journals and autonomous run event streams.
- Sanitized application logs (all secrets and API keys masked).

---

## Common Issues & Solutions

### 1. Unity Disconnected (`NOT_CONNECTED` or `WAITING_FOR_UNITY`)
- **Symptoms**: Setup Wizard shows "Disconnected"; runs fail to communicate with Unity.
- **Cause**: Unity Editor is not running or the package WebSocket client failed to connect.
- **Solution**:
  1. Verify Unity Editor is open with a project loaded.
  2. Check **Window -> Autonomous Agent -> Status** in Unity.
  3. Verify port `8080` is open and not blocked by firewall software.
  4. If Unity performed a domain reload, wait 3-5 seconds for auto-reconnection.

### 2. AI Provider Authentication Failed (HTTP 401)
- **Symptoms**: Setup Wizard shows `UNCONFIGURED` or `FAILED`; log shows HTTP 401.
- **Cause**: Invalid API key or missing environment variable `AGENT_PROVIDER_API_KEY`.
- **Solution**:
  1. Open Setup Wizard or Settings modal.
  2. Select the provider and re-enter your API key.
  3. Click **Test Provider Connection** to verify in real-time.

### 3. Roslyn Compilation Loop (Repairs Exceeded)
- **Symptoms**: Run status transitions to `FAILED` with compilation error notes.
- **Cause**: Generated C# code references a missing Unity API or obsolete namespace (e.g. `UnityEngine.Experimental`).
- **Solution**:
  1. Inspect the error log in the chat console or diagnostics bundle.
  2. Re-prompt the agent with explicit namespace instructions (e.g. *"Use UnityEngine.InputSystem only"*).
  3. Ensure the project has the required Unity package installed (e.g. Input System or Universal RP).

### 4. Release Corrupted (`CORRUPTED` Status)
- **Symptoms**: Release status marked `CORRUPTED`; cannot publish release.
- **Cause**: The executable file on disk was modified, truncated, or replaced after initial artifact registration.
- **Solution**:
  1. Re-build the game via the studio.
  2. Register the fresh artifact and verify the new SHA-256 checksum.

---

## Log Files Reference

Logs are written to:
- Backend standard out / console.
- `logs/autonomous-studio.log`
- Unity Editor console and `Editor.log`.
All log output automatically masks sensitive bearer tokens and API keys.
