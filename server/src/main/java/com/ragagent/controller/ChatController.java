package com.ragagent.controller;

import com.ragagent.model.ChatRequest;
import com.ragagent.model.ChatResponse;
import com.ragagent.service.ChatService;
import com.ragagent.service.SessionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

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
        SseEmitter emitter = new SseEmitter(60000L);
        chatService.chatStream(sessionId, message, emitter);
        return emitter;
    }

    /**
     * 非流式对话接口
     * POST /api/chat  body: {"sessionId":"test1","message":"推荐面霜"}
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String reply = chatService.chat(request.getSessionId(), request.getMessage());
        List<String> productIds = chatService.extractProductIds(reply);
        return ResponseEntity.ok(new ChatResponse(request.getSessionId(), reply, productIds));
    }

    /**
     * 清除会话历史
     * DELETE /api/chat/session/test1
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId) {
        sessionService.clearSession(sessionId);
        return ResponseEntity.ok().build();
    }
}
