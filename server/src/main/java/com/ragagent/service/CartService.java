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

    public CartItem addToCart(String sessionId, String productId, int quantity) {
        log.info("加购 | sessionId={} | productId={} | quantity={}", sessionId, productId, quantity);
        return cartRepository.add(sessionId, productId, quantity);
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
}
