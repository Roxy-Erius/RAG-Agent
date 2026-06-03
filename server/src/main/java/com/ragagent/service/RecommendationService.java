package com.ragagent.service;

import com.ragagent.model.Product;
import com.ragagent.model.ProductSearchResult;
import com.ragagent.repository.ConversationRepository;
import com.ragagent.repository.MessageRepository;
import com.ragagent.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final ProductRepository productRepo;
    private final RetrieverService retrieverService;

    // V1 关键词降级用
    private static final Map<String, String> CATEGORY_KEYWORDS = Map.ofEntries(
        Map.entry("洁面", "美妆护肤"), Map.entry("洗面奶", "美妆护肤"), Map.entry("面霜", "美妆护肤"),
        Map.entry("精华", "美妆护肤"), Map.entry("爽肤水", "美妆护肤"), Map.entry("眼霜", "美妆护肤"),
        Map.entry("护肤", "美妆护肤"), Map.entry("美妆", "美妆护肤"), Map.entry("面膜", "美妆护肤"),
        Map.entry("耳机", "数码电子"), Map.entry("手机", "数码电子"), Map.entry("数码", "数码电子"),
        Map.entry("电脑", "数码电子"), Map.entry("平板", "数码电子"), Map.entry("智能", "数码电子"),
        Map.entry("跑鞋", "服饰运动"), Map.entry("运动", "服饰运动"), Map.entry("T恤", "服饰运动"),
        Map.entry("户外", "服饰运动"), Map.entry("鞋", "服饰运动"), Map.entry("服饰", "服饰运动"),
        Map.entry("衣服", "服饰运动")
    );

    public RecommendationService(ConversationRepository conversationRepo,
                                  MessageRepository messageRepo,
                                  ProductRepository productRepo,
                                  RetrieverService retrieverService) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.productRepo = productRepo;
        this.retrieverService = retrieverService;
    }

    /**
     * V2 语义推荐：将用户历史消息拼接 → Embedding 语义检索 → Top 3。
     * V1 关键词作为降级方案（RetrieverService 不可用时）。
     */
    public List<Product> recommend(Long userId) {
        var conversations = conversationRepo.findByUserId(userId);
        if (conversations.isEmpty()) {
            log.debug("无历史会话，V1 随机推荐");
            return randomProducts(null);
        }

        List<String> userMessages = new ArrayList<>();
        for (var conv : conversations) {
            var messages = messageRepo.findByConversationId(conv.getId());
            for (var msg : messages) {
                if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                    userMessages.add(msg.getContent());
                }
            }
        }

        if (userMessages.isEmpty()) {
            return randomProducts(null);
        }

        // V2: 拼接历史消息 → 语义检索
        try {
            String query = String.join(" ", userMessages);
            log.info("V2 语义推荐 | userId={} | 历史消息数={} | queryLen={}", userId, userMessages.size(), query.length());
            List<ProductSearchResult> results = retrieverService.retrieveProductsByText(query, 3, null);
            if (!results.isEmpty()) {
                List<String> ids = results.stream().map(ProductSearchResult::getProductId).toList();
                log.info("V2 推荐结果 | ids={}", ids);
                return productRepo.findByIds(ids);
            }
        } catch (Exception e) {
            log.warn("V2 语义推荐失败，降级到 V1: {}", e.getMessage());
        }

        // V1 降级
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (String msg : userMessages) {
            for (var entry : CATEGORY_KEYWORDS.entrySet()) {
                if (msg.contains(entry.getKey())) {
                    categoryCounts.merge(entry.getValue(), 1, Integer::sum);
                }
            }
        }
        String topCategory = categoryCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        log.info("V1 降级推荐 | userId={} | topCategory={} | counts={}", userId, topCategory, categoryCounts);
        return randomProducts(topCategory);
    }

    private List<Product> randomProducts(String category) {
        List<Product> all = productRepo.findAll();
        if (category != null) {
            all = all.stream()
                    .filter(p -> category.equals(p.getCategory()))
                    .collect(Collectors.toList());
        }
        if (all.size() <= 3) return all;
        Collections.shuffle(all);
        return all.subList(0, Math.min(3, all.size()));
    }
}
