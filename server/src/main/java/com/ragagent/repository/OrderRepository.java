package com.ragagent.repository;

import com.ragagent.model.CartItem;
import com.ragagent.model.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Order> ORDER_MAPPER = (rs, rowNum) -> {
        Order o = new Order();
        o.setId(rs.getLong("id"));
        o.setOrderId(rs.getString("order_id"));
        o.setUserId(rs.getLong("user_id"));
        o.setSessionId(rs.getString("session_id"));
        o.setTotalAmount(rs.getDouble("total_amount"));
        o.setItemCount(rs.getInt("item_count"));
        o.setStatus(rs.getString("status"));
        o.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        o.setPaidAt(rs.getTimestamp("paid_at") != null
                ? rs.getTimestamp("paid_at").toLocalDateTime() : null);
        return o;
    };

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 保存订单并返回带 ID 的订单对象。
     */
    public Order save(Order order) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO orders (order_id, user_id, session_id, total_amount, item_count, status, paid_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, order.getOrderId());
            ps.setObject(2, order.getUserId());
            ps.setString(3, order.getSessionId());
            ps.setDouble(4, order.getTotalAmount());
            ps.setInt(5, order.getItemCount());
            ps.setString(6, order.getStatus());
            ps.setObject(7, order.getPaidAt());
            return ps;
        }, keyHolder);
        order.setId(keyHolder.getKey().longValue());
        return order;
    }

    /**
     * 按订单号查询。
     */
    public Order findByOrderId(String orderId) {
        List<Order> results = jdbc.query(
                "SELECT * FROM orders WHERE order_id = ?", ORDER_MAPPER, orderId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 按用户 ID 查询订单列表（倒序）。
     */
    public List<Order> findByUserId(Long userId) {
        return jdbc.query(
                "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC",
                ORDER_MAPPER, userId);
    }

    /**
     * 按会话 ID 查询订单列表（匿名用户用）。
     */
    public List<Order> findBySessionId(String sessionId) {
        return jdbc.query(
                "SELECT * FROM orders WHERE session_id = ? ORDER BY created_at DESC",
                ORDER_MAPPER, sessionId);
    }
}