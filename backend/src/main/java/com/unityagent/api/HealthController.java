package com.unityagent.api;

import com.unityagent.agent.observability.MetricsService;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.provider.openai.OpenAICompatibleProvider;
import com.unityagent.agent.resilience.CircuitBreaker;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.unity.UnityConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health and diagnostics endpoints distinguishing backend liveness, readiness,
 * Unity connection state, and AI provider availability.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final MemoryDatabase db;
    private final UnityConnection unityConnection;
    private final AIProvider aiProvider;
    private final MetricsService metricsService;

    @Autowired
    public HealthController(MemoryDatabase db,
                            @Autowired(required = false) UnityConnection unityConnection,
                            @Autowired(required = false) AIProvider aiProvider,
                            MetricsService metricsService) {
        this.db = db;
        this.unityConnection = unityConnection;
        this.aiProvider = aiProvider;
        this.metricsService = metricsService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> components = new LinkedHashMap<>();

        // 1. Backend
        components.put("backend", Map.of("status", "UP"));

        // 2. Database
        boolean dbHealthy = isDatabaseHealthy();
        components.put("database", Map.of(
                "status", dbHealthy ? "UP" : "DOWN",
                "path", db != null && db.getDatabasePath() != null ? db.getDatabasePath() : "unknown"
        ));

        // 3. Unity connection
        boolean unityConnected = unityConnection != null && unityConnection.isReady();
        components.put("unity", Map.of(
                "status", unityConnected ? "CONNECTED" : "DISCONNECTED",
                "state", unityConnection != null ? unityConnection.getState().name() : "UNAVAILABLE"
        ));

        // 4. Provider: distinct configured vs available
        boolean configured = aiProvider != null && aiProvider.isConfigured();
        boolean available = configured;
        String breakerState = "CLOSED";
        if (aiProvider instanceof OpenAICompatibleProvider oacp) {
            breakerState = oacp.getCircuitBreaker().getState().name();
            if (oacp.getCircuitBreaker().getState() == CircuitBreaker.State.OPEN) {
                available = false;
            }
        }
        components.put("provider", Map.of(
                "configured", configured,
                "available", available,
                "circuitBreaker", breakerState,
                "providerName", aiProvider != null ? aiProvider.getProviderName() : "none"
        ));

        boolean overallUp = dbHealthy;
        response.put("status", overallUp ? "UP" : "DEGRADED");
        response.put("service", "autonomous-unity-agent");
        response.put("version", "0.1.0");
        response.put("components", components);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health/live")
    public ResponseEntity<Map<String, String>> getLiveness() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, String>> getReadiness() {
        if (isDatabaseHealthy()) {
            return ResponseEntity.ok(Map.of("status", "UP"));
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN", "error", "Database connection unhealthy"));
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(metricsService.getMetricsSnapshot());
    }

    private boolean isDatabaseHealthy() {
        if (db == null) return false;
        try (Connection conn = db.getConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
