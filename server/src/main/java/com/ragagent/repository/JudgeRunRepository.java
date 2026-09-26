package com.ragagent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** 评测结果持久化（judge_runs / judge_cases）。写入由 JudgeEvalService 调用，读取给后台。 */
@Repository
public class JudgeRunRepository {

    private final JdbcTemplate jdbc;

    public JudgeRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RunRow(long id, LocalDateTime runAt, String generatorModel, String judgeModel,
                         int caseCount, double avgFaithfulness, double avgRelevance, double avgTone,
                         String note) {}

    public record CaseRow(long id, long runId, String query, String reply,
                          int faithfulness, int relevance, int tone, long elapsedMs) {}

    public long insertRun(LocalDateTime runAt, String generatorModel, String judgeModel,
                          int caseCount, double avgFaithfulness, double avgRelevance, double avgTone,
                          String note) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO judge_runs (run_at, generator_model, judge_model, case_count, "
                            + "avg_faithfulness, avg_relevance, avg_tone, note) VALUES (?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setTimestamp(1, Timestamp.valueOf(runAt));
            ps.setString(2, generatorModel);
            ps.setString(3, judgeModel);
            ps.setInt(4, caseCount);
            ps.setDouble(5, avgFaithfulness);
            ps.setDouble(6, avgRelevance);
            ps.setDouble(7, avgTone);
            ps.setString(8, note);
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    public void insertCase(long runId, String query, String reply,
                           int faithfulness, int relevance, int tone, long elapsedMs) {
        jdbc.update("INSERT INTO judge_cases (run_id, query, reply, faithfulness, relevance, tone, elapsed_ms) "
                        + "VALUES (?,?,?,?,?,?,?)",
                runId, query, reply, faithfulness, relevance, tone, elapsedMs);
    }

    private static final RowMapper<RunRow> RUN_ROW = (rs, i) -> new RunRow(
            rs.getLong("id"), rs.getTimestamp("run_at").toLocalDateTime(),
            rs.getString("generator_model"), rs.getString("judge_model"),
            rs.getInt("case_count"), rs.getDouble("avg_faithfulness"),
            rs.getDouble("avg_relevance"), rs.getDouble("avg_tone"), rs.getString("note"));

    public List<RunRow> findRuns(int limit, int offset) {
        return jdbc.query("SELECT * FROM judge_runs ORDER BY run_at DESC LIMIT ? OFFSET ?",
                RUN_ROW, limit, offset);
    }

    public long countRuns() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM judge_runs", Long.class);
        return n != null ? n : 0;
    }

    public List<CaseRow> findCases(long runId) {
        return jdbc.query("SELECT * FROM judge_cases WHERE run_id = ? ORDER BY id",
                (rs, i) -> new CaseRow(rs.getLong("id"), rs.getLong("run_id"), rs.getString("query"),
                        rs.getString("reply"), rs.getInt("faithfulness"), rs.getInt("relevance"),
                        rs.getInt("tone"), rs.getLong("elapsed_ms")), runId);
    }
}
