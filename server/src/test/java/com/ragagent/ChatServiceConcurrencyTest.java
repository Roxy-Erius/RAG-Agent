package com.ragagent;

import com.ragagent.config.RagConfig;
import com.ragagent.repository.ConversationRepository;
import com.ragagent.repository.MessageRepository;
import com.ragagent.repository.ProductRepository;
import com.ragagent.service.CartService;
import com.ragagent.service.ChatService;
import com.ragagent.service.ConversationService;
import com.ragagent.service.RetrieverService;
import com.ragagent.service.SessionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 复现 BUG-001：ChatService 用成员变量 {@code sseBuffer} / {@code currentValidIds}
 * 保存请求级状态，导致并发 SSE 串流。
 *
 * <p>做法：把 LLM 换成可控假流式输出（A 线程吐 "A"、B 线程吐 "B"），
 * 用 CyclicBarrier 让两路同时进入流式回调，最后检查 A 的 SSE 帧里是否混进了 B 的内容。
 *
 * <p>预期：当前（buggy）代码下本测试**失败**；把 sseBuffer/currentValidIds 改成局部变量后**通过**。
 */
class ChatServiceConcurrencyTest {

    /** 捕获 SseEmitter 发出的每一帧内容 */
    static class CapturingEmitter extends SseEmitter {
        final List<String> frames = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void send(SseEventBuilder builder) {
            for (DataWithMediaType d : builder.build()) {
                Object data = d.getData();
                if (data instanceof String s) {
                    frames.add(s);
                } else if (data instanceof byte[] b) {
                    frames.add(new String(b, StandardCharsets.UTF_8));
                }
            }
        }

        @Override
        public void send(Object object) {
            if (object instanceof String s) frames.add(s);
        }

        @Override
        public void complete() { /* no-op */ }

        @Override
        public void completeWithError(Throwable ex) { /* no-op */ }
    }

    private ChatService buildService(OpenAiStreamingChatModel model) {
        RetrieverService retriever = mock(RetrieverService.class);
        when(retriever.retrieveProductsMultiModal(anyString(), anyInt(), any())).thenReturn(List.of());

        SessionService sessionService = mock(SessionService.class);
        when(sessionService.getHistory(anyString())).thenReturn(List.of());

        CartService cartService = mock(CartService.class);
        when(cartService.getCart(anyString())).thenReturn(List.of());

        return new ChatService(
                new RagConfig(),
                retriever,
                model,
                sessionService,
                mock(ConversationService.class),
                cartService,
                mock(ConversationRepository.class),
                mock(MessageRepository.class),
                mock(ProductRepository.class));
    }

    @Test
    void concurrentStreamsShouldNotCrossContaminate() throws Exception {
        OpenAiStreamingChatModel model = mock(OpenAiStreamingChatModel.class);
        ChatService chatService = buildService(model);

        final int TOKEN_LEN = 300;   // 单 token 足够长，拉大 append 的竞态窗口
        final int ITERATIONS = 300;  // 每路 token 数
        final int ROUNDS = 5;        // 多跑几轮提高复现率

        AtomicInteger intercepted = new AtomicInteger();
        boolean contaminated = false;

        for (int round = 0; round < ROUNDS && !contaminated; round++) {
            CapturingEmitter emitterA = new CapturingEmitter();
            CapturingEmitter emitterB = new CapturingEmitter();
            CyclicBarrier barrier = new CyclicBarrier(2);

            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                StreamingResponseHandler<AiMessage> handler = inv.getArgument(1);
                String thread = Thread.currentThread().getName();
                String marker = thread.endsWith("-A") ? "A" : "B";
                String token = marker.repeat(TOKEN_LEN);
                intercepted.incrementAndGet();

                barrier.await(5, TimeUnit.SECONDS);   // 两路同时开始吐
                for (int i = 0; i < ITERATIONS; i++) {
                    handler.onNext(token);
                    Thread.yield();
                }
                handler.onComplete(null);
                return null;
            }).when(model).generate(anyList(), any());

            Thread ta = new Thread(() -> chatService.chatStream("sess-A", null, null, "AAA", emitterA), "req-A");
            Thread tb = new Thread(() -> chatService.chatStream("sess-B", null, null, "BBB", emitterB), "req-B");
            ta.start();
            tb.start();
            ta.join();
            tb.join();

            boolean aHasB = emitterA.frames.stream().anyMatch(f -> f.contains("BBB"));
            boolean bHasA = emitterB.frames.stream().anyMatch(f -> f.contains("AAA"));
            contaminated = aHasB || bHasA;

            System.out.printf("[round %d] A帧数=%d 含B内容=%s | B帧数=%d 含A内容=%s%n",
                    round + 1, emitterA.frames.size(), aHasB, emitterB.frames.size(), bHasA);

            if (contaminated) {
                String sample = emitterA.frames.stream().filter(f -> f.contains("BBB")).findFirst().orElse("");
                System.out.println(">>> 复现！A 收到混入 B 的帧（前 160 字）："
                        + sample.substring(0, Math.min(160, sample.length())));
            }
        }

        System.out.println("LLM mock 被调用次数 = " + intercepted.get());
        assertFalse(contaminated,
                "并发串流：A/B 两个请求的 SSE 内容互相污染 —— sseBuffer 是单例 ChatService 的成员变量（BUG-001）");
    }
}
