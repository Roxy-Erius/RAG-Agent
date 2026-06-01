package com.ragagent.controller;

import com.ragagent.model.CartItem;
import com.ragagent.security.JwtAuthFilter;
import com.ragagent.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);
    private final CartService cartService;
    private final JwtAuthFilter jwtAuthFilter;

    public CartController(CartService cartService, JwtAuthFilter jwtAuthFilter) {
        this.cartService = cartService;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @PostMapping("/add")
    public ResponseEntity<CartItem> add(
            @RequestParam String sessionId,
            @RequestParam String productId,
            @RequestParam(defaultValue = "1") int quantity,
            HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        log.info("==> POST /api/cart/add | sessionId={} | userId={} | productId={} | quantity={}",
                sessionId, userId, productId, quantity);
        CartItem item;
        if (userId != null) {
            item = cartService.addToCart(sessionId, userId, productId, quantity);
        } else {
            item = cartService.addToCart(sessionId, productId, quantity);
        }
        return ResponseEntity.ok(item);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @PathVariable Long id,
            @RequestParam String sessionId,
            HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        log.info("==> DELETE /api/cart/{} | sessionId={} | userId={}", id, sessionId, userId);
        boolean removed;
        if (userId != null) {
            removed = cartService.removeFromCart(id, userId);
        } else {
            removed = cartService.removeFromCart(id, sessionId);
        }
        return removed ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<CartItem> updateQuantity(
            @PathVariable Long id,
            @RequestParam String sessionId,
            @RequestParam int quantity,
            HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        log.info("==> PUT /api/cart/{} | sessionId={} | userId={} | quantity={}", id, sessionId, userId, quantity);
        CartItem item;
        if (userId != null) {
            item = cartService.updateQuantity(id, userId, quantity);
        } else {
            item = cartService.updateQuantity(id, sessionId, quantity);
        }
        return item != null ? ResponseEntity.ok(item) : ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<CartItem>> list(@RequestParam String sessionId,
                                                HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        log.info("==> GET /api/cart | sessionId={} | userId={}", sessionId, userId);
        List<CartItem> items;
        if (userId != null) {
            items = cartService.getCartByUser(userId);
        } else {
            items = cartService.getCart(sessionId);
        }
        return ResponseEntity.ok(items);
    }

    /**
     * 直接设置购物车数量（对话驱动加购 set 模式用）
     * POST /api/cart/set?sessionId=xxx&productId=xxx&quantity=3
     */
    @PostMapping("/set")
    public ResponseEntity<Void> setQuantity(
            @RequestParam String sessionId,
            @RequestParam String productId,
            @RequestParam int quantity,
            HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        log.info("==> POST /api/cart/set | sessionId={} | userId={} | productId={} | quantity={}",
                sessionId, userId, productId, quantity);
        if (userId != null) {
            cartService.setCartQuantity(sessionId, userId, productId, quantity);
        } else {
            cartService.setCartQuantity(sessionId, productId, quantity);
        }
        return ResponseEntity.ok().build();
    }
}
