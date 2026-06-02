package com.ragagent.controller;

import com.ragagent.model.Conversation;
import com.ragagent.model.Message;
import com.ragagent.security.JwtAuthFilter;
import com.ragagent.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
    private final ConversationService conversationService;
    private final JwtAuthFilter jwtAuthFilter;

    public ConversationController(ConversationService conversationService, JwtAuthFilter jwtAuthFilter) {
        this.conversationService = conversationService;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * GET /api/conversations — 获取当前用户的会话列表
     */
    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }
        log.info("==> GET /api/conversations | userId={}", userId);
        List<Conversation> conversations = conversationService.listConversations(userId);
        return ResponseEntity.ok(conversations);
    }

    /**
     * GET /api/conversations/{conversationId}/messages — 获取会话的所有消息
     */
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<?> getMessages(@PathVariable String conversationId,
                                          HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }
        log.info("==> GET /api/conversations/{}/messages | userId={}", conversationId, userId);
        List<Message> messages = conversationService.getMessages(conversationId, userId);
        return ResponseEntity.ok(messages);
    }

    /**
     * DELETE /api/conversations/{conversationId} — 删除会话
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<?> delete(@PathVariable String conversationId,
                                     HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }
        log.info("==> DELETE /api/conversations/{} | userId={}", conversationId, userId);
        boolean ok = conversationService.deleteConversation(conversationId, userId);
        return ok ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.notFound().build();
    }
}
