package com.ragagent.controller;

import com.ragagent.model.CartItem;
import com.ragagent.model.Order;
import com.ragagent.repository.OrderRepository;
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
    private final OrderRepository orderRepository;
    private final com.ragagent.service.LogEventService logEventService;

    public OrderController(CartService cartService,
                           com.ragagent.repository.CartRepository cartRepository,
                           JwtAuthFilter jwtAuthFilter,
                           OrderRepository orderRepository,
                           com.ragagent.service.LogEventService logEventService) {
        this.cartService = cartService;
        this.cartRepository = cartRepository;
        this.jwtAuthFilter = jwtAuthFilter;
        this.orderRepository = orderRepository;
        this.logEventService = logEventService;
    }

    /**
     * POST /api/orders/checkout
     * Body: {"sessionId":"xxx","itemIds":[1,3]}
     * Only pays for selected items, deletes them from cart.
     * 结算成功后写入 orders 表。
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

        // === 落库：写入 orders 表 ===
        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString().substring(0, 8));
        order.setUserId(userId);
        order.setSessionId(sessionId);
        order.setTotalAmount(total);
        order.setItemCount(selectedItems.size());
        order.setStatus("paid");
        order.setPaidAt(LocalDateTime.now());
        order.setItems(selectedItems);
        orderRepository.save(order);
        log.info("订单已落库 | orderId={} | total=¥{} | items={}", order.getOrderId(), total, order.getItemCount());
        logEventService.action("ORDER", "结算成功 orderId=" + order.getOrderId()
                + " total=" + total + " items=" + order.getItemCount()
                + " userId=" + userId + " sessionId=" + sessionId);

        // Delete selected items from cart
        int deleted;
        if (userId != null) {
            deleted = cartRepository.deleteByIdsForUser(itemIds, userId);
        } else {
            deleted = cartRepository.deleteByIds(itemIds, sessionId);
        }

        log.info("<== 支付完成 | orderId={} | total=¥{} | deleted={} items", order.getOrderId(), total, deleted);

        return ResponseEntity.ok(Map.of(
                "orderId", order.getOrderId(),
                "total", total,
                "itemCount", selectedItems.size(),
                "status", "paid",
                "paidAt", LocalDateTime.now().toString()
        ));
    }

    /**
     * GET /api/orders — 查询订单列表
     * 登录用户按 user_id 查询；匿名用户按 sessionId 查询。
     */
    @GetMapping
    public ResponseEntity<?> listOrders(
            @RequestParam(required = false) String sessionId,
            HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);

        List<Order> orders;
        if (userId != null) {
            orders = orderRepository.findByUserId(userId);
            log.debug("==> GET /api/orders | userId={} | count={}", userId, orders.size());
        } else if (sessionId != null && !sessionId.isBlank()) {
            orders = orderRepository.findBySessionId(sessionId);
            log.debug("==> GET /api/orders | sessionId={} | count={}", sessionId, orders.size());
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId is required for anonymous users"));
        }

        return ResponseEntity.ok(Map.of(
                "items", orders,
                "total", orders.size()
        ));
    }
}