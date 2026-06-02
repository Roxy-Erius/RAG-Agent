package com.ragagent.service;

import com.ragagent.model.Product;
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

    // Category keywords mapping (Chinese keyword -> category)
    private static final Map<String, String> CATEGORY_KEYWORDS = Map.ofEntries(
        Map.entry("洁面", "美妆护肤"), Map.entry("洗面奶", "美妆护肤"), Map.entry("面霜", "美妆护肤"),
        Map.entry("精华", "美妆护肤"), Map.entry("爽肤水", "美妆护肤"), Map.entry("眼霜", "美妆护肤"),
        Map.entry("护肤", "美妆护肤"), Map.entry("美妆", "美妆护肤"), Map.entry("面膜", "美妆护肤"),
        Map.entry("耳机", "数码产品"), Map.entry("手机", "数码产品"), Map.entry("数码", "数码产品"),
        Map.entry("电脑", "数码产品"), Map.entry("平板", "数码产品"), Map.entry("智能", "数码产品"),
        Map.entry("跑鞋", "运动户外"), Map.entry("运动", "运动户外"), Map.entry("T恤", "运动户外"),
        Map.entry("户外", "运动户外"), Map.entry("鞋", "运动户外"), Map.entry("服饰", "运动户外"),
        Map.entry("衣服", "运动户外")
    );

    public RecommendationService(ConversationRepository conversationRepo,
                                  MessageRepository messageRepo,
                                  ProductRepository productRepo) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.productRepo = productRepo;
    }

    /**
     * Analyze user's chat history, find preferred category, return 3 random products.
     * Falls back to random popular products if user has no history.
     */
    public List<Product> recommend(Long userId) {
        // Get all user conversations
        var conversations = conversationRepo.findByUserId(userId);
        if (conversations.isEmpty()) {
            log.debug("无历史会话，返回随机推荐");
            return randomProducts(null);
        }

        // Collect all user messages
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

        // Count category hits
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (String msg : userMessages) {
            for (var entry : CATEGORY_KEYWORDS.entrySet()) {
                if (msg.contains(entry.getKey())) {
                    categoryCounts.merge(entry.getValue(), 1, Integer::sum);
                }
            }
        }

        // Find top category
        String topCategory = categoryCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        log.info("用户偏好分析 | userId={} | topCategory={} | counts={}", userId, topCategory, categoryCounts);
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
        // Random shuffle and pick 3
        Collections.shuffle(all);
        return all.subList(0, Math.min(3, all.size()));
    }
}
