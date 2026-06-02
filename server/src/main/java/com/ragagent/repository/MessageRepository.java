package com.ragagent.repository;

import com.ragagent.model.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Message> ROW_MAPPER = (rs, rowNum) -> {
        Message m = new Message();
        m.setId(rs.getLong("id"));
        m.setConversationId(rs.getLong("conversation_id"));
        m.setRole(rs.getString("role"));
        m.setContent(rs.getString("content"));
        m.setProductIds(rs.getString("product_ids"));
        m.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return m;
    };

    public MessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Long conversationId, String role, String content, String productIds) {
        jdbc.update(
                "INSERT INTO messages (conversation_id, role, content, product_ids) VALUES (?, ?, ?, ?)",
                conversationId, role, content, productIds);
    }

    public List<Message> findByConversationId(Long conversationId) {
        return jdbc.query(
                "SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC",
                ROW_MAPPER, conversationId);
    }
}
