# Day 12 — 对话驱动购物车、测试验证、个性化推荐与商品详情页补全

## 一、对话驱动购物车（ADD_TO_CART 标签）

### 设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 加购触发方式 | LLM 输出 `[ADD_TO_CART:id]` 标签 | SSE 流中实时检测，无需额外 API 调用 |
| 数量支持 | `[ADD_TO_CART:id:N]` 设数量 / `[:+N]` 加数量 | 灵活支持"加 3 件"、"再来 2 件" |
| 模式区分 | set 模式（替换）vs add 模式（叠加） | set 适合"买 3 件"，add 适合"再买 2 件" |
| 多商品冲突 | AI 先确认再操作 | 避免用户说要 A 但 AI 加了 B |
| 标签检测 | flushSseBuffer 捕获完整标签再发送 | LLM 分 token 输出，需拼接待完整 |

### 1.1 SSE 标签解析

`ChatService.flushSseBuffer()` — SSE 缓冲区累积 LLM token，正则 `\[(PRODUCT|ADD_TO_CART|DELETE_FROM_CART|CLEAR_CART)(?::(\w+)(?::(\+?\d+))?)?\]` 检测完整标签后分类发送结构化 JSON 事件。

`isIncompleteTag()` — 检测缓冲区末尾片段是否可能为不完整标签（如 `[ADD`、`[D`），保留等待后续 token。

### 1.2 标签检测修复（5 次迭代）

| 问题 | 修复 |
|------|------|
| startsWith/regex 方向错误 | 翻转匹配逻辑 |
| quantity 后缀 `:+2` 未覆盖 | 正则新增 `\+?` |
| LLM 将标签拆为单字符 | isIncompleteTag 处理单独 `[` 字符 |

### 1.3 客户端 SSE 事件处理

`SseEvent` 新增 `AddToCart`、`DeleteFromCart`、`ClearCart` 事件类型。`ChatViewModel` 在 SSE collect 中处理：

- `AddToCart(mode=set)` → `apiService.setCartQuantity()`
- `AddToCart(mode=add)` → `apiService.addToCart()`
- `DeleteFromCart` → `apiService.removeFromCart()`
- `ClearCart` → 遍历删除所有购物车项

`cartEventsDisabled` 标志位防止 Activity 不可见时重复处理 SSE 加购事件。

### 1.4 对话驱动购物车修复

| Bug | 修复 |
|-----|------|
| 购物车在 prompt 中不可见 | System prompt 中明确购物车状态格式 |
| 跨话题查询污染 | 限制品类关键词提取近 5 条消息 |
| AI 推荐多商品直接加第一个 | AI 先反问用户要哪个再操作 |

---

## 二、测试验证

### 2.1 测试清单

40 个测试点，覆盖 10 个模块：Chat API、SSE 流、商品检索、购物车 CRUD、对话驱动加购、标签解析、会话持久化、用户认证、推荐系统、前端 UI。

### 2.2 测试进度

```
18/40 API 验证通过
26/40 API + adb 通过
40/40 全部通过
```

### 2.3 修复项

| 问题 | 修复 |
|------|------|
| DONE 事件卡片消失 | 改为 DONE 时批量 `getProductsBatch()` 拉取 |
| 历史消息含 `[PRODUCT:id]` | 加载时 regex 清除所有标签类型 |
| 会话条目删除按钮太小 | 扩大点击区域 |

---

## 三、虚拟支付

### 3.1 功能

购物车页面支持复选框选择商品 → 点击结算 → 弹出支付确认对话框 → 模拟支付 → 加载动画 → 支付成功/失败提示。

### 3.2 实现

`CartActivity.kt` 新增 `checkedIds` 状态管理、全选/取消全选、选中商品总价计算、支付确认框。

---

## 四、配置优化

| 变更 | 说明 |
|------|------|
| BASE_URL 从 local.properties 读取 | `BuildConfig.BASE_URL` 由 Gradle 从 gitignored 文件注入，不再硬编码 |

---

## 五、个性化推荐 V1–V3（optimization 分支）

### 设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 推荐策略 | V1 关键词 → V2 语义 → V3 行为加权，逐级降级 | 保证总有推荐结果 |
| V2 Embedding | ChromaDB 语义检索历史消息 Top 3 | 理解用户意图而非关键词匹配 |
| V3 行为加权 | VIEW/CART/PURCHASE 信号 × 时间衰减 | 近期浏览权重更高 |
| 已购过滤 | V2/V3 自动排除已购买商品，推荐相似替代品 | 避免推荐已拥有的商品 |
| 卡片交互 | 点击跳详情页（非发消息） | 更符合用户预期 |

### 1.1 V1 关键词匹配

`ChatService.analyzeUserPreferences()` — 提取近 5 条历史消息中的品类关键词 → 随机选 3 条同品类商品。

### 1.2 V2 Embedding 语义检索

`RetrieverService` 新增 `retrieveByText()` — 拼接历史消息 → ChromaDB query_embeddings → 语义检索 Top 3。

降级链：V2 Exception → V1 fallback。

### 1.3 V3 行为加权推荐

