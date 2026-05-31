package com.ragagent.controller;

import com.ragagent.model.CartItem;
import com.ragagent.service.CartService;
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

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/add")
    public ResponseEntity<CartItem> add(
            @RequestParam String sessionId,
            @RequestParam String productId,
            @RequestParam(defaultValue = "1") int quantity) {
        log.info("==> POST /api/cart/add | sessionId={} | productId={} | quantity={}",
                sessionId, productId, quantity);
        CartItem item = cartService.addToCart(sessionId, productId, quantity);
        return ResponseEntity.ok(item);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @PathVariable Long id,
            @RequestParam String sessionId) {
        log.info("==> DELETE /api/cart/{} | sessionId={}", id, sessionId);
        boolean removed = cartService.removeFromCart(id, sessionId);
        return removed ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<CartItem> updateQuantity(
            @PathVariable Long id,
            @RequestParam String sessionId,
            @RequestParam int quantity) {
        log.info("==> PUT /api/cart/{} | sessionId={} | quantity={}", id, sessionId, quantity);
        CartItem item = cartService.updateQuantity(id, sessionId, quantity);
        return item != null ? ResponseEntity.ok(item) : ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<CartItem>> list(@RequestParam String sessionId) {
        log.info("==> GET /api/cart | sessionId={}", sessionId);
        return ResponseEntity.ok(cartService.getCart(sessionId));
    }
}
