package com.ragagent.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private final ConcurrentHashMap<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 10;

    public void addUserMessage(String sessionId, String content) {
        List<ChatMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(UserMessage.from(content));
        trimHistory(history);
    }

    public void addAiMessage(String sessionId, String content) {
        List<ChatMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(AiMessage.from(content));
        trimHistory(history);
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return sessions.getOrDefault(sessionId, List.of());
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    private void trimHistory(List<ChatMessage> history) {
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }
}
