-- =============================================
-- RAG 电商导购 Agent - 数据库初始化
-- =============================================
-- 说明：自 2026-09-26 起，**表结构由 Flyway 版本化管理**：
--   - 迁移脚本位置：server/src/main/resources/db/migration/V*.sql
--   - 应用启动时自动执行（全新空库会建出与线上一致的全部表）
--   - 已有数据的库通过 baseline-on-migrate 标记现状，不会重跑 V1
--   - 任何结构变更请新增 V2__xxx.sql，**禁止手工 ALTER 线上**
-- 因此本文件现在只负责「建库」这一件事。
-- =============================================

CREATE DATABASE IF NOT EXISTS rag_agent
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 建库后直接启动应用，Flyway 会自动建表。
SELECT '数据库已就绪，请启动应用让 Flyway 建表' AS status;
