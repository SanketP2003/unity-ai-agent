package com.unityagent.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AgentController REST endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("autonomous-unity-agent"))
                .andExpect(jsonPath("$.version").value("0.1.0"));
    }

    @Test
    void statusEndpointReturnsConnectionState() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unityConnection.state").value("DISCONNECTED"))
                .andExpect(jsonPath("$.unityConnection.ready").value(false))
                .andExpect(jsonPath("$.registeredTools").isArray());
    }

    @Test
    void statusEndpointShowsRegisteredTools() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredTools", org.hamcrest.Matchers.hasItem("create_test_cube")))
                .andExpect(jsonPath("$.registeredTools", org.hamcrest.Matchers.hasItem("create_primitive")));
    }

    @Test
    void executeToolWithMissingToolFieldReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/tools/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_TOOL"));
    }

    @Test
    void executeToolWhenUnityNotReadyReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(post("/api/tools/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "tool": "create_test_cube", "parameters": {} }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("UNITY_NOT_READY"));
    }

    @Test
    void executeUnknownToolWhenUnityNotReadyReturnsServiceUnavailable() throws Exception {
        // Even with an unknown tool, Unity readiness is checked first
        mockMvc.perform(post("/api/tools/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "tool": "nonexistent_tool", "parameters": {} }
                                """))
                .andExpect(status().isServiceUnavailable());
    }
}