`RecommendationService` — 服务端新建，核心逻辑：

```
user_behaviors 表
  ├── VIEW × 1.0 权重
  ├── CART × 3.0 权重
  └── PURCHASE × 5.0 权重
         ×
  时间衰减 = e^(-λ·days)  (λ=0.05)
         ↓
  品类得分聚合 → Top 3 品类 → 每品类 Top N 商品 → 过滤已购
         ↓
  插入已购商品的相似替代品（同品类、同价位段）
```

降级链：V3 → V2 → V1。

### 1.4 已购过滤

`ProductRepository` 新增 `findPurchasedProductIds()` — 从 `purchase_history` 表查用户已购 → V2/V3 `excludeProductIds` 参数过滤。

### 1.5 服务端新增文件

| 文件 | 说明 |
|------|------|
| `service/RecommendationService.java` | V3 行为加权推荐核心 |
| `repository/UserBehaviorRepository.java` | 用户行为记录 + 查询 |
| `controller/RecommendationController.java` | GET /api/recommendations |
| `controller/BehaviorController.java` | POST /api/behaviors/record |
| `model/UserBehavior.java` | 用户行为模型 |

### 1.6 Bug 修复

| Bug | 原因 | 修复 |
|-----|------|------|
| 推荐卡片闪退 | 滚动中 Adapter 反复 new ViewHolder | 改为复用现有 holder |
| 点击卡片发消息 | 卡片 onClick 绑定为发送文字 | 改为跳转 ProductCardActivity |
| 品类名称不匹配 | 代码用"数码"但 DB 是"数码电子" | 对齐为 数码电子/服饰运动 |
| 推荐已购商品 | 未过滤 purchase_history | V2/V3 排除 + 推荐相似替代品 |

---

## 六、加购翻倍修复

**现象**：商品详情页点"加入购物车"加 1 件，再点"立即购买"又加 1 件 → 购物车出现 2 件。

**根因**：两个按钮都调了 `apiService.addToCart()`。

**修复**：`btnBuyNow` 移除 `addToCart` 调用，仅跳转购物车页面。

---

## 七、商品详情页补全

### 3.1 设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 规格选择交互 | 淘宝风格 BottomSheet | 详情页不铺满标签，点击摘要行弹出面板 |
| SKU 分组 | 按属性维度拆分（颜色/存储/版本） | 动态解析 JSON，不硬编码维度名 |
| 评价/FAQ | 默认 2 行截断，点击展开 | 长文本不占屏 |
| 购物车 SKU | 按 (product_id, sku_id) 去重 | 不同规格独立成行 |

### 3.2 客户端

| 文件 | 说明 |
|------|------|
| `ui/SpecSheetDialog.kt` | BottomSheetDialogFragment，SKU 解析 → 按维度分组 → 动态渲染标签行 → 交叉匹配 → 数量调节 → 确认加购 |
| `res/layout/bottom_sheet_spec.xml` | 面板布局（商品图 + 价格 + 规格组容器 + 数量 + 确定按钮） |
| `ui/ProductCardActivity.kt` | 规格摘要行 + SpecSheetDialog 集成 + 评价/FAQ 展示 + SKU 价格更新 |
| `res/layout/activity_product_detail.xml` | 规格摘要行 + 评价区 + FAQ 区 + 展开/收起 |

新增 drawable：`bg_spec_summary`、`bottom_sheet_bg`、`bg_confirm_btn`、`bg_qty_btn`、`bg_tag_selected`。

### 3.3 服务端

| 端点 | 说明 |
|------|------|
| GET `/api/products/{id}/skus` | 商品 SKU 列表 |
| GET `/api/products/{id}/reviews` | 用户评价列表 |
| GET `/api/products/{id}/faqs` | FAQ 问答列表 |

| 文件 | 变更 |
|------|------|
| `ProductRepository.java` | 新增 findSkus/findReviews/findFaqs |
| `ProductController.java` | 新增 3 个端点 |
| `CartItem.java` | 新增 skuId、skuLabel 字段 |
| `CartRepository.java` | add 按 (product_id, sku_id) 去重；查询 JOIN product_skus 取 SKU 价格 |
| `CartService.java` | addToCart 传递 SKU 参数 |
| `CartController.java` | /add 接收 skuId、skuLabel |

### 3.4 数据库适配

| 表 | 问题 | 修复 |
|---|---|---|
| `product_skus` | SQL 查了不存在的 `stock` 列 | 移除 |
| `product_reviews` | SQL 查了不存在的 `user_id`、`created_at` 列 | 移除 |
| `product_faqs` | SQL 查了不存在的 `created_at` 列 | 移除，按 id 排序 |

---

## 八、建议提问词条 V1

| 决策 | 选择 | 理由 |
|---|---|---|
| 版本策略 | V1 固定词条，V2 个性化后续做 | 避免和推荐卡片功能重叠 |
| 位置 | 输入框上方横向滑动词条 | 类似推荐卡片，视觉统一 |
| 交互 | 点击直接发送 | 减少操作步骤 |

