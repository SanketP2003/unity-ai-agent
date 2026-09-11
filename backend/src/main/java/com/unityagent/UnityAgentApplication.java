package com.unityagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Autonomous Unity Agent backend.
 * Provides REST API, WebSocket server, and tool execution infrastructure.
 */
@SpringBootApplication
public class UnityAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnityAgentApplication.class, args);
    }
}
