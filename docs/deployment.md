# Production Deployment Guide

Guide for deploying Autonomous Game Studio as a standalone binary or Docker container in staging and production environments.

## Deployment Options

### Option A: Standalone Fat JAR (Standard)

1. Package the production JAR:
   ```bash
   mvn clean package -DskipTests
   ```
2. Set environment variables:
   ```bash
   export SERVER_PORT=8080
   export AGENT_PROVIDER_API_KEY=nvapi-...
   export SPRING_PROFILES_ACTIVE=production
   ```
3. Run as a systemd service or background daemon:
   ```bash
   nohup java -jar target/autonomous-unity-agent-0.1.0.jar > logs/stdout.log 2>&1 &
   ```

---

### Option B: Docker Container

A multi-stage Dockerfile packaging the Java 21 runtime:

```dockerfile
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/autonomous-unity-agent-0.1.0.jar app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=production
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:
```bash
docker build -t unityagent/autonomous-studio:1.0.0 .
docker run -d -p 8080:8080 \
  -e AGENT_PROVIDER_API_KEY="nvapi-..." \
  -v $(pwd)/data:/app/data \
  unityagent/autonomous-studio:1.0.0
```

---

## Health Checks & Monitoring

The studio exposes Spring Boot Actuator endpoints for container orchestrators (Kubernetes / ECS):
- **Liveness Probe**: `GET /actuator/health/liveness`
- **Readiness Probe**: `GET /actuator/health/readiness`
- **Studio Prerequisite Check**: `GET /api/product/setup-status`
