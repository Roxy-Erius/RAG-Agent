package com.ragagent.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 启动时自动扫描 data 目录下的 JSON 文件，导入 MySQL。
 * 数据集目录结构：
 *   ecommerce_agent_dataset/
 *   ├── 1_美妆护肤/data/p_beauty_001.json ...
 *   ├── 2_数码电子/data/p_digital_001.json ...
 *   ├── 3_服饰运动/data/p_clothes_001.json ...
 *   └── 4_食品生活/data/p_food_001.json ...
 */
@Component
@Order(0)
public class DataImportService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataImportService.class);
    private final JdbcTemplate jdbc;
    private final Gson gson = new Gson();

    public DataImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) throws Exception {
        // 检查是否已有数据
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        if (count != null && count > 0) {
            log.info("数据库已有 {} 条商品数据，跳过导入", count);
            return;
        }

        // 扫描数据集目录（相对于 server/ 的上级目录）
        Path datasetDir = Paths.get("../ecommerce_agent_dataset");
        if (!datasetDir.toFile().exists()) {
            log.warn("数据集目录不存在: {}，请将 ecommerce_agent_dataset 放到项目根目录", datasetDir.toAbsolutePath());
            return;
        }

        int imported = 0;
        File[] categories = datasetDir.toFile().listFiles(File::isDirectory);
        if (categories == null) return;

        for (File categoryDir : categories) {
            File dataDir = new File(categoryDir, "data");
            if (!dataDir.exists()) continue;

            File[] jsonFiles = dataDir.listFiles((d, name) -> name.endsWith(".json"));
            if (jsonFiles == null) continue;

            for (File jsonFile : jsonFiles) {
                try {
                    JsonObject product = gson.fromJson(new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8), JsonObject.class);
                    importProduct(product);
                    imported++;
                } catch (Exception e) {
                    log.error("导入失败: {}", jsonFile.getName(), e);
                }
            }
        }

        log.info("数据导入完成！共导入 {} 条商品", imported);
    }

    private void importProduct(JsonObject product) {
        String productId = product.get("product_id").getAsString();
        String title = product.get("title").getAsString();
        String brand = product.get("brand").getAsString();
        String category = product.get("category").getAsString();
        String subCategory = product.get("sub_category").getAsString();
        double basePrice = product.get("base_price").getAsDouble();
        String imagePath = product.has("image_path") ? product.get("image_path").getAsString() : "";

        // 提取营销描述
        String marketingDesc = "";
        if (product.has("rag_knowledge")) {
            JsonObject rag = product.getAsJsonObject("rag_knowledge");
            if (rag.has("marketing_description")) {
                marketingDesc = rag.get("marketing_description").getAsString();
            }
        }

        // 插入商品主表
        jdbc.update(
            "INSERT INTO products (product_id, title, brand, category, sub_category, base_price, image_path, marketing_description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            productId, title, brand, category, subCategory, basePrice, imagePath, marketingDesc
        );

        // 插入 SKU
        if (product.has("skus")) {
            JsonArray skus = product.getAsJsonArray("skus");
            for (JsonElement skuElem : skus) {
                JsonObject sku = skuElem.getAsJsonObject();
                String skuId = sku.get("sku_id").getAsString();
                double price = sku.get("price").getAsDouble();
                String properties = sku.has("properties") ? sku.get("properties").toString() : "{}";
                jdbc.update(
                    "INSERT INTO product_skus (sku_id, product_id, properties, price) VALUES (?, ?, ?, ?)",
                    skuId, productId, properties, price
                );
            }
        }

        // 插入 FAQ
        if (product.has("rag_knowledge")) {
            JsonObject rag = product.getAsJsonObject("rag_knowledge");
            if (rag.has("official_faq")) {
                JsonArray faqs = rag.getAsJsonArray("official_faq");
                for (JsonElement faqElem : faqs) {
                    JsonObject faq = faqElem.getAsJsonObject();
                    jdbc.update(
                        "INSERT INTO product_faqs (product_id, question, answer) VALUES (?, ?, ?)",
                        productId, faq.get("question").getAsString(), faq.get("answer").getAsString()
                    );
                }
            }

            // 插入评论
            if (rag.has("user_reviews")) {
                JsonArray reviews = rag.getAsJsonArray("user_reviews");
                for (JsonElement reviewElem : reviews) {
                    JsonObject review = reviewElem.getAsJsonObject();
                    jdbc.update(
                        "INSERT INTO product_reviews (product_id, nickname, rating, content) VALUES (?, ?, ?, ?)",
                        productId,
                        review.get("nickname").getAsString(),
                        review.get("rating").getAsInt(),
                        review.get("content").getAsString()
                    );
                }
            }
        }
    }
}
