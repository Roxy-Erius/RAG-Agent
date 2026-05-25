# RAG 电商导购 Agent — API 接口文档（Day 04 完整版）

> 后端地址：`http://服务器IP:8080`
> Swagger 中文文档：`http://服务器IP:8080/doc.html`

---

## 目录

1. [商品接口](#1-商品接口)
2. [对话接口](#2-对话接口)
3. [错误响应格式](#3-错误响应格式)
4. [SSE 协议说明](#4-sse-协议说明)
5. [PRODUCT 标签说明](#5-product-标签说明)
6. [联调指南](#6-联调指南)

---

## 1. 商品接口

### 1.1 查询单个商品

```
GET /api/products/{productId}
```

| 参数 | 位置 | 类型 | 必填 | 示例 |
|------|------|------|------|------|
| productId | Path | String | 是 | `p_beauty_001` |

```bash
curl http://localhost:8080/api/products/p_beauty_001
```

**成功响应 (200)**

```json
{
  "productId": "p_beauty_001",
  "title": "雅诗兰黛特润修护肌活精华露淡纹紧致保湿夜间修护抗初老精华30ml",
  "brand": "雅诗兰黛",
  "category": "美妆护肤",
  "subCategory": "精华",
  "basePrice": 720.00,
  "imagePath": "1_美妆护肤/images/p_beauty_001_live.jpg",
  "marketingDescription": "雅诗兰黛特润修护肌活精华露（小棕瓶）是夜间修护领域的标杆产品...",
  "createdAt": "2026-05-22T22:00:38",
  "updatedAt": "2026-05-22T22:00:38"
}
```

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 404 | 商品不存在 |

---

### 1.2 批量查询商品

```
GET /api/products/batch?ids={id1},{id2},{id3}
```

| 参数 | 类型 | 必填 | 示例 |
|------|------|------|------|
| ids | String | 是 | `p_beauty_001,p_digital_001,p_food_003` |

```bash
curl "http://localhost:8080/api/products/batch?ids=p_beauty_001,p_digital_001"
```

**成功响应 (200)** — 返回数组，不存在的 ID 会被跳过（不会整体报错）

```json
[
  { "productId": "p_beauty_001", "title": "...", "brand": "雅诗兰黛", "basePrice": 720.00, "..." },
  { "productId": "p_digital_001", "title": "...", "brand": "华为", "basePrice": 899.00, "..." }
]
```

> **C 同学注意：** 对话接口返回的 `productIds` 数组可以直接传到这里获取完整商品信息。

---

### 1.3 向量语义搜索

```
GET /api/products/search?query={自然语言}&topK={数量}&category={类目}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| query | String | **是** | — | 自然语言搜索词 |
| topK | int | 否 | `3` | 返回数量 |
| category | String | 否 | 不限 | 限定类目 |

**支持的类目值：** `美妆护肤` / `数码电子` / `服饰运动` / `食品保健`

```bash
curl "http://localhost:8080/api/products/search?query=保湿面霜&topK=3"
curl "http://localhost:8080/api/products/search?query=蓝牙耳机&topK=5&category=数码电子"
```

| 状态码 | 说明 |
|--------|------|
| 200 | 成功，无匹配时返回 `[]`（空数组，不是报错） |
| 400 | 参数格式错误（如 `topK=abc`） |

---

## 2. 对话接口

### 2.1 SSE 流式对话（推荐）

```
GET /api/chat/stream?message={消息}&sessionId={会话ID}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| message | String | **是** | — | 用户消息，支持中文 |
| sessionId | String | 否 | `default` | 会话标识，同一会话多轮对话需保持一致 |

```bash
curl -N "http://localhost:8080/api/chat/stream?message=推荐蓝牙耳机&sessionId=user001"
```

**响应格式：** SSE 事件流（`Content-Type: text/event-stream`）

```
data:为
data:你
data:推荐
data:华为
data:FreeBuds
data: Pro
data: 5
data: [PRODUCT:p_digital_007]
data:，
data:最高
...
data:[DONE]
```

- 每个 `data:` 行是一个 token，前端逐字拼接显示即可
- `data:[DONE]` 表示本次对话结束，可关闭连接
- **商品标记 `[PRODUCT:xxx]` 会混在 token 流中**，前端需解析后渲染为商品卡片

| 状态码 | 说明 |
|--------|------|
| 200 | SSE 流正常推送 |
| 500 | LLM 调用失败 |

---

### 2.2 非流式对话

```
POST /api/chat
Content-Type: application/json
```

**请求体：**

```json
{
  "sessionId": "user001",
  "message": "推荐保湿面霜"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 否 | 会话标识，默认 `default` |
| message | String | **是** | 用户消息 |

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"user001","message":"推荐保湿面霜"}'
```

**成功响应 (200)**

```json
{
  "sessionId": "user001",
  "reply": "为你推荐这款薇诺娜舒敏保湿特护霜 [PRODUCT:p_beauty_007]...",
  "productIds": ["p_beauty_007", "p_beauty_012"]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话标识 |
| reply | String | LLM 完整回复（含 `[PRODUCT:id]` 标记） |
| productIds | Array\<String\> | 回复中涉及的商品 ID 列表 |

> **C 同学注意：** 拿到 `productIds` 后可直接调用 `GET /api/products/batch?ids=...` 拉取商品完整信息渲染卡片。

---

### 2.3 清除会话历史

```
DELETE /api/chat/session/{sessionId}
```

```bash
curl -X DELETE http://localhost:8080/api/chat/session/user001
```

| 状态码 | 说明 |
|--------|------|
| 200 | 成功（会话不存在也返回 200） |

**使用场景：** 用户开始新的对话话题时清除历史，避免上一轮上下文干扰。

---

## 3. 错误响应格式

所有接口的**错误响应**都使用统一的 JSON 格式（由 `GlobalExceptionHandler` 统一处理）：

```json
{
  "error": "错误描述",
  "status": 400,
  "timestamp": "2026-05-25T10:56:15.404215100"
}
```

### 常见错误码

| HTTP 状态码 | error 示例 | 触发场景 |
|-------------|-----------|----------|
| 400 | `请求体格式错误，请检查 JSON 是否正确` | POST body 不是合法 JSON |
| 400 | `参数 'topK' 格式错误` | 参数类型不匹配（如 `topK=abc`） |
| 400 | `缺少必填参数: message` | 缺少必填查询参数 |
| 400 | `业务校验失败的具体原因` | `IllegalArgumentException` |
| 500 | `服务器内部错误，请稍后重试` | 数据库断连、LLM 调用失败等 |

> **C 同学注意：** 前端统一判断 `status` 字段即可，`error` 字段可直接展示给用户。

---

## 4. SSE 协议说明

### 4.1 什么是 SSE

SSE (Server-Sent Events) 是一种**服务端单向推送**协议。客户端发一次请求，服务端持续推送数据，直到结束。

对比 WebSocket：

| | SSE | WebSocket |
|------|-----|-----------|
| 方向 | 服务端 → 客户端（单向） | 双向 |
| 协议 | HTTP | ws:// |
| 复杂度 | 简单（浏览器原生 EventSource） | 需要额外库 |
| 适用场景 | AI 流式输出、通知推送 | 实时聊天、游戏 |

### 4.2 前端接收示例

**JavaScript (EventSource)**

```javascript
const eventSource = new EventSource(
  `http://localhost:8080/api/chat/stream?message=${encodeURIComponent(msg)}&sessionId=user001`
);

let fullReply = '';

eventSource.onmessage = (event) => {
  const token = event.data;
  if (token === '[DONE]') {
    eventSource.close();
    renderProducts(parseProductTags(fullReply));
    return;
  }
  fullReply += token;
  appendText(token);  // 逐字追加到 UI
};

eventSource.onerror = () => {
  eventSource.close();
  showError('对话连接失败');
};
```

### 4.3 注意事项

1. **中文编码：** `message` 参数中的中文必须做 URL 编码（`encodeURIComponent`）
2. **超时：** 服务端 SseEmitter 超时 60 秒，超时自动关闭连接
3. **断线重连：** EventSource 支持自动重连，但重连时会丢失 `fullReply`，建议在 `[DONE]` 前做好本地缓存
4. **`[PRODUCT:id]` 混在 token 流中：** 商品标记会作为 token 的一部分推送，前端需要在收到 `[DONE]` 后解析完整回复

---

## 5. [PRODUCT] 标签说明

### 5.1 格式

```
[PRODUCT:商品ID]
```

示例：`[PRODUCT:p_beauty_007]`（薇诺娜面霜）、`[PRODUCT:p_digital_007]`（华为耳机）

### 5.2 前端解析

```javascript
function parseProductTags(reply) {
  const pattern = /\[PRODUCT:(\w+)\]/g;
  const ids = [];
  let match;
  while ((match = pattern.exec(reply)) !== null) {
    ids.push(match[1]);
  }
  return ids;
}

// 示例
const reply = "推荐这款 [PRODUCT:p_beauty_007] 和 [PRODUCT:p_beauty_012]";
const ids = parseProductTags(reply);
// → ["p_beauty_007", "p_beauty_012"]

// 然后用这些 ID 拉取商品详情
fetch(`/api/products/batch?ids=${ids.join(',')}`).then(...)
```

### 5.3 渲染建议

1. 从 `reply` 中提取 `[PRODUCT:id]` 标签及其在文本中的位置
2. 用 `productIds` 调用 `/api/products/batch` 获取商品完整信息
3. 将 `[PRODUCT:id]` 替换为商品卡片 UI 组件（图片、名称、价格）

---

## 6. 联调指南

### 6.1 联调前检查清单

| 检查项 | 验证方式 |
|--------|----------|
| 服务是否启动 | 浏览器打开 `http://服务器IP:8080/doc.html`，能看到 Swagger 页面即正常 |
| ChromaDB 是否连通 | 调用 `/api/products/search?query=test&topK=1`，返回 200 即正常 |
| LLM API 是否连通 | 调用 `/api/chat/stream?message=你好`，有 token 返回即正常 |

### 6.2 前端对接流程

```
用户输入消息
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  方式 A：SSE 流式（推荐）                             │
│                                                      │
│  const es = new EventSource(                         │
│    `/api/chat/stream?message=${msg}&sessionId=${sid}` │
│  );                                                  │
│  es.onmessage = (e) => {                             │
│    if (e.data === '[DONE]') {                        │
│      es.close();                                     │
│      const ids = parseProductTags(fullReply);        │
│      fetchProducts(ids);  // 渲染商品卡片             │
│    } else {                                          │
│      fullReply += e.data;  // 追加 token             │
│    }                                                 │
│  };                                                  │
└─────────────────────────────────────────────────────┘
    或
┌─────────────────────────────────────────────────────┐
│  方式 B：非流式                                       │
│                                                      │
│  const res = await fetch('/api/chat', {              │
│    method: 'POST',                                   │
│    body: JSON.stringify({ sessionId, message })      │
│  });                                                 │
│  const { reply, productIds } = await res.json();     │
│  // 直接用 productIds 拉商品                          │
│  const products = await fetch(                       │
│    `/api/products/batch?ids=${productIds.join(',')}` │
│  );                                                  │
└─────────────────────────────────────────────────────┘
```

### 6.3 商品 ID 前缀说明

| 前缀 | 类目 | 示例 |
|------|------|------|
| `p_beauty_` | 美妆护肤 | `p_beauty_007` |
| `p_digital_` | 数码电子 | `p_digital_007` |
| `p_clothes_` | 服饰运动 | `p_clothes_007` |
| `p_food_` | 食品保健 | `p_food_001` |
| `p_home_` | 家居生活 | `p_home_009` |

### 6.4 接口速查表

| 方法 | 路径 | 用途 | 流式 |
|------|------|------|------|
| GET | `/api/products/{id}` | 查单个商品 | — |
| GET | `/api/products/batch?ids=...` | 批量查商品 | — |
| GET | `/api/products/search?query=...` | 语义搜索商品 | — |
| GET | `/api/chat/stream?message=...&sessionId=...` | AI 对话 | ✅ |
| POST | `/api/chat` | AI 对话 | ❌ |
| DELETE | `/api/chat/session/{id}` | 清除会话 | — |

---

## 附录：字段速查

### Product 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| productId | String | 商品唯一 ID |
| title | String | 商品标题 |
| brand | String | 品牌 |
| category | String | 类目 |
| subCategory | String | 子类目 |
| basePrice | Double | 价格（元） |
| imagePath | String | 图片相对路径 |
| marketingDescription | String | 营销卖点描述 |
| createdAt | String | 创建时间 |
| updatedAt | String | 更新时间 |

### ChatResponse 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话 ID |
| reply | String | LLM 完整回复 |
| productIds | Array\<String\> | 回复中涉及的商品 ID |
