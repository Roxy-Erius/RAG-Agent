package com.ragagent.controller;

import com.ragagent.model.ChatRequest;
import com.ragagent.model.ChatResponse;
import com.ragagent.service.ChatService;
import com.ragagent.service.SessionService;
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

    public ChatController(ChatService chatService, SessionService sessionService) {
        this.chatService = chatService;
        this.sessionService = sessionService;
    }

    /**
     * SSE 流式对话接口
     * GET /api/chat/stream?message=推荐面霜&sessionId=test1
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        log.info("==> SSE 流式对话 | sessionId={} | message=\"{}\"", sessionId, message);
        SseEmitter emitter = new SseEmitter(60000L);
        emitter.onCompletion(() -> log.info("<== SSE 完成 | sessionId={}", sessionId));
        emitter.onTimeout(() -> log.warn("<== SSE 超时 | sessionId={}", sessionId));
        emitter.onError(e -> log.error("<== SSE 异常 | sessionId={} | {}", sessionId, e.getMessage()));
        chatService.chatStream(sessionId, message, emitter);
        return emitter;
    }

    /**
     * 非流式对话接口
     * POST /api/chat  body: {"sessionId":"test1","message":"推荐面霜"}
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("==> 非流式对话 | sessionId={} | message=\"{}\"", request.getSessionId(), request.getMessage());
        String reply = chatService.chat(request.getSessionId(), request.getMessage());
        List<String> productIds = chatService.extractProductIds(reply);
        log.info("<== 非流式对话完成 | sessionId={} | replyLen={} | productIds={}",
                request.getSessionId(), reply.length(), productIds);
        return ResponseEntity.ok(new ChatResponse(request.getSessionId(), reply, productIds));
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
