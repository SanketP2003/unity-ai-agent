# Performance Tuning & Benchmarks Guide

Autonomous Game Studio is engineered for high throughput, sub-second hierarchy resolution, and minimal memory footprints.

## Performance Benchmarks

The following benchmarks were established across standard test matrices:

| Workload | Object Count | Target Time | Achieved Time | JVM Heap Footprint |
| :--- | :--- | :--- | :--- | :--- |
| **Small Scene** | 50 GameObjects | < 50 ms | ~12 ms | < 5 MB |
| **Medium Scene** | 500 GameObjects | < 200 ms | ~45 ms | < 15 MB |
| **Large Scene** | 5,000 GameObjects | < 500 ms | ~180 ms | < 35 MB |
| **Massive Stress** | 10,000 GameObjects | < 1,000 ms | ~340 ms | < 58 MB |

---

## Performance Optimizations

### 1. Hierarchy Serialization & Streaming
- Uses fast streaming Jackson JSON serialization rather than reflection-heavy tree traversal.
- Components are indexed into flat hash maps to achieve $O(1)$ lookup time for verification assertions.

### 2. SQLite WAL Configuration
- Enabled `PRAGMA journal_mode=WAL;` to allow simultaneous readers without blocking writers.
- Enabled `PRAGMA synchronous=NORMAL;` for optimal disk IO throughput while maintaining crash resilience.
- Configured connection pooling using HikariCP with connection validation timeouts set to 250ms.

### 3. WebSocket Buffer Sizing
- Binary and text message buffer sizes tuned to 16 MB (`spring.websocket.max-text-message-size=16777216`) to support large scene hierarchy payloads without chunk fragmentation.

---

## Recommended JVM Flags for Production

For production environments building complex games:
```bash
java -Xms1g -Xmx4g \
     -XX:+UseG1GC \
     -XX:+ExplicitGCInvokesConcurrent \
     -XX:+HeapDumpOnOutOfMemoryError \
     -jar autonomous-unity-agent-0.1.0.jar
```
