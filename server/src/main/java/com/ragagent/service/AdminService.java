package com.ragagent.service;

import com.ragagent.model.Message;
import com.ragagent.model.Order;
import com.ragagent.model.UserBehavior;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台只读查询服务。
 * 权限由 {@link com.ragagent.security.AdminAuthInterceptor} 统一拦截，此处只负责取数。
 */
@Service
public class AdminService {

    private final JdbcTemplate jdbc;
    private final com.ragagent.repository.JudgeRunRepository judgeRunRepo;

    public AdminService(JdbcTemplate jdbc, com.ragagent.repository.JudgeRunRepository judgeRunRepo) {
        this.jdbc = jdbc;
        this.judgeRunRepo = judgeRunRepo;
    }

    // ==================== DTO ====================
    public record Overview(long userCount, long conversationCount, long messageCount,
                           long orderCount, double gmv, long productCount, long behaviorCount,
                           long todayNewUsers, long todayConversations, long todayOrders) {}

    public record UserRow(long id, String username, String role, LocalDateTime createdAt,
                          long conversationCount, long orderCount) {}

    public record ConversationRow(long id, String conversationId, Long userId, String username,
                                  String title, long messageCount,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record TimeseriesPoint(String date, long newUsers, long conversations, long orders) {}

    public record LogEventRow(long id, LocalDateTime ts, String level, String category,
                              String logger, String message, String thread) {}

    public record PageResult<T>(List<T> items, long total, int page, int size) {}

    // ==================== 概览 ====================
    public Overview overview() {
        Double gmv = jdbc.queryForObject("SELECT COALESCE(SUM(total_amount), 0) FROM orders", Double.class);
        return new Overview(
                count("SELECT COUNT(*) FROM users"),
                count("SELECT COUNT(*) FROM conversations"),
                count("SELECT COUNT(*) FROM messages"),
                count("SELECT COUNT(*) FROM orders"),
                gmv != null ? gmv : 0,
                count("SELECT COUNT(*) FROM products"),
                count("SELECT COUNT(*) FROM user_behaviors"),
                count("SELECT COUNT(*) FROM users WHERE DATE(created_at) = CURDATE()"),
                count("SELECT COUNT(*) FROM conversations WHERE DATE(created_at) = CURDATE()"),
                count("SELECT COUNT(*) FROM orders WHERE DATE(created_at) = CURDATE()"));
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n != null ? n : 0;
    }

    // ==================== 用户 ====================
    private static final RowMapper<UserRow> USER_ROW = (rs, i) -> new UserRow(
            rs.getLong("id"), rs.getString("username"), rs.getString("role"),
            ts(rs.getTimestamp("created_at")),
            rs.getLong("conv_count"), rs.getLong("order_count"));

    public PageResult<UserRow> users(int page, int size, String q) {
        int offset = offset(page, size);
        boolean hasQ = q != null && !q.isBlank();
        String where = hasQ ? "WHERE u.username LIKE ?" : "";
        Object[] qArg = hasQ ? new Object[]{"%" + q.trim() + "%"} : new Object[]{};
        long total = count("SELECT COUNT(*) FROM users u " + where, qArg);

        String sql = """
            SELECT u.id, u.username, u.role, u.created_at,
                   (SELECT COUNT(*) FROM conversations c WHERE c.user_id = u.id) AS conv_count,
                   (SELECT COUNT(*) FROM orders o WHERE o.user_id = u.id) AS order_count
            FROM users u
            """ + where + " ORDER BY u.id DESC LIMIT ? OFFSET ?";
        Object[] args = hasQ
                ? new Object[]{qArg[0], size, offset}
                : new Object[]{size, offset};
        return new PageResult<>(jdbc.query(sql, USER_ROW, args), total, page, size);
    }

    // ==================== 会话 ====================
    private static final RowMapper<ConversationRow> CONV_ROW = (rs, i) -> new ConversationRow(
            rs.getLong("id"), rs.getString("conversation_id"), (Long) rs.getObject("user_id"),
            rs.getString("username"), rs.getString("title"), rs.getLong("msg_count"),
            ts(rs.getTimestamp("created_at")), ts(rs.getTimestamp("updated_at")));

    public PageResult<ConversationRow> conversations(int page, int size, Long userId, String q) {
        int offset = offset(page, size);
        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (userId != null) {
            where.append(" AND c.user_id = ?");
            args.add(userId);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (c.title LIKE ? OR c.conversation_id LIKE ?)");
            args.add("%" + q.trim() + "%");
            args.add("%" + q.trim() + "%");
        }
        long total = count("SELECT COUNT(*) FROM conversations c " + where, args.toArray());

        String sql = """
            SELECT c.id, c.conversation_id, c.user_id, u.username, c.title, c.created_at, c.updated_at,
                   (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id) AS msg_count
            FROM conversations c LEFT JOIN users u ON u.id = c.user_id
            """ + where + " ORDER BY c.updated_at DESC LIMIT ? OFFSET ?";
        args.add(size);
        args.add(offset);
        return new PageResult<>(jdbc.query(sql, CONV_ROW, args.toArray()), total, page, size);
    }

    /** 某会话的全部消息（管理员视角，不校验归属） */
    public List<Message> messages(String conversationId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM conversations WHERE conversation_id = ?", Long.class, conversationId);
        if (ids.isEmpty()) return List.of();
        return jdbc.query("SELECT * FROM messages WHERE conversation_id = ? ORDER BY id",
                (rs, i) -> {
                    Message m = new Message();
                    m.setId(rs.getLong("id"));
                    m.setConversationId(rs.getLong("conversation_id"));
                    m.setRole(rs.getString("role"));
                    m.setContent(rs.getString("content"));
                    m.setProductIds(rs.getString("product_ids"));
                    m.setCreatedAt(ts(rs.getTimestamp("created_at")));
                    return m;
                }, ids.get(0));
    }

    // ==================== 订单 ====================
    private static final RowMapper<Order> ORDER_ROW = (rs, i) -> {
        Order o = new Order();
        o.setId(rs.getLong("id"));
        o.setOrderId(rs.getString("order_id"));
        o.setUserId(rs.getLong("user_id"));
        o.setSessionId(rs.getString("session_id"));
        o.setTotalAmount(rs.getDouble("total_amount"));
        o.setItemCount(rs.getInt("item_count"));
        o.setStatus(rs.getString("status"));
        o.setCreatedAt(ts(rs.getTimestamp("created_at")));
        o.setPaidAt(ts(rs.getTimestamp("paid_at")));
        return o;
    };

    public PageResult<Order> orders(int page, int size) {
        int offset = offset(page, size);
        long total = count("SELECT COUNT(*) FROM orders");
        List<Order> items = jdbc.query(
                "SELECT * FROM orders ORDER BY created_at DESC LIMIT ? OFFSET ?", ORDER_ROW, size, offset);
        return new PageResult<>(items, total, page, size);
    }

    // ==================== 行为 ====================
    public PageResult<UserBehavior> behaviors(int page, int size, String actionType) {
        int offset = offset(page, size);
        boolean hasType = actionType != null && !actionType.isBlank();
        String where = hasType ? "WHERE action_type = ?" : "";
        Object[] qArg = hasType ? new Object[]{actionType.trim()} : new Object[]{};
        long total = count("SELECT COUNT(*) FROM user_behaviors " + where, qArg);
        Object[] args = hasType
                ? new Object[]{actionType.trim(), size, offset}
                : new Object[]{size, offset};
        List<UserBehavior> items = jdbc.query(
                "SELECT * FROM user_behaviors " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    UserBehavior b = new UserBehavior();
                    b.setId(rs.getLong("id"));
                    b.setUserId(rs.getLong("user_id"));
                    b.setProductId(rs.getString("product_id"));
                    b.setActionType(rs.getString("action_type"));
                    b.setCreatedAt(ts(rs.getTimestamp("created_at")));
                    return b;
                }, args);
        return new PageResult<>(items, total, page, size);
    }

