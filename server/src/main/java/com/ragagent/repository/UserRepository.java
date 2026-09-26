package com.ragagent.repository;

import com.ragagent.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    private static final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        try { u.setRole(rs.getString("role")); } catch (Exception e) { u.setRole("USER"); }
        u.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return u;
    };

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public User findByUsername(String username) {
        List<User> users = jdbc.query("SELECT * FROM users WHERE username = ?", ROW_MAPPER, username);
        return users.isEmpty() ? null : users.get(0);
    }

    public User create(String username, String passwordHash) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO users (username, password_hash) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            return ps;
        }, keyHolder);
        User u = new User();
        u.setId(keyHolder.getKey().longValue());
        u.setUsername(username);
        return u;
    }
}
