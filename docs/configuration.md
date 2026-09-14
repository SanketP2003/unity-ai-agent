# Configuration Guide — Profiles & Environment Variables

Autonomous Game Studio strictly separates configuration from code and secret keys, adhering to 12-factor application standards and the Zero-Secret Invariant.

## Configuration Hierarchy

Configuration resolution follows this strict precedence order:
1. Environment Variables (`AGENT_PROVIDER_API_KEY`, `SERVER_PORT`, etc.)
2. Active Profile overrides (e.g. `application-production.properties`)
3. Base application properties (`application.properties`)
4. In-memory runtime session overrides (per-session API keys, never saved to disk)

---

## Core Application Properties (`application.properties`)

```properties
# Server
server.port=8080

# SQLite Database
spring.datasource.url=jdbc:sqlite:autonomous_studio.db
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=update

# AI Provider Defaults
agent.provider.default=nvidia
agent.provider.base-url=https://integrate.api.nvidia.com/v1
agent.provider.model=meta/llama-3.3-70b-instruct
agent.provider.timeout-seconds=60
agent.provider.max-retries=3

# Unity WebSocket Bridge
unity.bridge.port=8080
unity.bridge.path=/ws/unity
unity.bridge.heartbeat-interval-ms=5000

# Security & Circuit Breakers
agent.safety.ast-validation=true
agent.safety.zero-secret-enforcement=true
circuit-breaker.failure-threshold=5
circuit-breaker.recovery-time-seconds=30
```

---

## Environment Profiles

The studio provides three standard lifecycle environments:

### 1. `development` (Default)
- Verbose logging enabled (`logging.level.com.unityagent=DEBUG`).
- Hot script compilation and dynamic reloading.
- Diagnostic auto-repair enabled.
- Relaxation of human review requirement for ephemeral local test builds.

### 2. `staging`
- Simulates release environment.
- Strict `CompletionGate` verification.
- Release candidate generation with SHA-256 verification.
- Tamper detection validation.

### 3. `production`
- Strict Human Approval Required: No release can be published without human review.
- Mandatory SHA-256 binary fingerprinting.
- Full immutable release archiving.
- Audited zero-secret guarantees.

---

## Environment Variables Reference

| Variable | Description | Default |
| :--- | :--- | :--- |
| `SERVER_PORT` | HTTP & WebSocket listening port | `8080` |
| `AGENT_PROVIDER_API_KEY` | Provider API Key (Injected into memory, never written to disk) | *(None)* |
| `AGENT_PROVIDER_BASE_URL` | Provider endpoint URL | `https://integrate.api.nvidia.com/v1` |
| `AGENT_PROVIDER_MODEL` | Default LLM model identifier | `meta/llama-3.3-70b-instruct` |
| `STUDIO_WORKSPACE_DIR` | Base directory for project storage and builds | `./workspace` |
| `STUDIO_BACKUP_DIR` | Base directory for encrypted/clean backups | `./backups` |
