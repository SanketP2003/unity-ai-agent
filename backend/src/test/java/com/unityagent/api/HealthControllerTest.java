package com.unityagent.api;

import com.unityagent.agent.observability.MetricsService;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.unity.UnityConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private UnityConnection unityConnection;
    private AIProvider aiProvider;
    private MetricsService metricsService;
    private HealthController controller;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("health_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();

        unityConnection = Mockito.mock(UnityConnection.class);
        aiProvider = Mockito.mock(AIProvider.class);
        metricsService = new MetricsService();

        when(unityConnection.isReady()).thenReturn(true);
        when(unityConnection.getState()).thenReturn(UnityConnection.ConnectionState.READY);
        when(aiProvider.isConfigured()).thenReturn(true);
        when(aiProvider.getProviderName()).thenReturn("mock-provider");

        controller = new HealthController(db, unityConnection, aiProvider, metricsService);
    }

    @Test
    void testHealthEndpointReturnsComponentBreakdown() {
        ResponseEntity<Map<String, Object>> response = controller.getHealth();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));

        Map<?, ?> components = (Map<?, ?>) body.get("components");
        assertNotNull(components);

        Map<?, ?> dbStatus = (Map<?, ?>) components.get("database");
        assertEquals("UP", dbStatus.get("status"));

        Map<?, ?> unityStatus = (Map<?, ?>) components.get("unity");
        assertEquals("CONNECTED", unityStatus.get("status"));

        Map<?, ?> providerStatus = (Map<?, ?>) components.get("provider");
        assertEquals(true, providerStatus.get("configured"));
        assertEquals(true, providerStatus.get("available"));
    }

    @Test
    void testLivenessAndReadinessProbes() {
        ResponseEntity<Map<String, String>> live = controller.getLiveness();
        assertEquals(HttpStatus.OK, live.getStatusCode());
        assertEquals("UP", live.getBody().get("status"));

        ResponseEntity<Map<String, String>> ready = controller.getReadiness();
        assertEquals(HttpStatus.OK, ready.getStatusCode());
        assertEquals("UP", ready.getBody().get("status"));
    }

    @Test
    void testMetricsEndpoint() {
        metricsService.recordRunStarted();
        metricsService.recordToolExecution(true);
        metricsService.recordToolExecution(false);
        metricsService.recordRunCompleted(true, 5000);

        ResponseEntity<Map<String, Object>> resp = controller.getMetrics();
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        Map<String, Object> metrics = resp.getBody();
        assertNotNull(metrics);
        assertEquals(1L, metrics.get("run_count"));
        assertEquals(1L, metrics.get("successful_runs"));
        assertEquals(2L, metrics.get("tool_calls_total"));
        assertEquals(0.5, (Double) metrics.get("tool_success_rate"), 0.001);
    }
}
