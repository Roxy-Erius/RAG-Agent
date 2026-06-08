package com.ragagent.model;

import java.time.LocalDateTime;

public class CartItem {
    private Long id;
    private String sessionId;
    private String productId;
    private String skuId;
    private String skuLabel;
    private int quantity;
    private LocalDateTime createdAt;
    private String productTitle;
    private String productBrand;
    private Double productPrice;
    private String productImagePath;
    private String productImageBase64;

    public CartItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }
    public String getSkuLabel() { return skuLabel; }
    public void setSkuLabel(String skuLabel) { this.skuLabel = skuLabel; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getProductTitle() { return productTitle; }
    public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
    public String getProductBrand() { return productBrand; }
    public void setProductBrand(String productBrand) { this.productBrand = productBrand; }
    public Double getProductPrice() { return productPrice; }
    public void setProductPrice(Double productPrice) { this.productPrice = productPrice; }
    public String getProductImagePath() { return productImagePath; }
    public void setProductImagePath(String productImagePath) { this.productImagePath = productImagePath; }
    public String getProductImageBase64() { return productImageBase64; }
    public void setProductImageBase64(String productImageBase64) { this.productImageBase64 = productImageBase64; }
}
