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
-- 购物车表（加分项）
-- =============================================
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL COMMENT '会话ID',
    product_id VARCHAR(50) NOT NULL COMMENT '商品ID',
    quantity INT DEFAULT 1 COMMENT '数量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- =============================================
-- 索引
-- =============================================
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_sub_category ON products(sub_category);
CREATE INDEX idx_products_brand ON products(brand);
CREATE INDEX idx_products_price ON products(base_price);

-- 完成
SELECT '数据库初始化完成！' AS status;
