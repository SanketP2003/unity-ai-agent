package com.unityagent.config;

import com.unityagent.unity.UnityConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring WebSocket configuration.
 * Registers the UnityConnection handler at the configured endpoint.
 * Uses raw WebSocket (no STOMP) for direct Protocol v1.0 messaging.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UnityConnection unityConnection;

    @Value("${unity.websocket.endpoint:/unity-bridge}")
    private String endpoint;

    public WebSocketConfig(UnityConnection unityConnection) {
        this.unityConnection = unityConnection;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(unityConnection, endpoint)
                .setAllowedOrigins("*");
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean createWebSocketContainer() {
        org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean container =
                new org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean() {
                    @Override
                    public void afterPropertiesSet() {
                        try {
                            super.afterPropertiesSet();
                        } catch (IllegalStateException ex) {
                            // ServerContainer attribute is absent in mock servlet contexts (e.g. MockMvc tests); ignore safely
                            if (ex.getMessage() == null || !ex.getMessage().contains("ServerContainer")) {
                                throw ex;
                            }
                        }
                    }
                };
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        return container;
    }
}

