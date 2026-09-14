# Multi-Platform Build Targets Guide

Autonomous Game Studio supports compiling and packaging games across multiple target platforms.

## Supported Target Platforms

| Platform | Target Identifier | Unity BuildTarget | Artifact Output Format |
| :--- | :--- | :--- | :--- |
| **Windows 64-bit** | `windows_x86_64` | `StandaloneWindows64` | `.exe` + `_Data/` directory / `.zip` |
| **macOS (Universal / Apple Silicon)** | `osx_universal` | `StandaloneOSX` | `.app` bundle / `.dmg` / `.zip` |
| **Linux 64-bit** | `linux_x86_64` | `StandaloneLinux64` | `.x86_64` executable / `.tar.gz` |
| **WebGL (HTML5)** | `webgl` | `WebGL` | Directory with `index.html`, `Build/` |
| **Android** | `android` | `Android` | `.apk` or `.aab` |
| **iOS** | `ios` | `iOS` | Xcode Project directory |

---

## Multi-Platform Build Pipeline

```text
CompletionGate Passed
         │
         ▼
 BuildProfile Selection (e.g. windows_x86_64 + osx_universal)
         │
         ▼
 Unity BuildPipeline.BuildPlayer (Invoked via Bridge)
         │
         ▼
 Physical Verification (File exists, size > 0)
         │
         ▼
 SHA-256 Hashing & Registration in ArtifactManager
         │
         ▼
 Multi-Platform Release Candidate Bundle
```

### Configuring Multi-Platform Targets via API
```bash
POST /api/product/builds/trigger
{
  "projectId": "proj_platformer_01",
  "targets": ["windows_x86_64", "osx_universal", "linux_x86_64"],
  "buildOptions": ["Development", "CleanBuildCache"]
}
```
All artifacts are independently hashed, verified, and linked to the release manifest.
