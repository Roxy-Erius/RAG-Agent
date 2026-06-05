# Day 12 — 商品详情页补全与购物车优化

## 一、设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 规格选择交互 | 淘宝风格 BottomSheet | 详情页不铺满规格标签，点击摘要行弹出面板 |
| SKU 分组 | 按属性维度拆分（颜色/存储/版本等） | 比扁平标签更清晰，动态解析不硬编码维度名 |
| 加购翻倍修复 | 立即购买仅跳转不加购 | 根因是两个按钮都调了 addToCart，非 Android 焦点问题 |
| 评价/FAQ 展示 | 默认 2 行截断，点击展开 | 长文本不占屏，想看全文可点击 |
| 建议词条 | V1 固定词条横向滑动 | V2 个性化后续再做，避免和推荐卡片功能重叠 |
| 聊天卡片加购 | 弹出规格选择面板 | 让用户在聊天流中也能选规格，而非无 SKU 加入 |

## 二、Bug 修复

### 2.1 加购翻倍

**现象**：商品详情页点"加入购物车"加 1 件，再点"立即购买"又加 1 件 → 购物车出现 2 件。

**根因**：两个按钮都调了 `apiService.addToCart()`。

**修复**：`btnBuyNow` 移除 `addToCart` 调用，仅跳转购物车页面。

### 2.2 数据库列不匹配

| 表 | 问题 | 修复 |
|---|---|---|
| `product_skus` | 查询了不存在的 `stock` 列 | 移除 |
| `product_reviews` | 查询了不存在的 `user_id`、`created_at` 列 | 移除 |
| `product_faqs` | 查询了不存在的 `created_at` 列 | 移除，按 `id` 排序 |

## 三、后端实现

### 3.1 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/products/{id}/skus` | 商品 SKU 列表 |
| GET | `/api/products/{id}/reviews` | 用户评价列表 |
| GET | `/api/products/{id}/faqs` | FAQ 问答列表 |

### 3.2 购物车 SKU 支持

| 文件 | 变更 |
|------|------|
| `model/CartItem.java` | 新增 `skuId`、`skuLabel` 字段 |
| `repository/CartRepository.java` | add() 按 (product_id, sku_id) 去重；查询 JOIN product_skus 取 SKU 价格 |
| `service/CartService.java` | addToCart 传递 SKU 参数 |
| `controller/CartController.java` | /add 接口接收 skuId、skuLabel 参数 |

## 四、前端实现

### 4.1 商品详情页

| 文件 | 说明 |
|------|------|
| `ui/ProductCardActivity.kt` | 规格摘要行 + SpecSheetDialog 集成 + 评价/FAQ 展示 |
| `ui/SpecSheetDialog.kt` | BottomSheetDialogFragment：SKU 解析 → 按维度分组 → 动态渲染标签行 → 选择匹配 → 数量调节 → 确认加购 |
| `res/layout/bottom_sheet_spec.xml` | 底部面板布局（商品图 + 价格 + 规格组容器 + 数量 + 确定按钮） |
| `res/layout/activity_product_detail.xml` | 规格摘要行 + 评价区 + FAQ 区 |
| `res/drawable/bg_spec_summary.xml` | 规格摘要行背景 |
| `res/drawable/bottom_sheet_bg.xml` | 面板圆角背景 |
| `res/drawable/bg_confirm_btn.xml` | 确定按钮 |
| `res/drawable/bg_qty_btn.xml` | 数量步进按钮 |
| `res/drawable/bg_tag_selected.xml` | 选中标签样式 |

**SpecSheetDialog 核心逻辑**：

```
SKU JSON properties → parseProperties() → Map<String,Map<String,String>>
                                                  ↓
                                          specDimensions: LinkedHashMap
                                          "颜色" → ["深空灰","星河银","雅丹黑"]
                                          "存储" → ["256GB","512GB","1TB"]
                                                  ↓
                                     createDimensionRow() × N
                                     createTagView() → 可选/不可用灰度
                                                  ↓
                                    onSpecValueSelected() → SKU 交叉匹配
                                    findBestMatch() → 降级策略
```

**数量调节**：− / + 步进器，范围 1–99。

### 4.2 建议提问词条 V1

| 文件 | 说明 |
|------|------|
| `res/layout/activity_main.xml` | 输入框上方 HorizontalScrollView + 4 个词条 TextView |
| `ui/MainActivity.kt` | 空对话 + 非流式时显示，点击直接发送消息 |

4 个固定词条：推荐油皮洗面奶、性价比高的手机、保湿面霜推荐、送女友礼物。

### 4.3 聊天卡片加购

`MainActivity.kt`：点聊天卡片"加入购物车"→ `showSpecSheetForProduct()` → 弹出 SpecSheetDialog → 选规格 + 数量 → 确定 → addToCart with SKU。

`ChatAdapter.kt`：移除 `addedProductIds` 状态管理，按钮始终可点击，每次弹出规格选择。

### 4.4 购物车 SKU 展示

| 文件 | 说明 |
|------|------|
| `res/layout/item_cart_product.xml` | 商品名下方新增 `tvCartSkuLabel`（11sp 灰色小字） |
| `ui/CartAdapter.kt` | 绑定 SKU 标签，无 SKU 时显示"标准" |

### 4.5 客户端 API

| 方法 | 说明 |
|------|------|
| `ApiService.getProductSkus(productId)` | 获取 SKU 列表 |
| `ApiService.getProductReviews(productId)` | 获取评价列表 |
| `ApiService.getProductFaqs(productId)` | 获取 FAQ 列表 |
| `ApiService.addToCart(skuId, skuLabel)` | 加购支持 SKU 参数 |

## 五、提交记录

```
1f69a08 feat: 聊天卡片加购弹出规格选择 + 购物车展示规格标签 + 清理待开发项
56218d7 docs: #7 建议提问词条 V1 完成，V2 个性化待后续开发
c975f4f feat: V1 建议提问词条 — 输入框上方横向滑动词条，点击直接发送
50d69f0 docs: 更新优化清单 — #6 商品详情页补全已完成，加购翻倍修复、SKU 支持附带完成
aa84bb3 feat: 评价和 FAQ 默认显示 2 行，点击卡片展开/收起全文
bbbcf0f fix: 移除 product_reviews 查询中不存在的 created_at 列
fb59b6a fix: 修正 product_skus/reviews/faqs 查询字段，移除数据库中不存在的列
b8bd165 feat: 商品详情页补全 — 规格选择 BottomSheet + 用户评价 + FAQ + SKU 支持
d98e13f fix: 立即购买按钮移除重复 addToCart，仅跳转购物车，消除加购翻倍问题
```

9 commits, 30 files changed.
