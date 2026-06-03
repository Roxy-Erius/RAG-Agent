package com.ragagent.service;

import com.ragagent.model.Product;
import com.ragagent.model.ProductSearchResult;
import com.ragagent.model.UserBehavior;
import com.ragagent.repository.ConversationRepository;
import com.ragagent.repository.MessageRepository;
import com.ragagent.repository.ProductRepository;
import com.ragagent.repository.UserBehaviorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final ProductRepository productRepo;
    private final RetrieverService retrieverService;
    private final UserBehaviorRepository behaviorRepo;

    // 行为权重和半衰期配置
    private static final Map<String, Integer> BASE_WEIGHTS = Map.of(
        "VIEW", 1,
        "CART", 3,
        "PURCHASE", 5
    );
    private static final Map<String, Integer> HALF_LIFE_DAYS = Map.of(
        "VIEW", 7,
        "CART", 30,
        "PURCHASE", 90
    );

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
                                  RetrieverService retrieverService,
                                  UserBehaviorRepository behaviorRepo) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.productRepo = productRepo;
        this.retrieverService = retrieverService;
        this.behaviorRepo = behaviorRepo;
    }

    /**
     * V3 个性化推荐：V2 语义搜索 + 用户行为信号时间衰减加权。
     * 降级链：V3 → V2 → V1
     */
    public List<Product> recommend(Long userId) {
        var conversations = conversationRepo.findByUserId(userId);
        if (conversations.isEmpty()) {
            log.debug("无历史会话，尝试仅基于行为推荐 | userId={}", userId);
        }

        // 收集用户历史消息
        List<String> userMessages = new ArrayList<>();
        for (var conv : conversations) {
            var messages = messageRepo.findByConversationId(conv.getId());
            for (var msg : messages) {
                if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                    userMessages.add(msg.getContent());
                }
            }
        }

        if (userMessages.isEmpty() && conversations.isEmpty()) {
            return randomProducts(null);
        }

        // V2 基础查询（所有用户历史消息拼接）
        String baseQuery = String.join(" ", userMessages);
        log.info("V2 基础查询 | userId={} | 历史消息数={} | queryLen={}", userId, userMessages.size(), baseQuery.length());

        // V3: 构建带时间衰减行为的加权查询
        try {
            List<UserBehavior> behaviors = behaviorRepo.findByUserId(userId);
            // 收集已购买的商品 ID（用于过滤）
            final Set<String> purchasedIds = behaviors.stream()
                    .filter(b -> "PURCHASE".equals(b.getActionType()))
                    .map(UserBehavior::getProductId)
                    .collect(Collectors.toSet());

            if (!behaviors.isEmpty()) {
                String weightedQuery = buildWeightedQuery(baseQuery, behaviors);
                log.info("V3 加权查询 | userId={} | 行为数={} | 已购数={} | queryLen={}",
                        userId, behaviors.size(), purchasedIds.size(), weightedQuery.length());
                // 多取一些，过滤已购后仍有足够候选
                List<ProductSearchResult> results = retrieverService.retrieveProductsByText(weightedQuery, 6, null);
                if (!results.isEmpty()) {
                    // 过滤掉已购买的商品
                    List<String> ids = results.stream()
                            .map(ProductSearchResult::getProductId)
                            .filter(id -> !purchasedIds.contains(id))
                            .limit(3)
                            .toList();
                    if (!ids.isEmpty()) {
                        log.info("V3 推荐结果（已过滤已购） | ids={}", ids);
                        return productRepo.findByIds(ids);
                    }
                }
                log.info("V3 无结果，降级到 V2");
            } else {
                log.info("无用户行为数据，使用 V2 纯语义推荐");
            }

            // V2: 纯语义推荐（无行为数据或 V3 无结果时）
            List<ProductSearchResult> results = retrieverService.retrieveProductsByText(baseQuery, 6, null);
            if (!results.isEmpty()) {
                List<String> ids = results.stream()
                        .map(ProductSearchResult::getProductId)
                        .filter(id -> !purchasedIds.contains(id))
                        .limit(3)
                        .toList();
                if (!ids.isEmpty()) {
                    log.info("V2 推荐结果（已过滤已购）| ids={}", ids);
                    return productRepo.findByIds(ids);
                }
            }
        } catch (Exception e) {
            log.warn("V3/V2 语义推荐失败，降级到 V1: {}", e.getMessage());
        }

        // V1 降级：关键词匹配
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

    /**
     * 根据用户行为的时间衰减权重构建加权查询字符串。
     * 公式: adjustedWeight = baseWeight × 2^(-daysSinceAction / halfLifeDays)
     * 对每个行为获取商品标题，按权重（四舍五入整数）重复添加到查询中。
     */
    private String buildWeightedQuery(String baseQuery, List<UserBehavior> behaviors) {
        StringBuilder weightedQuery = new StringBuilder(baseQuery);
        LocalDateTime now = LocalDateTime.now();

        for (UserBehavior behavior : behaviors) {
            double adjustedWeight = calculateAdjustedWeight(behavior, now);
            int repeat = (int) Math.round(adjustedWeight);
            if (repeat <= 0) continue;

            String title = getProductTitle(behavior.getProductId());
            if (title == null || title.isEmpty()) continue;

            // 按权重重复添加商品标题到查询
            for (int i = 0; i < repeat; i++) {
                weightedQuery.append(" ").append(title);
            }
        }

        return weightedQuery.toString();
    }

    /**
     * 计算时间衰减后的权重。
     */
    private double calculateAdjustedWeight(UserBehavior behavior, LocalDateTime now) {
        Integer baseWeight = BASE_WEIGHTS.getOrDefault(behavior.getActionType(), 0);
        Integer halfLife = HALF_LIFE_DAYS.getOrDefault(behavior.getActionType(), 7);

        LocalDateTime createdAt = behavior.getCreatedAt();
        if (createdAt == null) {
            return baseWeight;
        }

        long daysSinceAction = ChronoUnit.DAYS.between(createdAt, now);
        if (daysSinceAction < 0) daysSinceAction = 0;

        // adjustedWeight = baseWeight × 2^(-daysSinceAction / halfLifeDays)
        double exponent = -(double) daysSinceAction / halfLife;
        return baseWeight * Math.pow(2, exponent);
    }

    /**
     * 从数据库获取商品标题。
     */
    private String getProductTitle(String productId) {
        try {
            Product product = productRepo.findById(productId);
            return product != null ? product.getTitle() : null;
        } catch (Exception e) {
            return null;
        }
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
