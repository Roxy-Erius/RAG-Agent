# Day 12 — 对话驱动购物车与测试验证

## 一、对话驱动购物车（ADD_TO_CART 标签）

### 设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 加购触发 | LLM 输出 `[ADD_TO_CART:id]` 标签 | SSE 流中实时检测，无需额外 API |
| 数量支持 | `[:N]` 设数量 / `[:+N]` 加数量 | 灵活支持"买 3 件"、"再来 2 件" |
| 模式区分 | set（替换）vs add（叠加） | set 适合精确数量，add 适合累加 |
| 多商品冲突 | AI 先反问再操作 | 避免加错商品 |

### 1.1 SSE 标签解析

`ChatService.flushSseBuffer()` — SSE 缓冲区累积 LLM token，正则检测完整标签后分类发送 JSON 事件：

```
正则: \[(PRODUCT|ADD_TO_CART|DELETE_FROM_CART|CLEAR_CART)(?::(\w+)(?::(\+?\d+))?)?\]
```

`isIncompleteTag()` — 检测末尾片段是否可能为不完整标签（如 `[ADD`、`[`），保留等待后续 token。

### 1.2 标签检测修复（5 次迭代）

| 问题 | 修复 |
|------|------|
| startsWith/regex 方向错误 | 翻转匹配逻辑 |
| quantity 后缀 `:+2` 未覆盖 | 正则新增 `\+?` |
| LLM 拆标签为单字符 | isIncompleteTag 处理单独 `[` |

### 1.3 修复项

| Bug | 修复 |
|-----|------|
| 购物车在 prompt 中不可见 | System prompt 明确购物车状态格式 |
| 跨话题查询污染 | 品类关键词提取限制近 5 条消息 |
| AI 多推荐直接加第一个 | AI 先反问用户要哪个再操作 |

## 二、客户端 SSE 事件处理

`SseEvent` 新增：`AddToCart`（productId + quantity + mode）、`DeleteFromCart`（cartItemId）、`ClearCart`。

`ChatViewModel` SSE collect 处理：
- `AddToCart(mode=set)` → `setCartQuantity()`
- `AddToCart(mode=add)` → `addToCart()`
- `DeleteFromCart` → `removeFromCart()`
- `ClearCart` → 遍历删除

`cartEventsDisabled` 标志位防止切后台后重复处理。

## 三、测试验证

### 3.1 测试清单

40 个测试点，覆盖 10 个模块：Chat API、SSE 流、商品检索、购物车 CRUD、对话驱动加购、标签解析、会话持久化、用户认证、推荐系统、前端 UI。

### 3.2 测试进度

```
18/40 API 验证通过
26/40 API + adb 通过
40/40 全部通过
```

### 3.3 修复项

| 问题 | 修复 |
|------|------|
| DONE 事件卡片消失 | DONE 时批量 getProductsBatch() |
| 历史消息含 `[PRODUCT:id]` | 加载时 regex 清除所有标签 |
| 删除按钮太小 | 扩大点击区域 |

## 四、配置优化

BASE_URL 改为从 `local.properties` 读取（`BuildConfig.BASE_URL`），不再硬编码。

## 五、提交记录（2026-06-01）

```
93245ea test: all 40 test points passed
4bde988 fix: enlarge delete button + add 10-round test case
28c9724 fix: batch-fetch product cards at Done event + strip all tag types in history
267ed05 test: 26/40 passed (18 API + 8 adb)
4833a17 test: 18/40 passed via API verification
e33369e docs: comprehensive test checklist (40 test points, 10 modules)
60144a8 fix: isIncompleteTag catches lone '[' character
d062a85 fix: isIncompleteTag helper for robust partial tag detection
d892377 fix: incomplete tag detection fix
d1d9ecb fix: AI asks which product before adding to cart
8d29870 fix: cart visibility in prompt, cross-topic pollution
de98af0 feat: ADD_TO_CART supports set vs add mode
a915d89 feat: ADD_TO_CART supports quantity
94529df feat: conversation-driven add-to-cart via [ADD_TO_CART:id] tag
d51b7cc refactor: read BASE_URL from local.properties
```

15 commits.
