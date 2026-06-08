package com.ragagent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Product {
    private String productId;
    private String title;
    private String brand;
    private String category;
    private String subCategory;
    private BigDecimal basePrice;
    private String imagePath;
    private String imageBase64;  // 商品图片 Base64 编码（data:image/jpeg;base64,...）
    private String marketingDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
