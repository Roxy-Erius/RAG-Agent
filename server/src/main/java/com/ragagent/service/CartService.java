package com.ragagent.service;

import com.ragagent.model.CartItem;
import com.ragagent.repository.CartRepository;
import com.ragagent.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    public CartItem addToCart(String sessionId, String productId, String skuId, String skuLabel, int quantity) {
        // 如果没有 skuId 但有 skuLabel，从 product_skus 表反查 skuId
        if (skuId == null && skuLabel != null && !skuLabel.isBlank()) {
            skuId = resolveSkuId(productId, skuLabel);
            if (skuId != null) {
                log.info("根据 skuLabel 反查 skuId: {} → {}", skuLabel, skuId);
            }
        }
        log.info("加购 | sessionId={} | productId={} | skuId={} | skuLabel={} | quantity={}", sessionId, productId, skuId, skuLabel, quantity);
        return cartRepository.add(sessionId, productId, skuId, skuLabel, quantity);
    }

    /**
     * 根据 skuLabel 从 product_skus 表查找匹配的 skuId。
     * 去除空格后做包含匹配，兼容 "标准版/冰霜银" 和 "标准版 / 冰霜银" 等格式差异。
     */
    private String resolveSkuId(String productId, String skuLabel) {
        try {
            String normalizedInput = skuLabel.replaceAll("\\s+", "").replace("/", "").trim();
            log.info("resolveSkuId | productId={} | skuLabel={} | normalized={}", productId, skuLabel, normalizedInput);
            List<Map<String, Object>> skus = productRepository.findSkusByProductId(productId);
            for (Map<String, Object> sku : skus) {
                String props = sku.get("properties") != null ? sku.get("properties").toString() : "{}";
                String label = parseSkuLabel(props);
                String normalizedLabel = label.replaceAll("\\s+", "").replace("/", "").trim();
                String skuId = sku.get("sku_id") != null ? sku.get("sku_id").toString() : null;
                log.info("  比对 | skuId={} | label={} | normalized={}", skuId, label, normalizedLabel);
                if (normalizedInput.equals(normalizedLabel) || normalizedLabel.contains(normalizedInput) || normalizedInput.contains(normalizedLabel)) {
                    log.info("  匹配成功! skuId={}", skuId);
                    return skuId;
                }
            }
            log.warn("  未找到匹配的 SKU");
        } catch (Exception e) {
            log.warn("反查 skuId 失败: {}", e.getMessage());
        }
        return null;
    }

    private String parseSkuLabel(String propsJson) {
        if (propsJson == null || propsJson.isBlank() || propsJson.equals("{}")) return "标准";
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> props = new com.google.gson.Gson().fromJson(propsJson,
                    new com.google.gson.reflect.TypeToken<Map<String, String>>() {}.getType());
            return String.join(" / ", props.values());
        } catch (Exception e) {
            return propsJson;
        }
    }

    public boolean removeFromCart(Long id, String sessionId) {
        log.info("删除购物车项 | id={} | sessionId={}", id, sessionId);
        return cartRepository.remove(id, sessionId);
    }

    public CartItem updateQuantity(Long id, String sessionId, int quantity) {
        log.info("修改数量 | id={} | sessionId={} | quantity={}", id, sessionId, quantity);
        return cartRepository.updateQuantity(id, sessionId, quantity);
    }

    public List<CartItem> getCart(String sessionId) {
        log.debug("查询购物车 | sessionId={}", sessionId);
        return cartRepository.findBySessionId(sessionId);
    }

    public void migrateCart(String sessionId, Long userId) {
        log.info("购物车迁移 | sessionId={} → userId={}", sessionId, userId);
        cartRepository.migrateSessionToUser(sessionId, userId);
    }

    public CartItem addToCart(String sessionId, Long userId, String productId, String skuId, String skuLabel, int quantity) {
        // 如果没有 skuId 但有 skuLabel，从 product_skus 表反查 skuId
        if (skuId == null && skuLabel != null && !skuLabel.isBlank()) {
            skuId = resolveSkuId(productId, skuLabel);
            if (skuId != null) {
                log.info("根据 skuLabel 反查 skuId: {} → {}", skuLabel, skuId);
            }
        }
        log.info("加购(user) | userId={} | productId={} | skuId={} | skuLabel={} | quantity={}", userId, productId, skuId, skuLabel, quantity);
        return cartRepository.add(sessionId, userId, productId, skuId, skuLabel, quantity);
    }

    public boolean removeFromCart(Long id, Long userId) {
        log.info("删除购物车项(user) | id={} | userId={}", id, userId);
        return cartRepository.removeByUser(id, userId);
    }

    public CartItem updateQuantity(Long id, Long userId, int quantity) {
        log.info("修改数量(user) | id={} | userId={} | quantity={}", id, userId, quantity);
        return cartRepository.updateQuantityByUser(id, userId, quantity);
    }

    public List<CartItem> getCartByUser(Long userId) {
        log.debug("查询购物车(user) | userId={}", userId);
        return cartRepository.findByUserId(userId);
    }

    public void setCartQuantity(String sessionId, String productId, int quantity) {
        log.info("设置购物车数量(session) | sessionId={} | productId={} | quantity={}",
                sessionId, productId, quantity);
        cartRepository.setQuantity(sessionId, productId, quantity);
    }

    public void setCartQuantity(String sessionId, Long userId, String productId, int quantity) {
        log.info("设置购物车数量(user) | userId={} | productId={} | quantity={}",
                userId, productId, quantity);
        cartRepository.setQuantityByUser(userId, productId, quantity);
    }
}
