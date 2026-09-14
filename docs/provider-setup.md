# AI Provider Setup & Reliability Guide

Autonomous Game Studio interfaces with LLMs via OpenAI-compatible endpoints. The provider subsystem is hardened for high availability, automatic error recovery, and secret scrubbing.

## Supported Providers

### 1. NVIDIA NIM (Recommended)
- **Base URL**: `https://integrate.api.nvidia.com/v1`
- **Recommended Model**: `meta/llama-3.3-70b-instruct` or `deepseek-ai/deepseek-r1`
- **Authentication**: `Bearer nvapi-...`

### 2. OpenAI
- **Base URL**: `https://api.openai.com/v1`
- **Recommended Model**: `gpt-4o` or `gpt-4o-mini`
- **Authentication**: `Bearer sk-...`

### 3. Ollama (Local & Offline)
- **Base URL**: `http://localhost:11434/v1`
- **Recommended Model**: `llama3.3:70b` or `qwen2.5-coder:32b`
- **Authentication**: `Bearer ollama` (any string)

### 4. vLLM / Self-Hosted
- **Base URL**: `http://<your-host>:8000/v1`
- **Recommended Model**: Any loaded model name
- **Authentication**: Custom or unauthenticated

---

## Provider Reliability Architecture

### 1. Error Classification & Handling
The provider layer classifies HTTP responses into actionable semantic states:

- **401 Unauthorized**: Authentication failure. Immediately halts retries, flags provider state as `UNCONFIGURED` or `FAILED`, and prompts user for credential correction. Never leaks raw tokens into exception messages.
- **404 Not Found**: Model or endpoint mismatch. Verifies whether the requested model is deployed.
- **429 Too Many Requests**: Rate limit or quota exhaustion. Applies exponential backoff with jitter up to the configured retry limit.
- **400 Bad Request (Tool Calling Failure)**: Detects tool parsing or schema rejection. Triggers prompt sanitization fallback.
- **500 / 502 / 503 / 504 Server Errors**: Upstream provider degradation. Handled via exponential backoff and circuit breaker state tracking.

### 2. Circuit Breaker Protection
When consecutive errors exceed the threshold (default: 5 errors):
- Circuit trips to `OPEN` state.
- In-flight runs transition to `WAITING_FOR_PROVIDER`.
- System rejects downstream calls without waiting for network timeouts.
- Automatically transitions to `HALF_OPEN` after recovery cooldown (30s) to probe provider health.

### 3. In-Memory Secret Scrubbing
API keys are never stored in databases, loggers, or serialized objects.
Logs automatically mask keys: `nvapi-AbCd... -> [SCRUBBED]`.
Exception messages replace sensitive bearer tokens with `[REDACTED]`.
