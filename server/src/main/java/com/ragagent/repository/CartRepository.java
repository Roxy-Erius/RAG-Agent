package com.ragagent.repository;

import com.ragagent.model.CartItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class CartRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<CartItem> ROW_MAPPER = (rs, rowNum) -> {
        CartItem item = new CartItem();
        item.setId(rs.getLong("id"));
        item.setSessionId(rs.getString("session_id"));
        item.setProductId(rs.getString("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        try { item.setProductTitle(rs.getString("title")); } catch (Exception e) {}
        try { item.setProductBrand(rs.getString("brand")); } catch (Exception e) {}
        try { item.setProductPrice(rs.getDouble("base_price")); } catch (Exception e) {}
        try { item.setProductImagePath(rs.getString("image_path")); } catch (Exception e) {}
        return item;
    };

    private static final RowMapper<CartItem> PLAIN_MAPPER = (rs, rowNum) -> {
        CartItem item = new CartItem();
        item.setId(rs.getLong("id"));
        item.setSessionId(rs.getString("session_id"));
        item.setProductId(rs.getString("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return item;
    };

    public CartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CartItem add(String sessionId, String productId, int quantity) {
        List<CartItem> existing = jdbc.query(
                "SELECT * FROM cart_items WHERE session_id = ? AND product_id = ?",
                PLAIN_MAPPER, sessionId, productId);
        if (!existing.isEmpty()) {
            CartItem item = existing.get(0);
            jdbc.update("UPDATE cart_items SET quantity = quantity + ? WHERE id = ?",
                    quantity, item.getId());
            item.setQuantity(item.getQuantity() + quantity);
            return item;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO cart_items (session_id, product_id, quantity) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionId);
            ps.setString(2, productId);
            ps.setInt(3, quantity);
            return ps;
        }, keyHolder);
        CartItem item = new CartItem();
        item.setId(keyHolder.getKey().longValue());
        item.setSessionId(sessionId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    public boolean remove(Long id, String sessionId) {
        return jdbc.update("DELETE FROM cart_items WHERE id = ? AND session_id = ?",
                id, sessionId) > 0;
    }

    public CartItem updateQuantity(Long id, String sessionId, int quantity) {
        if (quantity <= 0) {
            remove(id, sessionId);
            return null;
        }
        jdbc.update("UPDATE cart_items SET quantity = ? WHERE id = ? AND session_id = ?",
                quantity, id, sessionId);
        List<CartItem> items = jdbc.query(
                "SELECT * FROM cart_items WHERE id = ?", PLAIN_MAPPER, id);
        return items.isEmpty() ? null : items.get(0);
    }

    public List<CartItem> findBySessionId(String sessionId) {
        return jdbc.query(
                "SELECT c.*, p.title, p.brand, p.base_price, p.image_path " +
                "FROM cart_items c LEFT JOIN products p ON c.product_id = p.product_id " +
                "WHERE c.session_id = ? ORDER BY c.created_at DESC",
                ROW_MAPPER, sessionId);
    }

    public CartItem add(String sessionId, Long userId, String productId, int quantity) {
        List<CartItem> existing = jdbc.query(
                "SELECT * FROM cart_items WHERE user_id = ? AND product_id = ?",
                PLAIN_MAPPER, userId, productId);
        if (!existing.isEmpty()) {
            CartItem item = existing.get(0);
            jdbc.update("UPDATE cart_items SET quantity = quantity + ? WHERE id = ?",
                    quantity, item.getId());
            item.setQuantity(item.getQuantity() + quantity);
            return item;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO cart_items (session_id, user_id, product_id, quantity) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionId);
            ps.setLong(2, userId);
            ps.setString(3, productId);
            ps.setInt(4, quantity);
            return ps;
        }, keyHolder);
        CartItem item = new CartItem();
        item.setId(keyHolder.getKey().longValue());
        item.setSessionId(sessionId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    public boolean removeByUser(Long id, Long userId) {
        return jdbc.update("DELETE FROM cart_items WHERE id = ? AND user_id = ?",
                id, userId) > 0;
    }

    public CartItem updateQuantityByUser(Long id, Long userId, int quantity) {
        if (quantity <= 0) {
            removeByUser(id, userId);
            return null;
        }
        jdbc.update("UPDATE cart_items SET quantity = ? WHERE id = ? AND user_id = ?",
                quantity, id, userId);
        List<CartItem> items = jdbc.query(
                "SELECT * FROM cart_items WHERE id = ?", PLAIN_MAPPER, id);
        return items.isEmpty() ? null : items.get(0);
    }

    public List<CartItem> findByUserId(Long userId) {
        return jdbc.query(
                "SELECT c.*, p.title, p.brand, p.base_price, p.image_path " +
                "FROM cart_items c LEFT JOIN products p ON c.product_id = p.product_id " +
                "WHERE c.user_id = ? ORDER BY c.created_at DESC",
                ROW_MAPPER, userId);
    }

    public void migrateSessionToUser(String sessionId, Long userId) {
        jdbc.update("UPDATE cart_items SET user_id = ? WHERE session_id = ? AND user_id IS NULL",
                userId, sessionId);
    }

    /** 直接设置数量（session 维度），不存在则插入 */
    public void setQuantity(String sessionId, String productId, int quantity) {
        List<CartItem> existing = jdbc.query(
                "SELECT * FROM cart_items WHERE session_id = ? AND product_id = ?",
                PLAIN_MAPPER, sessionId, productId);
        if (!existing.isEmpty()) {
            jdbc.update("UPDATE cart_items SET quantity = ? WHERE id = ?",
                    quantity, existing.get(0).getId());
        } else {
            jdbc.update("INSERT INTO cart_items (session_id, product_id, quantity) VALUES (?, ?, ?)",
                    sessionId, productId, quantity);
        }
    }

    /** 直接设置数量（用户维度），不存在则插入 */
    public void setQuantityByUser(Long userId, String productId, int quantity) {
        List<CartItem> existing = jdbc.query(
                "SELECT * FROM cart_items WHERE user_id = ? AND product_id = ?",
                PLAIN_MAPPER, userId, productId);
        if (!existing.isEmpty()) {
            jdbc.update("UPDATE cart_items SET quantity = ? WHERE id = ?",
                    quantity, existing.get(0).getId());
        } else {
            jdbc.update("INSERT INTO cart_items (user_id, product_id, quantity) VALUES (?, ?, ?)",
                    userId, productId, quantity);
        }
    }

    public int deleteByIds(List<Long> ids, String sessionId) {
        if (ids == null || ids.isEmpty()) return 0;
        String placeholders = ids.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        return jdbc.update("DELETE FROM cart_items WHERE id IN (" + placeholders + ") AND session_id = ?",
                java.util.stream.Stream.concat(ids.stream(), java.util.stream.Stream.of(sessionId)).toArray());
    }

    public int deleteByIdsForUser(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) return 0;
        String placeholders = ids.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        return jdbc.update("DELETE FROM cart_items WHERE id IN (" + placeholders + ") AND user_id = ?",
                java.util.stream.Stream.concat(ids.stream(), java.util.stream.Stream.of(userId)).toArray());
    }
}
