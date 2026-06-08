package com.ragagent.service;

import com.ragagent.config.RagConfig;
import com.ragagent.model.Conversation;
import com.ragagent.model.ProductSearchResult;
import com.ragagent.repository.ConversationRepository;
import com.ragagent.repository.MessageRepository;
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
            %s
            你是一个专业的电商导购助手。你的职责是根据用户需求，从商品库中推荐最合适的商品。

            ## 决策流程（先判断再行动，严格执行）
            核心原则：用户只说了**一个维度**（如仅品类）时，必须追问；只有**两个及以上维度**时才能直接推荐。

            需要追问的情况（只有一个维度或无目标）：
            - "推荐鞋子" → 只有品类，追问
            - "推荐耳机" → 只有品类，追问
            - "推荐跑鞋" → 只有子品类，追问
            - "推荐护肤品" → 只有大品类，追问
            - "我想买东西" → 无目标，追问
            - "有什么好的" → 无目标，追问

            可以直接推荐的情况（有两个及以上维度）：
            - "200元的蓝牙耳机" → 品类+价位
            - "油皮用的洗面奶" → 品类+肤质
            - "推荐一双Nike跑鞋" → 品类+品牌
            - "敏感肌保湿面霜" → 品类+肤质+功效

            追问时从以下方向引导用户（选2-3个）：价位预算、品牌偏好、使用场景/肤质、具体子品类

            ## 严格规则
            1. 你只能推荐下方【商品库】中列出的商品，绝对不能推荐不存在的商品
            2. 你不能编造商品的价格、规格、优惠信息等任何参数
            3. 如果商品库中没有匹配的商品，请如实告知用户，不要编造
            4. 回复要简洁专业，适合移动端阅读，控制在200字以内
            5. 用户只说了单一品类时，绝对不能直接推荐商品，必须先追问

            ## 追问示例
            用户："推荐跑鞋"
            ✅ 正确："好的~请问您有什么品牌偏好呢？预算大概多少？平时跑步的频率和路面类型是怎样的？"
            ❌ 错误：直接推荐 [PRODUCT:p_xxx]...

            用户："推荐耳机"
            ✅ 正确："请问您是想要蓝牙耳机还是有线耳机呢？主要用来听歌还是打游戏？预算大概多少？"
            ❌ 错误：直接推荐 [PRODUCT:p_xxx]..."

            ## 推荐技巧
            - 结合商品的实际卖点和用户需求，给出1-2句有说服力的推荐理由
            - 优先推荐与用户需求最匹配的商品，而非简单罗列
            - 如果有多款合适的商品，简要对比它们的关键差异
            - 提及价格时结合性价比做出评价

            ## 输出格式（重要！）
            - 正常回复使用纯文本
            - 推荐商品时必须使用下方准确的商品ID，格式为 [PRODUCT:商品ID]
            - 示例：这款洗面奶非常适合油皮使用 [PRODUCT:p_beauty_001]，价格也很实惠。

            ## 加购指令（重要！）
            当用户表达了"加入购物车"、"加购"、"帮我加这个"、"买这个"等意图时：
            1. 从对话历史中找到最近推荐的商品（上文出现过的 [PRODUCT:id]）
            2. 加购格式（数量前加 + 表示增量，不加表示设为总量）：
               - 默认 1 件：[ADD_TO_CART:商品ID]
               - 设为总数 3 件：[ADD_TO_CART:商品ID:3]
               - 再增加 2 件：[ADD_TO_CART:商品ID:+2]
            3. 语义判断：
               - "加入购物车"、"加购" → [ADD_TO_CART:p_001]（默认+1）
               - "要三台"、"买 5 个" → [ADD_TO_CART:p_001:3]（设总量为3）
               - "再来两台"、"再加 2 个" → [ADD_TO_CART:p_001:+2]（增量+2）
            4. 如果上文没有推荐过商品，回复："请问您想把哪款商品加入购物车呢？"
            5. 如果上文推荐了多款商品，且用户没有明确指定要哪一款（如"加入购物车"），必须询问用户想要哪一款并列出候选商品名称，绝不能自行猜测默认选择
            6. 用户明确指定了（如"加第二个"、"买第一个"）→ 直接执行加购

            ## 用户购物车
            %s

            ## 购物车操作
            - 用户问"购物车有什么"→ 直接列出上方购物车中的商品
            - 用户要求删除购物车某商品 → 输出 [DELETE_FROM_CART:购物车项ID]
              示例："好的，已删除 [DELETE_FROM_CART:5]"
            - 用户要求清空购物车 → 输出 [CLEAR_CART]
              示例："好的，已清空购物车 [CLEAR_CART]"

            ## 商品库
            %s
            """;

    private static final Pattern PRODUCT_TAG_PATTERN = Pattern.compile("\\[PRODUCT:(\\w+)]");
    private static final Pattern ADD_TO_CART_PATTERN = Pattern.compile("\\[ADD_TO_CART:(\\w+)(?::(\\d+))?]");

    // SSE 缓冲：累积 LLM token，检测完整 [PRODUCT:id] 后才分类发送
    private final StringBuilder sseBuffer = new StringBuilder();

    // 中文口语噪音词（按长度降序，优先匹配长短语）
    private static final String[] NOISE_WORDS = {
            "能不能", "可不可以", "帮我", "你帮", "麻烦",
            "一下", "一些", "给我", "我想", "我要", "我需要",
            "请问", "请", "你好", "嗯", "呢", "啊", "吧", "嘛"
    };
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[。？！，、.?!,;；\\s]+");

    private final RagConfig ragConfig;
    private final RetrieverService retrieverService;
    private final OpenAiStreamingChatModel streamingChatModel;
    private final SessionService sessionService;
    private final ConversationService conversationService;
    private final CartService cartService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepo;

    public ChatService(RagConfig ragConfig,
                       RetrieverService retrieverService,
                       OpenAiStreamingChatModel streamingChatModel,
                       SessionService sessionService,
                       ConversationService conversationService,
                       CartService cartService,
                       ConversationRepository conversationRepository,
                       MessageRepository messageRepo) {
        this.ragConfig = ragConfig;
        this.retrieverService = retrieverService;
        this.streamingChatModel = streamingChatModel;
        this.sessionService = sessionService;
        this.conversationService = conversationService;
        this.cartService = cartService;
        this.conversationRepository = conversationRepository;
        this.messageRepo = messageRepo;
    }

    /**
     * RAG 流式对话核心方法
     * @param userId 登录用户ID，null 表示匿名用户
     * @param conversationId 会话ID（UUID），null 则自动生成
     */
    public void chatStream(String sessionId, String conversationId, Long userId,
                           String userMessage, SseEmitter emitter) {
        try {
            // ① Retrieval: 检索相关商品（带完整信息 + score）
            //    先去噪音词，再多轮增强，避免口语化词汇干扰 embedding
            log.info("┌─ RAG 流式对话开始 | sessionId={}", sessionId);
            String cleaned = preprocessQuery(userMessage);
            String retrievalQuery = augmentQuery(sessionId, cleaned);
            log.info("│ 预处理: \"{}\" → \"{}\"", userMessage, retrievalQuery);
            List<ProductSearchResult> products = retrieverService.retrieveProductsByText(retrievalQuery, ragConfig.getTopK(), null);
            log.info("│ 检索结果: {} 条 | ids={}",
                    products.size(),
                    products.stream().map(ProductSearchResult::getProductId).toList());

            // ② Augmentation: 拼装上下文
            String context = formatProducts(products);
            String cartInfo = formatCart(sessionId, userId);
            String userProfile = formatUserProfile(userId);
            String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, userProfile, cartInfo, context);

            // ③ 构建消息列表: [system, history..., user]
            List<ChatMessage> messages = buildMessages(sessionId, systemPrompt, userMessage);

            // 预计算合法 Product ID 集合（供防幻觉校验）
            Set<String> validIds = products.stream()
                    .map(ProductSearchResult::getProductId)
                    .collect(java.util.stream.Collectors.toSet());

            // ④ Generation: 调用 LLM 流式生成
            log.info("│ 调用 LLM...");
            long startTime = System.currentTimeMillis();
            StringBuilder fullResponse = new StringBuilder();
            streamingChatModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    try {
                        fullResponse.append(token);
                        sseBuffer.append(token);
                        flushSseBuffer(emitter);
                    } catch (Exception e) {
                        log.warn("SSE 发送失败: {}", e.getMessage());
                    }
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    try {
                        // 刷出缓冲区剩余文本
                        emitTokens(emitter, sseBuffer.toString());
                        sseBuffer.setLength(0);

                        String reply = sanitizeProductTags(fullResponse.toString(), validIds);
                        sessionService.addUserMessage(sessionId, userMessage);
                        sessionService.addAiMessage(sessionId, reply);

                        // 持久化到 MySQL（仅登录用户）
                        String effectiveCid = conversationId;
                        if (userId != null) {
                            java.util.List<String> productIds = extractProductIds(reply);
                            Conversation conv = conversationService.getOrCreate(userId, conversationId, userMessage);
                            effectiveCid = conv.getConversationId();
                            conversationService.saveMessage(conv.getId(), "user", userMessage, null);
                            conversationService.saveMessage(conv.getId(), "ai", reply, productIds);
                            log.debug("持久化消息 | conversationId={} | role=user,ai", effectiveCid);
                        }

                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("└─ 对话完成 | sessionId={} | conversationId={} | 回复长度={} | 耗时={}ms",
                                sessionId, effectiveCid, reply.length(), elapsed);
                        emitter.send(SseEmitter.event().data(
                                "{\"type\":\"done\",\"conversationId\":\"" +
                                (effectiveCid != null ? effectiveCid : "") + "\"}"));
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
    public String chat(String sessionId, String conversationId, Long userId, String userMessage) {
        String cleaned = preprocessQuery(userMessage);
        String retrievalQuery = augmentQuery(sessionId, cleaned);
        List<ProductSearchResult> products = retrieverService.retrieveProductsByText(retrievalQuery, ragConfig.getTopK(), null);
        String context = formatProducts(products);
        String cartInfo = formatCart(sessionId, userId);
        String userProfile = formatUserProfile(userId);
        String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, userProfile, cartInfo, context);
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

                // 持久化到 MySQL（仅登录用户）
                if (userId != null) {
                    java.util.List<String> productIds = extractProductIds(reply);
                    Conversation conv = conversationService.getOrCreate(userId, conversationId, userMessage);
                    conversationService.saveMessage(conv.getId(), "user", userMessage, null);
                    conversationService.saveMessage(conv.getId(), "ai", reply, productIds);
                }

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
     * 检索 query 预处理：去除口语噪音词和标点，让 embedding 更聚焦于核心语义。
     *
     * 示例：
     *   "帮我推荐一下保湿面霜。" → "推荐保湿面霜"
     *   "你好，请问有没有蓝牙耳机啊？" → "蓝牙耳机"
     */
    private String preprocessQuery(String query) {
        String result = query;
        // 去除首尾标点和空白
        result = PUNCTUATION_PATTERN.matcher(result).replaceAll(" ").trim();
        // 按长度降序逐个替换噪音词（避免短词先匹配截断长词）
        for (String noise : NOISE_WORDS) {
            result = result.replace(noise, " ");
        }
        // 合并多余空格并 trim
        result = result.replaceAll("\\s+", " ").trim();
        if (!result.equals(query)) {
            log.debug("query 预处理: '{}' → '{}'", query, result);
        }
        return result;
    }

    /**
     * 多轮对话 query 增强：仅当当前问题是追问/引用类短句时才拼接历史关键词，
     * 避免独立新话题被前文污染（如聊完手机再聊护肤品）。
     */
    private String augmentQuery(String sessionId, String currentQuery) {
        // 独立话题检测：完整话题不拼接历史
        if (!isFollowUp(currentQuery)) {
            log.debug("query 增强跳过（独立话题）: '{}'", currentQuery);
            return currentQuery;
        }
        List<String> recentMsgs = sessionService.getRecentUserMessages(sessionId, 3);
        if (recentMsgs.isEmpty()) {
            return currentQuery;
        }
        String currentCleaned = preprocessQuery(currentQuery);
        List<String> keywords = new ArrayList<>();
        for (String msg : recentMsgs) {
            String cleaned = preprocessQuery(msg);
            if (!cleaned.isEmpty() && !cleaned.equals(currentCleaned)) {
                keywords.add(cleaned);
            }
        }
        if (keywords.isEmpty()) {
            return currentQuery;
        }
        String augmented = String.join(" ", keywords) + " " + currentQuery;
        log.debug("query 增强: '{}' → '{}'", currentQuery, augmented);
        return augmented;
    }

    /** 判断是否为追问/引用类短句（需要上下文增强） */
    private boolean isFollowUp(String query) {
        String q = query.trim();
        // 短句大概率是追问
        if (q.length() <= 6) return true;
        // 含有指代词 → 追问
        if (q.contains("这个") || q.contains("那个") || q.contains("它")
                || q.contains("这") || q.contains("那")) return true;
        // 含有比较/增量词 → 追问
        if (q.contains("更") || q.contains("再") || q.contains("还")
                || q.contains("不要") || q.contains("排除") || q.contains("除了")) return true;
        // 含有"加入购物车"、"加购"等 → 需要上下文知道加哪个
        if (q.contains("购物车") || q.contains("加购") || q.contains("买这个")
                || q.contains("买它") || q.contains("下单")) return true;
        // 其他情况 → 独立话题，不需要增强
        return false;
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

    /** 获取用户购物车内容文本，注入 System Prompt */
    private String formatCart(String sessionId, Long userId) {
        try {
            List<com.ragagent.model.CartItem> items;
            if (userId != null) {
                items = cartService.getCartByUser(userId);
            } else {
                items = cartService.getCart(sessionId);
            }
            if (items == null || items.isEmpty()) {
                return "（购物车为空）";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                com.ragagent.model.CartItem item = items.get(i);
                String title = item.getProductTitle() != null ? item.getProductTitle() : item.getProductId();
                sb.append(String.format("%d. %s x%d (购物车ID:%d)\n",
                        i + 1, title, item.getQuantity(), item.getId()));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取购物车失败: {}", e.getMessage());
            return "（暂时无法获取购物车信息）";
        }
    }

    /** 分析用户历史消息，提取偏好品类，注入 System Prompt */
    private String formatUserProfile(Long userId) {
        if (userId == null) return "";
        try {
            var conversations = conversationRepository.findByUserId(userId);
            if (conversations.isEmpty()) return "";
            List<String> msgs = new ArrayList<>();
            for (var conv : conversations) {
                var messages = messageRepo.findByConversationId(conv.getId());
                for (var msg : messages) {
                    if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                        msgs.add(msg.getContent());
                    }
                }
            }
            if (msgs.isEmpty()) return "";
            // Extract category keywords
            java.util.Set<String> categories = new java.util.HashSet<>();
            for (String msg : msgs) {
                if (msg.contains("洁面") || msg.contains("洗面奶") || msg.contains("面霜")
                    || msg.contains("精华") || msg.contains("护肤") || msg.contains("爽肤水")
                    || msg.contains("眼霜") || msg.contains("面膜")) categories.add("美妆护肤");
                if (msg.contains("耳机") || msg.contains("手机") || msg.contains("数码")
                    || msg.contains("电脑") || msg.contains("平板") || msg.contains("智能")) categories.add("数码电子");
                if (msg.contains("跑鞋") || msg.contains("运动") || msg.contains("T恤")
                    || msg.contains("户外") || msg.contains("服饰") || msg.contains("衣服")) categories.add("服饰运动");
            }
            if (categories.isEmpty()) return "";
            return "## 用户偏好\n该用户之前关注过：" + String.join("、", categories) + "类商品，推荐时可优先考虑这些品类。\n\n";
        } catch (Exception e) {
            log.warn("获取用户偏好失败: {}", e.getMessage());
            return "";
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    // ========== SSE 缓冲发送（技术路线 2.5 节：结构化 JSON） ==========

    /** 匹配 [PRODUCT:id] / [ADD_TO_CART:id:qty] / [DELETE_FROM_CART:id] / [CLEAR_CART] */
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "\\[(PRODUCT|ADD_TO_CART|DELETE_FROM_CART|CLEAR_CART)(?::(\\w+)(?::(\\+?\\d+))?)?]");

    /**
     * 从缓冲区中检测完整的 [PRODUCT:id] / [ADD_TO_CART:id] 标签，分类发送 SSE 事件。
     * 未完成的标签（如 "[PRO"）保留在缓冲区等待后续 token 拼接。
     */
    private void flushSseBuffer(SseEmitter emitter) throws java.io.IOException {
        String buf = sseBuffer.toString();

        // ① 查找完整的标签
        Matcher m = TAG_PATTERN.matcher(buf);
        int lastEnd = 0;
        while (m.find()) {
            String tagType = m.group(1);       // "PRODUCT" or "ADD_TO_CART"
            String id = m.group(2);
            String before = buf.substring(lastEnd, m.start());
            emitTokens(emitter, before);
            if ("ADD_TO_CART".equals(tagType)) {
                String qtyStr = m.group(3);
                int qty;
                String mode;
                if (qtyStr == null) {
                    qty = 1;
                    mode = "add";
                } else if (qtyStr.startsWith("+")) {
                    qty = Integer.parseInt(qtyStr.substring(1));
                    mode = "add";
                } else {
                    qty = Integer.parseInt(qtyStr);
                    mode = "set";
                }
                emitter.send(SseEmitter.event().data(
                        "{\"type\":\"add_to_cart\",\"productId\":\"" + id +
                        "\",\"quantity\":" + qty + ",\"mode\":\"" + mode + "\"}"));
                log.debug("  SSE → add_to_cart: {} {} {}", id, qty, mode);
            } else if ("DELETE_FROM_CART".equals(tagType)) {
                emitter.send(SseEmitter.event().data(
                        "{\"type\":\"delete_from_cart\",\"cartItemId\":" + id + "}"));
                log.debug("  SSE → delete_from_cart: {}", id);
            } else if ("CLEAR_CART".equals(tagType)) {
                emitter.send(SseEmitter.event().data(
                        "{\"type\":\"clear_cart\"}"));
                log.debug("  SSE → clear_cart");
            } else {
                emitter.send(SseEmitter.event().data(
                        "{\"type\":\"product\",\"productId\":\"" + id + "\"}"));
                log.debug("  SSE → product: {}", id);
            }
            lastEnd = m.end();
        }

        String rest = buf.substring(lastEnd);

        // ② 检查 [DONE]
        int doneIdx = rest.indexOf("[DONE]");
        if (doneIdx >= 0) {
            emitTokens(emitter, rest.substring(0, doneIdx));
            emitter.send(SseEmitter.event().data("{\"type\":\"done\"}"));
            sseBuffer.setLength(0);
            return;
        }

        // ③ 保留末尾可能为不完整标签的部分（以 '[' 开头的前缀）
        int bracketIdx = rest.lastIndexOf('[');
        if (bracketIdx >= 0) {
            String possibleTag = rest.substring(bracketIdx);
            if (isIncompleteTag(possibleTag)) {
                emitTokens(emitter, rest.substring(0, bracketIdx));
                sseBuffer.setLength(0);
                sseBuffer.append(possibleTag);
                return;
            }
        }

        // ④ 无风险 → 全量发送
        emitTokens(emitter, rest);
        sseBuffer.setLength(0);
    }

    /**
     * 判断缓冲区末尾的片段是否可能是不完整的标签（需要保留等待后续 token）。
     * 覆盖两种场景：① 极短前缀如 [D, [P → 检查 body 是否为已知关键词的前缀
     *               ② 含参数前缀如 [ADD_TO_CART:p_001: → 正则匹配
     */
    private boolean isIncompleteTag(String s) {
        if (s.isEmpty() || !s.startsWith("[")) return false;
        if (s.equals("[")) return true;  // lone bracket always incomplete
        String body = s.substring(1);  // 去掉 '['

        // ① body 是某个标签关键词的前缀
        if ("DONE".startsWith(body)
                || "PRODUCT:".startsWith(body)
                || "ADD_TO_CART:".startsWith(body)
                || "DELETE_FROM_CART:".startsWith(body)
                || "CLEAR_CART".startsWith(body)) {
            return true;
        }

        // ② body 已包含完整关键词且带参数字段
        return body.matches("(PRODUCT|ADD_TO_CART|DELETE_FROM_CART):\\w*(:\\+?\\d*)?")
                || body.equals("CLEAR_CART");
    }

    /** 发送文本 token 事件 */
    private void emitTokens(SseEmitter emitter, String text) throws java.io.IOException {
        if (text.isEmpty()) return;
        emitter.send(SseEmitter.event().data(
                "{\"type\":\"token\",\"content\":\"" + escapeJson(text) + "\"}"));
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
