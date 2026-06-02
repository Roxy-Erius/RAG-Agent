package com.ragagent.controller;

import com.ragagent.model.ChatRequest;
import com.ragagent.model.ChatResponse;
import com.ragagent.security.JwtAuthFilter;
import com.ragagent.service.ChatService;
import com.ragagent.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final SessionService sessionService;
    private final JwtAuthFilter jwtAuthFilter;

    public ChatController(ChatService chatService, SessionService sessionService,
                          JwtAuthFilter jwtAuthFilter) {
        this.chatService = chatService;
        this.sessionService = sessionService;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * SSE 流式对话接口
     * GET /api/chat/stream?message=推荐面霜&sessionId=test1&conversationId=xxx
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId,
            @RequestParam(required = false) String conversationId,
            HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        log.info("==> SSE 流式对话 | sessionId={} | conversationId={} | userId={} | message=\"{}\"",
                sessionId, conversationId, userId, message);
        SseEmitter emitter = new SseEmitter(60000L);
        emitter.onCompletion(() -> log.info("<== SSE 完成 | sessionId={}", sessionId));
        emitter.onTimeout(() -> log.warn("<== SSE 超时 | sessionId={}", sessionId));
        emitter.onError(e -> log.error("<== SSE 异常 | sessionId={} | {}", sessionId, e.getMessage()));
        chatService.chatStream(sessionId, conversationId, userId, message, emitter);
        return emitter;
    }

    /**
     * 非流式对话接口
     * POST /api/chat  body: {"sessionId":"test1","message":"推荐面霜","conversationId":"xxx"}
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest chatRequest,
                                              HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        log.info("==> 非流式对话 | sessionId={} | conversationId={} | userId={} | message=\"{}\"",
                chatRequest.getSessionId(), chatRequest.getConversationId(), userId,
                chatRequest.getMessage());
        String reply = chatService.chat(chatRequest.getSessionId(),
                chatRequest.getConversationId(), userId, chatRequest.getMessage());
        List<String> productIds = chatService.extractProductIds(reply);
        log.info("<== 非流式对话完成 | sessionId={} | replyLen={} | productIds={}",
                chatRequest.getSessionId(), reply.length(), productIds);
        return ResponseEntity.ok(new ChatResponse(chatRequest.getSessionId(), reply, productIds));
    }

    /**
     * 清除会话历史
     * DELETE /api/chat/session/test1
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId) {
        log.info("==> 清除会话 | sessionId={}", sessionId);
        sessionService.clearSession(sessionId);
        return ResponseEntity.ok().build();
    }
}
