package com.ragagent.repository;

import com.ragagent.model.UserBehavior;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserBehaviorRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<UserBehavior> ROW_MAPPER = (rs, rowNum) -> {
        UserBehavior b = new UserBehavior();
        b.setId(rs.getLong("id"));
        b.setUserId(rs.getLong("user_id"));
        b.setProductId(rs.getString("product_id"));
        b.setActionType(rs.getString("action_type"));
        b.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return b;
    };

    public UserBehaviorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(Long userId, String productId, String actionType) {
        jdbc.update(
            "INSERT INTO user_behaviors (user_id, product_id, action_type) VALUES (?, ?, ?)",
            userId, productId, actionType);
    }

    public List<UserBehavior> findByUserId(Long userId) {
        return jdbc.query(
            "SELECT * FROM user_behaviors WHERE user_id = ? ORDER BY created_at DESC",
            ROW_MAPPER, userId);
    }
}
