package com.ragagent.model;

import java.util.List;

public class ChatResponse {
    private String sessionId;
    private String reply;
    private List<String> productIds;

    public ChatResponse() {}

    public ChatResponse(String sessionId, String reply, List<String> productIds) {
        this.sessionId = sessionId;
        this.reply = reply;
        this.productIds = productIds;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public List<String> getProductIds() { return productIds; }
    public void setProductIds(List<String> productIds) { this.productIds = productIds; }
}