| 文件 | 变更 |
|------|------|
| `activity_main.xml` | 新增 HorizontalScrollView + 4 个词条 |
| `MainActivity.kt` | 空对话 + 非流式时显示，点击 sendMessage() |

---

## 九、聊天卡片加购 + 购物车 SKU 展示

| 决策 | 选择 | 理由 |
|---|---|---|
| 聊天卡片加购 | 弹出 SpecSheetDialog 选规格 | 确保每次加购都有 SKU |
| 按钮行为 | 始终可点击，不限制重复加购 | 用户可多次加同一商品不同规格 |
| 购物车规格 | 每行显示商品名 + 规格小字 | 无 SKU 时显示"标准" |

| 文件 | 变更 |
|------|------|
| `MainActivity.kt` | 聊天卡片 onAddToCart → showSpecSheetForProduct() |
| `ChatAdapter.kt` | 移除 addedProductIds 状态，按钮固定显示"加入购物车" |
| `item_cart_product.xml` | 新增 tvCartSkuLabel（11sp 灰色小字） |
| `CartAdapter.kt` | 绑定 SKU 标签 |

---

## 十、客户端 API 新增

| 方法 | 说明 |
|------|------|
| `getProductSkus(productId)` | SKU 列表 |
| `getProductReviews(productId)` | 评价列表 |
| `getProductFaqs(productId)` | FAQ 列表 |
| `getRecommendations()` | 个性化推荐 |
| `recordBehavior(productId, actionType)` | 行为记录 |
| `addToCart(sessionId, productId, skuId, skuLabel, quantity)` | 加购支持 SKU |

---

## 十一、提交记录

### rag_agent_frontend 分支（对话驱动购物车 + 测试 + 支付）

```
93245ea test: all 40 test points passed
4bde988 fix: enlarge delete button in conversation item + add 10-round test case
28c9724 fix: batch-fetch product cards at Done event + strip all tag types in history
267ed05 test: 26/40 passed (18 API + 8 adb), 14 need manual testing
4833a17 test: 18/40 passed via API verification, 22 need Android device
e33369e docs: comprehensive test checklist (40 test points, 10 modules)
60144a8 fix: isIncompleteTag now catches lone '[' character
d062a85 fix: isIncompleteTag helper for robust partial tag detection
d892377 fix: incomplete tag detection - flipped startsWith + regex covers quantity suffix
d1d9ecb fix: AI asks which product before adding to cart when multiple recommended
8d29870 fix: cart visibility in prompt, query cross-topic pollution, cart CRUD via chat
de98af0 feat: ADD_TO_CART supports set vs add mode
a915d89 feat: ADD_TO_CART supports quantity
94529df feat: conversation-driven add-to-cart via [ADD_TO_CART:id] tag
d51b7cc refactor: read BASE_URL from local.properties
b9ca1c3 feat: virtual payment with checkbox selection in cart
6493e24 feat: loading spinner dialog during payment processing
0f12c56 docs: optimization backlog
faf3f43 docs: 项目启动指南
```

### optimization 分支（个性化推荐 + 详情页补全 + 购物车优化）

```
9b59a58 docs: Day12 商品详情页补全与购物车优化开发日志
1f69a08 feat: 聊天卡片加购弹出规格选择 + 购物车展示规格标签 + 清理待开发项
56218d7 docs: #7 建议提问词条 V1 完成，V2 个性化待后续开发
c975f4f feat: V1 建议提问词条 — 输入框上方横向滑动词条，点击直接发送
50d69f0 docs: 更新优化清单 — #6 商品详情页补全已完成，加购翻倍修复、SKU 支持附带完成
aa84bb3 feat: 评价和 FAQ 默认显示 2 行，点击卡片展开/收起全文
bbbcf0f fix: 移除 product_reviews 查询中不存在的 created_at 列
fb59b6a fix: 修正 product_skus/reviews/faqs 查询字段，移除数据库中不存在的列
b8bd165 feat: 商品详情页补全 — 规格选择 BottomSheet + 用户评价 + FAQ + SKU 支持
d98e13f fix: 立即购买按钮移除重复 addToCart，仅跳转购物车，消除加购翻倍问题
3f05813 docs: 更新优化清单 — V1-V3 已完成，补充附带完成项
afc5a60 fix: V3/V2 推荐过滤已购买商品，推荐相似替代品而非已购
0d876b3 feat: V3 行为加权推荐 + 时间衰减，降级链 V3→V2→V1
159541c fix: 推荐卡片 adapter 复用，避免滚动中新建导致闪退
39c837f feat: V2 语义推荐 — 历史消息 Embedding 检索替代关键词匹配，V1 降级保留
efa61f3 feat: 商品详情页加入购物车和立即购买按钮
0897d7b fix: 推荐卡片宽度 140→180dp，点击跳转详情页而非发消息
d7a425b fix: 品类名称与数据库对齐 — 数码电子/服饰运动
2a67606 docs: 个性化推荐拆为 V1/V2/V3 三级方案
e0fce62 feat: V1 个性化推荐系统 — 猜你喜欢
```

共 38 commits：rag_agent_frontend 分支 19 + optimization 分支 19。
