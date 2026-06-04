package com.ragagent.service;

import com.ragagent.model.CartItem;
import com.ragagent.repository.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private final CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    public CartItem addToCart(String sessionId, String productId, String skuId, String skuLabel, int quantity) {
        log.info("加购 | sessionId={} | productId={} | skuId={} | skuLabel={} | quantity={}", sessionId, productId, skuId, skuLabel, quantity);
        return cartRepository.add(sessionId, productId, skuId, skuLabel, quantity);
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
