# Security Model & Zero-Secret Policy

Autonomous Game Studio enforces rigorous security controls across all software layers to prevent secret leakage, remote code execution, and supply-chain attacks.

## The Zero-Secret Invariant

Under no circumstances may plaintext secrets (API keys, tokens, private keys) exist in persistent storage or unauthenticated outputs:

```text
               ┌────────────────────────────────────────────────────────┐
               │              ZERO-SECRET INVARIANT                     │
               ├────────────────────────────────────────────────────────┤
               │ 🚫 NO Plaintext Keys in SQLite DB                      │
               │ 🚫 NO Plaintext Keys in Git Tracking                   │
               │ 🚫 NO Keys in Export Archives (.unitypackage / .zip)   │
               │ 🚫 NO Keys in Log Files or Diagnostics Bundles         │
               │ 🚫 NO Keys in Generated C# Source Code / Unity Assets   │
               │ 🚫 NO Keys in Public / Client-Facing API Responses     │
               └────────────────────────────────────────────────────────┘
```

### 1. In-Memory Key Management
API keys (such as NVIDIA NIM or OpenAI keys) are passed in memory during active runs or configured via environment variables. When stored in configuration profiles, only metadata or encrypted tokens are retained.

### 2. Output Scrubbing
All loggers and exception handlers pass strings through regex-based scrubbing filters:
- Matches for `nvapi-*`, `sk-*`, `Bearer *`, and `BEGIN PRIVATE KEY` are replaced with `[SCRUBBED]`.
- API endpoints returning provider details always mask keys: `nvapi-AbCd... -> nvapi-****...`.

### 3. Security Audit Service
The built-in `SecurityAuditService` scans:
- SQLite databases
- Project directories and code files
- Diagnostics bundles
- Export archives
If any pattern matching an API key or private key is found, the audit reports violations and blocks distribution.

---

## C# Script Safety AST Validator

When the LLM generates C# code for Unity scripts, `ScriptSafetyValidator` analyzes the source code prior to disk writing. The following malicious or high-risk patterns are strictly prohibited:

1. **File System Destruction**: Calls to `File.Delete`, `Directory.Delete`, `File.Move` targeting system directories.
2. **Process Spawning**: Invocations of `System.Diagnostics.Process.Start`.
3. **Raw Network Sockets**: Direct instantiation of `System.Net.Sockets.Socket`, raw TCP/UDP connections.
4. **Reflection Abuse**: Malicious use of `Type.GetType`, `Assembly.Load`, or invoking private runtime methods.
5. **Unsafe Memory Access**: Use of the `unsafe` keyword, raw memory pointers (`*`, `&`), or `Marshal.AllocHGlobal`.

---

## Zip Slip / Path Traversal Defenses

All archive extraction operations (`BackupManager`, `ProjectPackageService`) strictly validate entry paths. Any entry containing `..` or leading slashes that attempts to escape the designated destination directory triggers an immediate `SecurityException` and aborts extraction.
