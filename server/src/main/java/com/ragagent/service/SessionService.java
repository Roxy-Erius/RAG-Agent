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

    /**
     * 提取最近 N 条用户消息（用于检索 query 增强）
     */
    public List<String> getRecentUserMessages(String sessionId, int maxCount) {
        List<ChatMessage> history = sessions.getOrDefault(sessionId, List.of());
        List<String> userMsgs = new ArrayList<>();
        // 从后往前遍历，取最近的用户消息
        for (int i = history.size() - 1; i >= 0 && userMsgs.size() < maxCount; i--) {
            if (history.get(i) instanceof UserMessage) {
                userMsgs.add(0, ((UserMessage) history.get(i)).singleText());
            }
        }
        return userMsgs;
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
