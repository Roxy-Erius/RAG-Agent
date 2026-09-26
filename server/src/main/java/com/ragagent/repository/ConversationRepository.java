package com.ragagent.repository;

import com.ragagent.model.Conversation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class ConversationRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Conversation> ROW_MAPPER = (rs, rowNum) -> {
        Conversation c = new Conversation();
        c.setId(rs.getLong("id"));
        c.setUserId(rs.getLong("user_id"));
        c.setConversationId(rs.getString("conversation_id"));
        c.setTitle(rs.getString("title"));
        c.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        c.setUpdatedAt(rs.getTimestamp("updated_at") != null
                ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return c;
    };

    public ConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Conversation create(Long userId, String conversationId, String title) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO conversations (user_id, conversation_id, title) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, conversationId);
            ps.setString(3, title != null ? title.substring(0, Math.min(title.length(), 100)) : "新对话");
            return ps;
        }, keyHolder);
        Conversation c = new Conversation();
        c.setId(keyHolder.getKey().longValue());
        c.setUserId(userId);
        c.setConversationId(conversationId);
        c.setTitle(title);
        return c;
    }

    public Conversation findByConversationId(String conversationId) {
        List<Conversation> list = jdbc.query(
                "SELECT * FROM conversations WHERE conversation_id = ?", ROW_MAPPER, conversationId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Conversation> findByUserId(Long userId) {
        return jdbc.query(
                "SELECT * FROM conversations WHERE user_id = ? ORDER BY updated_at DESC",
                ROW_MAPPER, userId);
    }

    /**
     * 按关键词搜索用户的会话：标题或消息内容包含关键词。
     */
    public List<Conversation> search(Long userId, String keyword) {
        String like = "%" + keyword.trim() + "%";
        return jdbc.query("""
            SELECT DISTINCT c.* FROM conversations c
            LEFT JOIN messages m ON m.conversation_id = c.id
            WHERE c.user_id = ? AND (c.title LIKE ? OR IFNULL(m.content, '') LIKE ?)
            ORDER BY c.updated_at DESC
            """, ROW_MAPPER, userId, like, like);
    }

    public void updateTimestamp(Long id) {
        jdbc.update("UPDATE conversations SET updated_at = ? WHERE id = ?",
                new Timestamp(System.currentTimeMillis()), id);
    }

    public boolean delete(String conversationId, Long userId) {
        // First find the conversation's internal ID
        Conversation conv = findByConversationId(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) return false;
        // Delete messages first, then conversation
        jdbc.update("DELETE FROM messages WHERE conversation_id = ?", conv.getId());
        return jdbc.update("DELETE FROM conversations WHERE id = ? AND user_id = ?",
                conv.getId(), userId) > 0;
    }
}
