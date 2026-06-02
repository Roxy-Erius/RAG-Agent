package com.ragagent.controller;

import com.ragagent.model.CartItem;
import com.ragagent.security.JwtAuthFilter;
import com.ragagent.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final CartService cartService;
    private final com.ragagent.repository.CartRepository cartRepository;
    private final JwtAuthFilter jwtAuthFilter;

    public OrderController(CartService cartService, com.ragagent.repository.CartRepository cartRepository, JwtAuthFilter jwtAuthFilter) {
        this.cartService = cartService;
        this.cartRepository = cartRepository;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * POST /api/orders/checkout
     * Body: {"sessionId":"xxx","itemIds":[1,3]}
     * Only pays for selected items, deletes them from cart.
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String sessionId = (String) body.get("sessionId");
        @SuppressWarnings("unchecked")
        List<Integer> itemIdsRaw = (List<Integer>) body.get("itemIds");
        if (sessionId == null || itemIdsRaw == null || itemIdsRaw.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId and itemIds are required"));
        }
        List<Long> itemIds = itemIdsRaw.stream().map(Long::valueOf).toList();
        Long userId = jwtAuthFilter.getUserId(request);

        log.info("==> POST /api/orders/checkout | sessionId={} | userId={} | itemIds={}", sessionId, userId, itemIds);

        // Get cart items and filter to only the selected ones
        List<CartItem> allItems;
        if (userId != null) {
            allItems = cartService.getCartByUser(userId);
        } else {
            allItems = cartService.getCart(sessionId);
        }

        List<CartItem> selectedItems = allItems.stream()
                .filter(item -> itemIds.contains(item.getId()))
                .toList();

        if (selectedItems.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No matching cart items found"));
        }

        // Calculate total
        double total = selectedItems.stream()
                .mapToDouble(item -> (item.getProductPrice() != null ? item.getProductPrice() : 0.0) * item.getQuantity())
                .sum();

        // Simulate payment (1 second delay)
        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Delete selected items from cart
        int deleted;
        if (userId != null) {
            deleted = cartRepository.deleteByIdsForUser(itemIds, userId);
        } else {
            deleted = cartRepository.deleteByIds(itemIds, sessionId);
        }

        String orderId = UUID.randomUUID().toString().substring(0, 8);
        log.info("<== 支付完成 | orderId={} | total=¥{} | deleted={} items", orderId, total, deleted);

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "total", total,
                "itemCount", selectedItems.size(),
                "status", "paid",
                "paidAt", LocalDateTime.now().toString()
        ));
    }
}
