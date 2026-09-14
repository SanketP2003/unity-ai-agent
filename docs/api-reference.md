# REST API & WebSocket Protocol Reference

Complete specification of HTTP REST endpoints and WebSocket protocols exposed by Autonomous Game Studio.

## 1. Product & Setup APIs

### `GET /api/product/setup-status`
Returns host environment readiness and subsystem states.
- **Response**:
  ```json
  {
    "systemCheck": {
      "satisfied": true,
      "javaVersion": 21,
      "maxMemoryBytes": 2147483648,
      "freeDiskBytes": 53687091200,
      "filesystemWritable": true,
      "sqliteSupported": true
    },
    "providerState": "CONFIGURED",
    "activeProviderId": "nvidia",
    "unityState": "CONNECTED",
    "connectedProjectCount": 1,
    "readinessState": "READY_FOR_AUTONOMOUS_RUN",
    "missingPrerequisites": []
  }
  ```

### `POST /api/product/test-provider`
Validates provider credentials and measures latency.
- **Request**:
  ```json
  {
    "providerName": "nvidia",
    "apiKeyOverride": "nvapi-..."
  }
  ```
- **Response**:
  ```json
  {
    "success": true,
    "statusCode": 200,
    "message": "Provider responded successfully with model meta/llama-3.3-70b-instruct",
    "latencyMs": 342
  }
  ```

### `GET /api/product/security-audit`
Performs an on-demand Zero-Secret audit.
- **Response**:
  ```json
  {
    "clean": true,
    "scannedFiles": 1420,
    "violations": [],
    "categoryStats": { "SCANNED_FILES": 1420, "CONFIG_FILES": 12 },
    "durationMs": 85
  }
  ```

---

## 2. Autonomous Run APIs

### `POST /api/agent/run`
Starts an autonomous game-building run.
- **Request**:
  ```json
  {
    "sessionId": "session_01",
    "prompt": "Build a 2D platformer with collectables and hazard spikes."
  }
  ```
- **Response**:
  ```json
  {
    "runId": "run_a8f92b",
    "status": "PLANNING"
  }
  ```

### `GET /api/agent/events/{sessionId}`
Server-Sent Events (SSE) stream broadcasting real-time agent thoughts, tool invocations, pipeline stages, and compiler messages.

---

## 3. Release & Artifact APIs

### `POST /api/product/releases/create`
Creates a new release candidate.
- **Request**:
  ```json
  {
    "projectId": "proj_01",
    "version": "1.0.0",
    "channel": "STABLE",
    "title": "Initial Release"
  }
  ```

### `POST /api/product/releases/{releaseId}/approve`
Human approval gate. Hard-rejects agent/LLM IDs.
- **Request**:
  ```json
  {
    "reviewerRole": "ADMIN",
    "reviewerId": "lead_architect",
    "notes": "Verified build on Windows and Mac targets. All tests pass."
  }
  ```

### `POST /api/product/releases/{releaseId}/publish`
Publishes an approved release, locking artifacts immutably.

---

## 4. WebSocket Bridge Protocol (`/ws/unity`)

- **Standard Command Message**:
  ```json
  {
    "id": "req_101",
    "command": "compile_scripts",
    "params": {}
  }
  ```
- **Standard Response Message**:
  ```json
  {
    "id": "req_101",
    "success": true,
    "result": { "errorCount": 0, "warningCount": 1 }
  }
  ```
