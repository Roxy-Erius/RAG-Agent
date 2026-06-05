package com.ragagent.repository;

import com.ragagent.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) -> {
        Product p = new Product();
        p.setProductId(rs.getString("product_id"));
        p.setTitle(rs.getString("title"));
        p.setBrand(rs.getString("brand"));
        p.setCategory(rs.getString("category"));
        p.setSubCategory(rs.getString("sub_category"));
        p.setBasePrice(rs.getBigDecimal("base_price"));
        p.setImagePath(rs.getString("image_path"));
        p.setMarketingDescription(rs.getString("marketing_description"));
        p.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        p.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return p;
    };

    public ProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Product findById(String productId) {
        List<Product> results = jdbc.query("SELECT * FROM products WHERE product_id = ?", ROW_MAPPER, productId);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Product> findByIds(List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) return List.of();
        String placeholders = String.join(",", productIds.stream().map(id -> "?").toArray(String[]::new));
        return jdbc.query("SELECT * FROM products WHERE product_id IN (" + placeholders + ")",
                ROW_MAPPER, productIds.toArray());
    }

    public List<Product> findAll() {
        return jdbc.query("SELECT * FROM products", ROW_MAPPER);
    }

    public List<Map<String, Object>> findSkusByProductId(String productId) {
        return jdbc.queryForList(
                "SELECT sku_id, product_id, properties, price FROM product_skus WHERE product_id = ?",
                productId);
    }

    public List<Map<String, Object>> findReviewsByProductId(String productId) {
        return jdbc.queryForList(
                "SELECT id, product_id, nickname, rating, content FROM product_reviews WHERE product_id = ? ORDER BY id DESC",
                productId);
    }

    public List<Map<String, Object>> findFaqsByProductId(String productId) {
        return jdbc.queryForList(
                "SELECT id, product_id, question, answer FROM product_faqs WHERE product_id = ? ORDER BY id ASC",
                productId);
    }
}
