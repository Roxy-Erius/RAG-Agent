package com.ragagent.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Product {
    private String productId;
    private String title;
    private String brand;
    private String category;
    private String subCategory;
    private BigDecimal basePrice;
    private String imagePath;
    private String marketingDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
