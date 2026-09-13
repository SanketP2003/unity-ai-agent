package com.unityagent.agent.provider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring configuration providing the active primary AIProvider bean
 * resolved through AIProviderFactory.
 */
@Configuration
public class AIProviderConfig {

    @Bean
    @Primary
    public AIProvider activeAIProvider(AIProviderFactory factory) {
        return new AIProvider() {
            @Override
            public String getProviderName() {
                return factory.getProvider().getProviderName();
            }

            @Override
            public boolean isConfigured() {
                return factory.getProvider().isConfigured();
            }

            @Override
            public com.unityagent.agent.model.AgentCompletion generate(com.unityagent.agent.model.AgentPrompt prompt) throws AIProviderException {
                return factory.getProvider().generate(prompt);
            }

            @Override
            public ProviderCapabilities getCapabilities() {
                return factory.getProvider().getCapabilities();
            }

            @Override
            public void validateConfiguration() throws AIProviderException {
                factory.getProvider().validateConfiguration();
            }

            @Override
            public String getModelName() {
                return factory.getProvider().getModelName();
            }
        };
    }
}
