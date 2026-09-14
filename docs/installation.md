# Installation Guide — Clean-Install & Host Requirements

This guide describes how to clean-install the Autonomous Game Studio across Windows, macOS, and Linux environments.

## System Requirements

| Component | Minimum Specification | Recommended Specification |
| :--- | :--- | :--- |
| **Operating System** | Windows 10/11 64-bit, macOS 12+ (Apple Silicon/Intel), Ubuntu 22.04 LTS | Windows 11 64-bit or macOS 14+ Sonoma |
| **Java Runtime** | Java 21 LTS (OpenJDK, Eclipse Temurin) | Java 21 LTS (Temurin 21.0.2+) |
| **Memory (RAM)** | 8 GB RAM (512 MB JVM heap) | 16+ GB RAM (2 GB JVM heap) |
| **Storage** | 2 GB free disk space | 20+ GB SSD space (for Unity builds) |
| **Unity Editor** | Unity 2022.3 LTS | Unity 2022.3 LTS or 2023.2 LTS |
| **Database** | SQLite 3 (Bundled via Xerial JDBC) | SQLite 3 WAL Mode |

---

## Clean Installation Process

### 1. Java 21 Installation
Verify your Java installation:
```bash
java -version
```
Output should indicate `version "21"` or higher. If not installed, download from [Adoptium](https://adoptium.net/).

### 2. Unity Editor & Package Installation
1. Ensure Unity Editor is installed via Unity Hub.
2. In your Unity project, open `Packages/manifest.json`.
3. Add the local or git package reference for the Autonomous Agent Bridge:
```json
{
  "dependencies": {
    "com.unityagent.autonomous-ai-agent": "file:../../unity-package"
  }
}
```
4. In Unity, select **Autonomous Agent -> Bridge Settings** and verify the WebSocket port (default: `8080`).

### 3. Backend Service Deployment
Clone and build the backend distribution:
```bash
git clone https://github.com/unityagent/autonomous-unity-agent.git
cd autonomous-unity-agent/backend
mvn clean package -DskipTests
```
The deployable fat JAR is created at:
`backend/target/autonomous-unity-agent-0.1.0.jar`

Run the service:
```bash
java -jar backend/target/autonomous-unity-agent-0.1.0.jar --server.port=8080
```

### 4. Automatic Workspace Initialization
On first boot, the system automatically:
1. Detects host operating system, architecture, and available disk space.
2. Initializes the SQLite schema in `autonomous_studio.db` with WAL mode enabled.
3. Creates default project templates:
   - `2d-platformer`
   - `topdown-combat`
   - `third-person-arena`
4. Initializes default configuration profiles: `development`, `staging`, `production`.
