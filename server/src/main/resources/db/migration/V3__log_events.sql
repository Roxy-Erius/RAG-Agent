-- ============================================================
-- V3: 日志事件表（ERROR + 关键动作）
-- 由 LogEventService 异步写入；每天清理 30 天前数据
-- ============================================================

CREATE TABLE `log_events` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `ts` DATETIME(3) NOT NULL COMMENT '事件时间',
  `level` VARCHAR(10) NOT NULL COMMENT 'ERROR / ACTION',
  `category` VARCHAR(30) NOT NULL COMMENT '分类：ERROR / AUTH / ORDER / CHAT / CART',
  `logger` VARCHAR(200) DEFAULT NULL COMMENT '来源 logger / 动作名',
  `message` TEXT,
  `thread` VARCHAR(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ts` (`ts`),
  KEY `idx_category` (`category`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日志事件表';
