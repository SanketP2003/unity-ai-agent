package com.unityagent.agent.memory;

import com.unityagent.agent.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory conversation memory.
 * Stores conversation history per sessionId.
 * Designed to be backed or replaced by persistent storage in future phases.
 */
@Component
public class ConversationMemory {

    private final Map<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();

    public List<ChatMessage> getHistory(String sessionId) {
        if (sessionId == null) return List.of();
        List<ChatMessage> list = sessions.get(sessionId);
        if (list == null) return List.of();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    public void addMessage(String sessionId, ChatMessage message) {
        if (sessionId == null || message == null) return;
        sessions.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(message);
    }

    public void addMessages(String sessionId, List<ChatMessage> messages) {
        if (sessionId == null || messages == null) return;
        List<ChatMessage> list = sessions.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) {
            list.addAll(messages);
        }
    }

    public void clear(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public boolean hasSession(String sessionId) {
        return sessionId != null && sessions.containsKey(sessionId);
    }

    public int sessionCount() {
        return sessions.size();
    }
}
