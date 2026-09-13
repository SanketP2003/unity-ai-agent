package com.unityagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.memory.ConversationMemory;
import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.agent.provider.AIProviderFactory;
import com.unityagent.tools.ToolRegistry;
import com.unityagent.unity.UnityCommandExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real OpenAI-compatible integration test (e.g. Nemotron, OpenRouter, Together, Groq, local endpoint).
 * Conditionally executes ONLY when AI_BASE_URL and AI_API_KEY are configured in the environment.
 */
@SpringBootTest
@DisplayName("Real OpenAI-Compatible Integration Tests (Conditional on AI_BASE_URL & AI_API_KEY)")
class OpenAICompatibleRealIntegrationTest {

    @Autowired
    private AIProviderFactory providerFactory;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private UnityCommandExecutor unityExecutor;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Should run live multi-turn session against OpenAI-compatible endpoint")
    @EnabledIfEnvironmentVariable(named = "AI_BASE_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AI_API_KEY", matches = ".+")
    void testLiveOpenAICompatibleConversationFlow() {
        AIProvider provider = providerFactory.getProvider();
        assertTrue(provider.isConfigured(), "Active AIProvider must be configured when environment variables are set");

        ConversationMemory memory = new ConversationMemory();
        AgentLimits limits = new AgentLimits(30, 5, 3, 30, 300);
        AgentLoop loop = new AgentLoop(provider, toolRegistry, unityExecutor, limits, memory, mapper);

        String sessionId = "sess_compat_" + UUID.randomUUID().toString().substring(0, 8);

        // Test A: Tell me what is currently in the Unity scene.
        System.out.println("--- Running Test A: Perception ---");
        CancellationToken tokenA = new CancellationToken();
        AgentRunResult resultA = loop.run(sessionId, "run_compat_a",
                "Tell me what is currently in the Unity scene.", tokenA);
        assertNotNull(resultA);
        System.out.println("Test A Success: " + resultA.isSuccess());
        System.out.println("Test A Response: " + resultA.getResponse());

        // Test B: Create a green ground plane and three red bouncy spheres above it.
        System.out.println("--- Running Test B: Autonomous Creation ---");
        CancellationToken tokenB = new CancellationToken();
        AgentRunResult resultB = loop.run(sessionId, "run_compat_b",
                "Create a green ground plane and three red bouncy spheres above it.", tokenB);
        assertNotNull(resultB);
        System.out.println("Test B Success: " + resultB.isSuccess());
        System.out.println("Test B Response: " + resultB.getResponse());

        // Test C: Make the ground blue.
        System.out.println("--- Running Test C: Modification ---");
        CancellationToken tokenC = new CancellationToken();
        AgentRunResult resultC = loop.run(sessionId, "run_compat_c",
                "Make the ground blue.", tokenC);
        assertNotNull(resultC);
        System.out.println("Test C Success: " + resultC.isSuccess());
        System.out.println("Test C Response: " + resultC.getResponse());

        // Test D: Describe the current Unity scene.
        System.out.println("--- Running Test D: Final Scene Perception ---");
        CancellationToken tokenD = new CancellationToken();
        AgentRunResult resultD = loop.run(sessionId, "run_compat_d",
                "Describe the current scene and list the objects you created.", tokenD);
        assertNotNull(resultD);
        System.out.println("Test D Success: " + resultD.isSuccess());
        System.out.println("Test D Response: " + resultD.getResponse());
    }
}
