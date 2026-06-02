package com.ragagent.model;

import java.time.LocalDateTime;

public class Message {
    private Long id;
    private Long conversationId;
    private String role;       // "user" or "ai"
    private String content;
    private String productIds; // JSON array string for AI messages, null for user
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getProductIds() { return productIds; }
    public void setProductIds(String productIds) { this.productIds = productIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
