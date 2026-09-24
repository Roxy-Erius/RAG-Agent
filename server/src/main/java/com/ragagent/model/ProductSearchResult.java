package com.ragagent.model;

/**
 * 这个类表示一个产品搜索结果，包含了产品的基本信息和与查询的相关度评分。
 * 返回独立的DTO，专门用于向量检索结果直接返回给前端，避免二次查询数据库。
 */
public class ProductSearchResult {
    private String productId;
    private double score;
    private String title;
    private String brand;
    private String category;
    private String subCategory;
    private double basePrice;
    private String imagePath;
    private String marketingDescription;
    /** 是否超出用户预算（仅在带预算的检索中标注；true=超预算） */
    private boolean overBudget;

    public ProductSearchResult(String productId, double score, String title, String brand,
                               String category, String subCategory, double basePrice,
                               String imagePath, String marketingDescription) {
        this.productId = productId;
        this.score = score;
        this.title = title;
        this.brand = brand;
        this.category = category;
        this.subCategory = subCategory;
        this.basePrice = basePrice;
        this.imagePath = imagePath;
        this.marketingDescription = marketingDescription;
    }

    // Getter 方法（省略 setter，因为返回对象通常是只读的）
    public String getProductId() { return productId; }
    public double getScore() { return score; }
    public String getTitle() { return title; }
    public String getBrand() { return brand; }
    public String getCategory() { return category; }
    public String getSubCategory() { return subCategory; }
    public double getBasePrice() { return basePrice; }
    public String getImagePath() { return imagePath; }
    public String getMarketingDescription() { return marketingDescription; }
    public boolean isOverBudget() { return overBudget; }
    public void setOverBudget(boolean overBudget) { this.overBudget = overBudget; }
}