package com.ragagent.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单模型 — 结算后写入 orders 表。
 * 订单明细存 order_items 表。
 */
public class Order {
    private Long id;
    private String orderId;         // 订单号（UUID 前 8 位）
    private Long userId;            // 登录用户 ID，可为 null（匿名）
    private String sessionId;
    private Double totalAmount;
    private Integer itemCount;
    private String status;          // "paid" / "pending" / "cancelled"
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    // 关联的购物车项（结算时复制，展示用）
    private List<CartItem> items;

    public Order() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }
}