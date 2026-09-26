-- ============================================================
-- V1: 基线（Baseline）—— 项目当前线上真实结构快照
-- 来源：2026-09-26 由线上 rag_agent 库 SHOW CREATE TABLE 导出
-- 说明：
--   1) 已有数据的库：由 flyway.baseline-on-migrate 标记为 V1，本文件不执行
--   2) 全新空库：执行本文件，建出与线上完全一致的结构
--   3) 之后所有结构变更请新增 V2__xxx.sql，禁止手工 ALTER 线上
-- ============================================================

-- 商品主表
CREATE TABLE `products` (
  `product_id` varchar(50) NOT NULL COMMENT '商品ID，如p_beauty_001',
  `title` varchar(300) NOT NULL COMMENT '商品标题',
  `brand` varchar(100) NOT NULL COMMENT '品牌',
  `category` varchar(50) NOT NULL COMMENT '顶级类目',
  `sub_category` varchar(50) NOT NULL COMMENT '子类目',
  `base_price` decimal(10,2) NOT NULL COMMENT '基础价格',
  `image_path` varchar(200) DEFAULT '' COMMENT '图片相对路径',
  `marketing_description` text COMMENT '营销描述',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`product_id`),
  KEY `idx_products_category` (`category`),
  KEY `idx_products_sub_category` (`sub_category`),
  KEY `idx_products_brand` (`brand`),
  KEY `idx_products_price` (`base_price`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品信息表';

-- 商品 SKU 表
CREATE TABLE `product_skus` (
  `sku_id` varchar(50) NOT NULL COMMENT 'SKU ID',
  `product_id` varchar(50) NOT NULL COMMENT '所属商品ID',
  `properties` json DEFAULT NULL COMMENT 'SKU属性，如{"容量":"30ml"}',
  `price` decimal(10,2) NOT NULL COMMENT 'SKU价格',
  PRIMARY KEY (`sku_id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `product_skus_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品SKU表';

-- 商品 FAQ 表
CREATE TABLE `product_faqs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` varchar(50) NOT NULL COMMENT '所属商品ID',
  `question` varchar(500) NOT NULL COMMENT '问题',
  `answer` text NOT NULL COMMENT '回答',
  PRIMARY KEY (`id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `product_faqs_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品FAQ表';

-- 用户评论表
CREATE TABLE `product_reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` varchar(50) NOT NULL COMMENT '所属商品ID',
  `nickname` varchar(50) NOT NULL COMMENT '用户昵称',
  `rating` int NOT NULL COMMENT '评分1-5',
  `content` text NOT NULL COMMENT '评论内容',
  PRIMARY KEY (`id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `product_reviews_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户评论表';

-- 购物车表（匿名 session 维度 + 登录 user 维度）
CREATE TABLE `cart_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(50) NOT NULL COMMENT '会话ID',
  `product_id` varchar(50) NOT NULL COMMENT '商品ID',
  `sku_id` varchar(100) DEFAULT NULL,
  `sku_label` varchar(500) DEFAULT NULL,
  `quantity` int DEFAULT '1' COMMENT '数量',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `cart_items_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车表';

-- 用户表
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password_hash` varchar(200) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 会话表（长期记忆）
CREATE TABLE `conversations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `conversation_id` varchar(36) NOT NULL,
  `title` varchar(100) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `conversation_id` (`conversation_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 消息表
CREATE TABLE `messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `role` varchar(10) NOT NULL,
  `content` text,
  `product_ids` varchar(500) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_conv_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 订单表
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` varchar(32) NOT NULL COMMENT '订单号',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID，匿名可为null',
  `session_id` varchar(50) NOT NULL COMMENT '会话ID',
  `total_amount` decimal(10,2) NOT NULL COMMENT '总金额',
  `item_count` int NOT NULL COMMENT '商品数量',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT '状态',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `paid_at` datetime DEFAULT NULL COMMENT '支付时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';

-- 用户行为表（偏好画像来源）
CREATE TABLE `user_behaviors` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `product_id` varchar(50) NOT NULL,
  `action_type` varchar(20) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_action_type` (`action_type`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
