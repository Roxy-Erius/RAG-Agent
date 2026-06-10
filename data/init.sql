-- =============================================
-- RAG 电商导购 Agent - 数据库初始化脚本
-- 执行方式：在 MySQL 客户端中运行此脚本
-- =============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS rag_agent
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE rag_agent;

-- =============================================
-- 商品主表
-- =============================================
DROP TABLE IF EXISTS product_reviews;
DROP TABLE IF EXISTS product_faqs;
DROP TABLE IF EXISTS product_skus;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS products;

CREATE TABLE products (
    product_id VARCHAR(50) PRIMARY KEY COMMENT '商品ID，如p_beauty_001',
    title VARCHAR(300) NOT NULL COMMENT '商品标题',
    brand VARCHAR(100) NOT NULL COMMENT '品牌',
    category VARCHAR(50) NOT NULL COMMENT '顶级类目',
    sub_category VARCHAR(50) NOT NULL COMMENT '子类目',
    base_price DECIMAL(10,2) NOT NULL COMMENT '基础价格',
    image_path VARCHAR(200) DEFAULT '' COMMENT '图片相对路径',
    marketing_description TEXT COMMENT '营销描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品信息表';

-- =============================================
-- SKU 表（一个商品多个规格）
-- =============================================
CREATE TABLE product_skus (
    sku_id VARCHAR(50) PRIMARY KEY COMMENT 'SKU ID',
    product_id VARCHAR(50) NOT NULL COMMENT '所属商品ID',
    properties JSON COMMENT 'SKU属性，如{"容量":"30ml"}',
    price DECIMAL(10,2) NOT NULL COMMENT 'SKU价格',
    FOREIGN KEY (product_id) REFERENCES products(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

-- =============================================
-- FAQ 表
-- =============================================
CREATE TABLE product_faqs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL COMMENT '所属商品ID',
    question VARCHAR(500) NOT NULL COMMENT '问题',
    answer TEXT NOT NULL COMMENT '回答',
    FOREIGN KEY (product_id) REFERENCES products(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品FAQ表';

-- =============================================
-- 用户评论表
-- =============================================
CREATE TABLE product_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL COMMENT '所属商品ID',
    nickname VARCHAR(50) NOT NULL COMMENT '用户昵称',
    rating INT NOT NULL COMMENT '评分1-5',
    content TEXT NOT NULL COMMENT '评论内容',
    FOREIGN KEY (product_id) REFERENCES products(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户评论表';

-- =============================================
-- 购物车表
-- =============================================
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(50) COMMENT '匿名会话ID',
    user_id BIGINT COMMENT '登录用户ID',
    product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
    sku_id VARCHAR(100) COMMENT 'SKU ID',
    sku_label VARCHAR(500) COMMENT '规格标签',
    quantity INT DEFAULT 1 COMMENT '数量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- =============================================
-- 用户表
-- =============================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =============================================
-- 会话表
-- =============================================
CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id VARCHAR(36) NOT NULL UNIQUE,
    title VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- =============================================
-- 消息表
-- =============================================
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL COMMENT 'user / ai',
    content TEXT,
    product_ids VARCHAR(500) COMMENT 'JSON数组: ["p_digital_007"]',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- =============================================
-- 用户行为表
-- =============================================
CREATE TABLE IF NOT EXISTS user_behaviors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    action_type VARCHAR(20) NOT NULL COMMENT 'VIEW / CART / PURCHASE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_action_type (action_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为表';

-- =============================================
-- 索引
-- =============================================
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_sub_category ON products(sub_category);
CREATE INDEX idx_products_brand ON products(brand);
CREATE INDEX idx_products_price ON products(base_price);

-- 完成
SELECT '数据库初始化完成！' AS status;
