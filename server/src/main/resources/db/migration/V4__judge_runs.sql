-- ============================================================
-- V4: LLM-as-Judge 评测结果
--   judge_runs  : 一次评测运行（批次）的汇总 → 用于看"每次迭代的进步/退步"
--   judge_cases : 每个用例的明细
-- 由 JudgeEvalService 写入；后台只读。
-- ============================================================

CREATE TABLE `judge_runs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `run_at` DATETIME(3) NOT NULL COMMENT '运行时间',
  `generator_model` VARCHAR(100) DEFAULT NULL COMMENT '被测(生成)模型',
  `judge_model` VARCHAR(100) DEFAULT NULL COMMENT '裁判模型',
  `case_count` INT NOT NULL COMMENT '用例数',
  `avg_faithfulness` DOUBLE DEFAULT NULL COMMENT '平均忠实度',
  `avg_relevance` DOUBLE DEFAULT NULL COMMENT '平均相关性',
  `avg_tone` DOUBLE DEFAULT NULL COMMENT '平均语气',
  `note` VARCHAR(300) DEFAULT NULL COMMENT '备注(本次改了什么)',
  PRIMARY KEY (`id`),
  KEY `idx_run_at` (`run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测运行汇总';

CREATE TABLE `judge_cases` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `run_id` BIGINT NOT NULL COMMENT '所属 judge_runs.id',
  `query` TEXT,
  `reply` TEXT,
  `faithfulness` INT,
  `relevance` INT,
  `tone` INT,
  `elapsed_ms` BIGINT,
  PRIMARY KEY (`id`),
  KEY `idx_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评测用例明细';
