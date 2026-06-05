# Day 15 — 商品详情页补全与购物车优化

## 一、加购翻倍修复

**根因**：详情页"加入购物车"和"立即购买"都调了 `addToCart()`，用户两按钮各点一次 → 加 2 件。

**修复**：`btnBuyNow` 移除 `addToCart`，仅跳转购物车。

## 二、商品详情页补全

### 2.1 设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 规格选择 | 淘宝风格 BottomSheet | 不铺满标签，点击摘要行弹出面板 |
| SKU 分组 | 按属性维度拆分 | 动态解析 JSON，不硬编码 |
| 评价/FAQ | 默认 2 行截断 | 长文本不占屏，点击展开全文 |

### 2.2 客户端新文件

| 文件 | 说明 |
|------|------|
| `ui/SpecSheetDialog.kt` | BottomSheetDialogFragment：SKU 解析 → 维度分组 → 标签行 → 交叉匹配 → 数量 → 确认 |
| `res/layout/bottom_sheet_spec.xml` | 面板布局 |

新增 drawable：`bg_spec_summary`、`bottom_sheet_bg`、`bg_confirm_btn`、`bg_qty_btn`、`bg_tag_selected`。

### 2.3 客户端修改

| 文件 | 变更 |
|------|------|
| `ProductCardActivity.kt` | 规格摘要行 + SpecSheetDialog 集成 + 评价/FAQ（点击展开全文） |
| `activity_product_detail.xml` | 规格摘要行 + 评价区 + FAQ 区 |

### 2.4 服务端

| 端点 | 说明 |
|------|------|
| GET `/api/products/{id}/skus` | SKU 列表 |
| GET `/api/products/{id}/reviews` | 评价列表 |
| GET `/api/products/{id}/faqs` | FAQ 列表 |

购物车新增 SKU 支持：`CartItem.skuId/skuLabel`，Repository 按 (product_id, sku_id) 去重，JOIN product_skus 取 SKU 价格。

### 2.5 数据库适配

| 表 | 修复 |
|---|---|
| `product_skus` | 移除不存在的 `stock` 列 |
| `product_reviews` | 移除 `user_id`、`created_at` |
| `product_faqs` | 移除 `created_at` |

## 三、建议提问词条 V1

| 决策 | 选择 |
|---|---|
| 位置 | 输入框上方 HorizontalScrollView 横向滑动 |
| 交互 | 点击直接发送 |
| 可见性 | 空对话 + 非流式时显示 |
| V2 | 个性化词条，后续做 |

## 四、聊天卡片加购 + 购物车 SKU 展示

| 变更 | 说明 |
|------|------|
| 聊天卡片"加入购物车" | 弹出 SpecSheetDialog 选规格再添加 |
| 购物车每行 | 商品名 + 规格小字（无 SKU 显示"标准"） |
| 按钮行为 | 始终可点击，不限制重复加购 |

## 五、客户端 API 新增

| 方法 | 说明 |
|------|------|
| `getProductSkus(id)` | SKU 列表 |
| `getProductReviews(id)` | 评价列表 |
| `getProductFaqs(id)` | FAQ 列表 |
| `addToCart(skuId, skuLabel, qty)` | 加购支持 SKU |

## 六、提交记录（2026-06-04 ~ 06-05）

```
9b59a58 docs: Day12 开发日志（后被拆分）
1f69a08 feat: 聊天卡片加购弹出规格选择 + 购物车展示规格标签
56218d7 docs: #7 建议提问词条 V1 完成
c975f4f feat: V1 建议提问词条
50d69f0 docs: 更新优化清单
aa84bb3 feat: 评价和 FAQ 默认 2 行截断
bbbcf0f fix: 移除 product_reviews 查询中不存在的 created_at
fb59b6a fix: 修正 product_skus/reviews/faqs 查询字段
b8bd165 feat: 商品详情页补全 — 规格 BottomSheet + 评价 + FAQ + SKU
d98e13f fix: 立即购买移除重复 addToCart
```

10 commits.
