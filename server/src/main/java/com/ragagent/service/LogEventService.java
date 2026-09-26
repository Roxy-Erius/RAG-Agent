package com.ragagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 日志事件落库（ERROR + 关键动作）。
 *
 * <p>设计要点：**异步 + 有界队列 + 失败吞掉** —— 绝不能因为写库失败/变慢拖垮业务。
 * 队列满时丢弃最旧任务，宁可丢日志也不阻塞请求线程。
 */
@Service
public class LogEventService {

    private static final Logger log = LoggerFactory.getLogger(LogEventService.class);
    private static final int QUEUE_SIZE = 2000;
    private static final int MAX_MESSAGE_LEN = 4000;
    private static final int RETENTION_DAYS = 30;

    private final JdbcTemplate jdbc;

    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_SIZE),
            r -> {
                Thread t = new Thread(r, "log-db");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    public LogEventService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 记录关键动作（category：AUTH / ORDER / CHAT / CART …） */
    public void action(String category, String message) {
        record("ACTION", category, "action", message, Thread.currentThread().getName());
    }

    /** 记录 ERROR（由 LogDbAppender 调用） */
    public void error(String loggerName, String message, String thread) {
        record("ERROR", "ERROR", loggerName, message, thread);
    }

    public void record(String level, String category, String loggerName, String message, String thread) {
        try {
            executor.execute(() -> {
                try {
                    jdbc.update(
                            "INSERT INTO log_events (ts, level, category, logger, message, thread) VALUES (?,?,?,?,?,?)",
                            Timestamp.valueOf(LocalDateTime.now()), level, category, loggerName,
                            truncate(message), thread);
                } catch (Exception e) {
                    // 写库失败：只回落到文件日志，绝不抛出
                    log.warn("日志落库失败: {}", e.getMessage());
                }
            });
        } catch (Exception ignored) {
            // 线程池已关闭等极端情况：直接丢弃，不阻塞业务
        }
    }

    /** 每天 03:30 清理 30 天前的日志 */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        try {
            int n = jdbc.update("DELETE FROM log_events WHERE ts < NOW() - INTERVAL " + RETENTION_DAYS + " DAY");
            if (n > 0) log.info("清理过期日志 {} 条", n);
        } catch (Exception e) {
            log.warn("清理日志失败: {}", e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_MESSAGE_LEN ? s : s.substring(0, MAX_MESSAGE_LEN);
    }
}