    // ==================== 活跃量时间序列 ====================
    public List<TimeseriesPoint> timeseries(int days) {
        int d = Math.max(1, Math.min(days, 365));
        // d 为已 clamp 的 int，直接内联无注入风险（MySQL 的 INTERVAL ? 占位符兼容性差）
        String sql = "WITH RECURSIVE dates AS ("
                + " SELECT CURDATE() - INTERVAL " + d + " DAY AS d"
                + " UNION ALL SELECT d + INTERVAL 1 DAY FROM dates WHERE d < CURDATE()"
                + " ) SELECT DATE_FORMAT(dates.d, '%Y-%m-%d') AS date,"
                + " (SELECT COUNT(*) FROM users u WHERE DATE(u.created_at) = dates.d) AS new_users,"
                + " (SELECT COUNT(*) FROM conversations c WHERE DATE(c.created_at) = dates.d) AS conversations,"
                + " (SELECT COUNT(*) FROM orders o WHERE DATE(o.created_at) = dates.d) AS orders"
                + " FROM dates";
        return jdbc.query(sql, (rs, i) -> new TimeseriesPoint(
                rs.getString("date"), rs.getLong("new_users"),
                rs.getLong("conversations"), rs.getLong("orders")));
    }

    // ==================== 日志事件 ====================
    public PageResult<LogEventRow> logs(int page, int size, String category, String level, String logger, String q) {
        int offset = offset(page, size);
        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            where.append(" AND category = ?");
            args.add(category.trim());
        }
        if (level != null && !level.isBlank()) {
            where.append(" AND level = ?");
            args.add(level.trim());
        }
        if (logger != null && !logger.isBlank()) {
            where.append(" AND logger LIKE ?");
            args.add("%" + logger.trim() + "%");
        }
        if (q != null && !q.isBlank()) {
            // grep 式：关键词跨字段模糊匹配（内容 / 级别 / 分类 / 来源）
            String like = "%" + q.trim() + "%";
            where.append(" AND (message LIKE ? OR level LIKE ? OR category LIKE ? OR logger LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        long total = count("SELECT COUNT(*) FROM log_events " + where, args.toArray());
        args.add(size);
        args.add(offset);
        List<LogEventRow> items = jdbc.query(
                "SELECT * FROM log_events " + where + " ORDER BY ts DESC LIMIT ? OFFSET ?",
                (rs, i) -> new LogEventRow(
                        rs.getLong("id"), ts(rs.getTimestamp("ts")), rs.getString("level"),
                        rs.getString("category"), rs.getString("logger"),
                        rs.getString("message"), rs.getString("thread")),
                args.toArray());
        return new PageResult<>(items, total, page, size);
    }

    // ==================== 评测结果 ====================
    public PageResult<com.ragagent.repository.JudgeRunRepository.RunRow> judgeRuns(int page, int size) {
        int offset = offset(page, size);
        long total = judgeRunRepo.countRuns();
        return new PageResult<>(judgeRunRepo.findRuns(size, offset), total, page, size);
    }

    public List<com.ragagent.repository.JudgeRunRepository.CaseRow> judgeCases(long runId) {
        return judgeRunRepo.findCases(runId);
    }

    // ==================== 工具 ====================
    private static int offset(int page, int size) {
        return Math.max(0, (Math.max(page, 1) - 1) * Math.max(size, 1));
    }

    private static LocalDateTime ts(java.sql.Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }
}
