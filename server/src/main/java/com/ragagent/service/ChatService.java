package com.ragagent.service;

import com.ragagent.config.RagConfig;
import com.ragagent.model.ProductSearchResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个专业的电商导购助手。你的职责是根据用户需求，从商品库中推荐最合适的商品。

            ## 严格规则
            1. 你只能推荐下方【商品库】中列出的商品，绝对不能推荐不存在的商品
            2. 你不能编造商品的价格、规格、优惠信息等任何参数
            3. 如果商品库中没有匹配的商品，请如实告知用户，不要编造
            4. 回复要简洁专业，适合移动端阅读，控制在200字以内
            5. 当用户需求模糊时，主动提问引导用户细化需求

            ## 推荐技巧
            - 结合商品的实际卖点和用户需求，给出1-2句有说服力的推荐理由
            - 优先推荐与用户需求最匹配的商品，而非简单罗列
            - 如果有多款合适的商品，简要对比它们的关键差异
            - 提及价格时结合性价比做出评价

            ## 输出格式（重要！）
            - 正常回复使用纯文本
            - 推荐商品时必须使用下方准确的商品ID，格式为 [PRODUCT:商品ID]
            - 示例：这款洗面奶非常适合油皮使用 [PRODUCT:p_beauty_001]，价格也很实惠。

            ## 商品库
            %s
            """;

    private static final Pattern PRODUCT_TAG_PATTERN = Pattern.compile("\\[PRODUCT:(\\w+)]");

    private final RagConfig ragConfig;
    private final RetrieverService retrieverService;
    private final OpenAiStreamingChatModel streamingChatModel;
    private final SessionService sessionService;

    public ChatService(RagConfig ragConfig,
                       RetrieverService retrieverService,
                       OpenAiStreamingChatModel streamingChatModel,
                       SessionService sessionService) {
        this.ragConfig = ragConfig;
        this.retrieverService = retrieverService;
        this.streamingChatModel = streamingChatModel;
        this.sessionService = sessionService;
    }

    /**
     * RAG 流式对话核心方法
     */
    public void chatStream(String sessionId, String userMessage, SseEmitter emitter) {
        try {
            // ① Retrieval: 检索相关商品（带完整信息 + score）
            List<ProductSearchResult> products = retrieverService.retrieveProductsByText(userMessage, ragConfig.getTopK(), null);
            log.info("检索到 {} 条商品，sessionId={}", products.size(), sessionId);

            // ② Augmentation: 拼装上下文
            String context = formatProducts(products);
            String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, context);

            // ③ 构建消息列表: [system, history..., user]
            List<ChatMessage> messages = buildMessages(sessionId, systemPrompt, userMessage);

            // 预计算合法 Product ID 集合（供防幻觉校验）
            Set<String> validIds = products.stream()
                    .map(ProductSearchResult::getProductId)
                    .collect(java.util.stream.Collectors.toSet());

            // ④ Generation: 调用 LLM 流式生成
            StringBuilder fullResponse = new StringBuilder();
            streamingChatModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    try {
                        fullResponse.append(token);
                        emitter.send(SseEmitter.event().data(token));
                    } catch (Exception e) {
                        log.warn("SSE 发送失败: {}", e.getMessage());
                    }
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    try {
                        String reply = sanitizeProductTags(fullResponse.toString(), validIds);
                        sessionService.addUserMessage(sessionId, userMessage);
                        sessionService.addAiMessage(sessionId, reply);
                        log.info("对话完成，sessionId={}, 回复长度={}", sessionId, reply.length());
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("完成回调异常: {}", e.getMessage());
                        emitter.complete();
                    }
                }

                @Override
                public void onError(Throwable error) {
                    log.error("LLM 调用失败: {}", error.getMessage());
                    emitter.completeWithError(error);
                }
            });

        } catch (Exception e) {
            log.error("chatStream 异常: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        }
    }

    /**
     * 非流式对话（备用）
     */
    public String chat(String sessionId, String userMessage) {
        List<ProductSearchResult> products = retrieverService.retrieveProductsByText(userMessage, ragConfig.getTopK(), null);
        String context = formatProducts(products);
        String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, context);
        List<ChatMessage> messages = buildMessages(sessionId, systemPrompt, userMessage);

        Set<String> validIds = products.stream()
                .map(ProductSearchResult::getProductId)
                .collect(java.util.stream.Collectors.toSet());

        StringBuilder fullResponse = new StringBuilder();
        java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();

        streamingChatModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                fullResponse.append(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                String reply = sanitizeProductTags(fullResponse.toString(), validIds);
                sessionService.addUserMessage(sessionId, userMessage);
                sessionService.addAiMessage(sessionId, reply);
                future.complete(reply);
            }

            @Override
            public void onError(Throwable error) {
                log.error("LLM 调用失败: {}", error.getMessage());
                future.completeExceptionally(error);
            }
        });

        try {
            return future.get();  // 阻塞等待完成
        } catch (Exception e) {
            log.error("非流式对话异常: {}", e.getMessage());
            return "抱歉，处理您的请求时出现错误，请稍后重试。";
        }
    }

    /**
     * 从 LLM 回复中提取推荐的商品 ID
     */
    public List<String> extractProductIds(String reply) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = PRODUCT_TAG_PATTERN.matcher(reply);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    /**
     * 防幻觉后处理：移除 LLM 编造的不存在的 [PRODUCT:id] 标签
     */
    private String sanitizeProductTags(String reply, Set<String> validProductIds) {
        Matcher matcher = PRODUCT_TAG_PATTERN.matcher(reply);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String id = matcher.group(1);
            if (validProductIds.contains(id)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            } else {
                log.warn("检测到幻觉 Product ID: {}，已从回复中移除", id);
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private List<ChatMessage> buildMessages(String sessionId, String systemPrompt, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(sessionService.getHistory(sessionId));
        messages.add(UserMessage.from(userMessage));
        return messages;
    }

    private String formatProducts(List<ProductSearchResult> products) {
        if (products.isEmpty()) {
            return "（暂无匹配商品）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < products.size(); i++) {
            ProductSearchResult p = products.get(i);
            sb.append(String.format("%d. [ID:%s] %s | %s | %s | %.0f元\n   %s\n",
                    i + 1,
                    p.getProductId(),
                    p.getTitle(),
                    p.getBrand(),
                    p.getCategory(),
                    p.getBasePrice(),
                    truncate(p.getMarketingDescription(), 100)));
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
