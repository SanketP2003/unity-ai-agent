package com.unityagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityagent.agent.memory.ConversationMemory;
import com.unityagent.agent.model.AgentRunResult;
import com.unityagent.agent.provider.openai.OpenAIProvider;
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
 * Real OpenAI integration test.
 * This test only executes when OPENAI_API_KEY is present in the environment.
 * It dynamically selects tools from the real ToolRegistry and communicates
 * via the live bridge if Unity is connected.
 */
@SpringBootTest
@DisplayName("Real OpenAI Integration Tests (Conditional on OPENAI_API_KEY)")
class OpenAIRealIntegrationTest {

    @Autowired
    private OpenAIProvider openAIProvider;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private UnityCommandExecutor unityExecutor;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Should run live OpenAI multi-turn agent loop when API key is provided")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void testLiveOpenAIConversationFlow() {
        assertTrue(openAIProvider.isConfigured(), "OpenAIProvider must be configured when key is present");

        ConversationMemory memory = new ConversationMemory();
        AgentLimits limits = new AgentLimits(30, 5, 3, 30, 300);
        AgentLoop loop = new AgentLoop(openAIProvider, toolRegistry, unityExecutor, limits, memory, mapper);

        String sessionId = "sess_live_" + UUID.randomUUID().toString().substring(0, 8);

        // Turn 1: Create a green ground plane and three red bouncy spheres above it
        CancellationToken token1 = new CancellationToken();
        AgentRunResult result1 = loop.run(sessionId, "run_live_1",
                "Create a green ground plane and three red bouncy spheres above it.", token1);

        assertNotNull(result1);
        System.out.println("Live Turn 1 Response: " + result1.getResponse());

        // Turn 2: Make the ground blue
        CancellationToken token2 = new CancellationToken();
        AgentRunResult result2 = loop.run(sessionId, "run_live_2",
                "Make the ground blue.", token2);

        assertNotNull(result2);
        System.out.println("Live Turn 2 Response: " + result2.getResponse());

        // Turn 3: Tell me what is currently in the Unity scene
        CancellationToken token3 = new CancellationToken();
        AgentRunResult result3 = loop.run(sessionId, "run_live_3",
                "Tell me what is currently in the Unity scene.", token3);

        assertNotNull(result3);
        System.out.println("Live Turn 3 Response: " + result3.getResponse());
    }
}
