# Autonomous Unity Game Builder

An AI-powered system that autonomously builds Unity games from natural-language descriptions.

## Architecture

```
User → Game Description → Java AI Agent → WebSocket → Unity C# Bridge → Unity Editor
```

## Project Structure

```
autonomous-unity-agent/
├── backend/          # Java Spring Boot backend (AI agent, tools, WebSocket server)
├── unity/            # Unity 6 project (C# bridge, tool handlers, Editor window)
├── docs/             # Documentation
├── scripts/          # Utility scripts
└── README.md
```

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- Unity 6000.4.7f1

### 1. Start the Java Backend
```bash
cd backend
mvn spring-boot:run
```

### 2. Open Unity Project
Open `unity/` in Unity Hub (Unity 6000.4.7f1).

### 3. Connect the Bridge
In Unity Editor: `Window → Autonomous Agent → Connect`

### API Endpoints
- `GET /api/health` — Health check
- `GET /api/status` — Agent and connection status
- `POST /api/tools/execute` — Execute a Unity tool

## Protocol
Communication uses Protocol v1.0 over WebSocket with message types:
HANDSHAKE, HANDSHAKE_ACK, TOOL_REQUEST, TOOL_RESPONSE, EVENT, ERROR, PING, PONG

## License
Proprietary
