# Day 06 — API 联调与问题修复

## 一、后端 API 接入

### 1.1 前后端接口对照

| 接口 | 方法 | 前端位置 | 状态 |
|---|---|---|---|
| `/api/products/{id}` | GET | `ApiService.getProduct()` | ✅ 已有 |
| `/api/products/batch` | GET | `ApiService.getProductsBatch()` | ✅ 已有 |
| `/api/products/search` | GET | `ApiService.searchProducts()` | ✅ 新增 |
| `/api/chat/stream` | GET (SSE) | `SseClient.connect()` | ✅ 已有 |
| `/api/chat/session/{id}` | DELETE | `ApiService.clearSession()` | ✅ 新增 |

### 1.2 新增方法

**`ApiService.searchProducts(query, topK, category)`**
- 调用 `/api/products/search?query=&topK=&category=`
- 支持可选类目过滤（美妆护肤/数码电子/服饰运动/食品保健）

**`ApiService.clearSession(sessionId)`**
- 调用 `DELETE /api/chat/session/{id}`
- ChatViewModel.clearSession() 同步调用清除后端会话历史

---

## 二、SSE 协议修复（核心踩坑）

### 2.1 协议不匹配

**问题：** 前端 `SseClient.parseEvent()` 用 Gson 解析结构化 JSON（如 `{"type":"token","content":"..."}`），但后端发的是**原始文本 token**：

```
后端实际发送:
data:为
data:你
data:推荐
data:[DONE]

前端期待:
{"type": "token", "content": "为"}
{"type": "done"}
```

**修复：** 重写 `parseEvent()` 为纯文本匹配：
- `data == "[DONE]"` → Done
- `data.matches("^\\[PRODUCT:\\w+\\]$")` → ProductRef
- 其他 → Token

### 2.2 LLM Token 化拆散商品标记

**问题：** 修复后发现商品卡片仍不渲染。通过 Logcat 调试发现 `[PRODUCT:p_digital_007]` 被 LLM 拆成 **11 个碎片**逐个发送：

```
data=[[]
data=[PRO]
data=[DUCT]
data=[:p]
data=[_d]
data=[igital]
data=[_]
data=[0]
data=[0]
data=[7]
data=[]]
```

没有任何一行包含完整的 `[PRODUCT:xxx]`，正则永远匹配不到。

**修复：** 在 `SseEvent.Done` 处理时，从**完整累积的 aiText** 中用正则抽取商品 ID，去除显示文本中的标记，再拉取商品卡片：

```kotlin
// ChatViewModel.kt — Done 处理
val productIds = PRODUCT_TAG_REGEX.findAll(aiText)
    .map { it.groupValues[1] }.toList()
if (productIds.isNotEmpty()) {
    updateLastAiMessage(aiText.replace(PRODUCT_TAG_REGEX, ""))
    productIds.forEach { fetchAndInsertProductCard(it) }
}
```

这样无论标记被拆成多少片，最终都能正确识别。

### 2.3 崩溃：SocketException

**问题：** 收到 `[DONE]` 后 App 异常退出。堆栈：

```
FATAL EXCEPTION: main
java.net.SocketException: Socket closed
    at okhttp3.internal.sse.ServerSentEventReader.processNextEvent
```

**原因：** 在 `onEvent()` 回调里直接调 `eventSource.cancel()`，这会关闭底层 socket，但 OkHttp 的 SSE reader 还在同一个线程上尝试读数据，导致 SocketException。

**修复：** 移除 `onEvent` 中的手动 `cancel()`。后端发完 `[DONE]` 后会自动关闭 SSE 连接，OkHttp 检测到连接关闭后触发 `onClosed` → `awaitClose(eventSource.cancel())` 自然清理。

---

## 三、多轮对话修复

**问题：** 第二轮对话会覆盖第一轮的 AI 回复。

**原因：** `updateLastAiMessage()` 总是查找列表中**最后一条** `ChatMessage.Ai` 来更新。第二轮 token 到达时，最后一条 Ai 还是第一轮的回答，直接被替换：

```
[User("msg1"), Ai("reply1"), User("msg2"), Loading]
                                    ↑
                     第二轮 token 到 → updateLastAiMessage → 找到 reply1 → 覆盖！
```

**修复：** 新一轮 SSE 连接的第一个 token 到来时，先 `append(ChatMessage.Ai(""))` 创建新的空 Ai 条目，后续 token 更新这个新条目而非上一轮的回答。

---

## 四、商品卡片对齐修复

**问题：** 商品卡片偏左，与 AI 气泡文字未对齐。

**原因：** AI 气泡文字起始于 44dp（外部 padding 8dp + 头像 28dp + 间距 8dp），但商品卡片 `marginStart="8dp"`、`marginEnd="56dp"`。

**修复：** 商品卡片 margin 改为与 AI 气泡文字区域一致：
- `marginStart`: 8dp → **44dp**
- `marginEnd`: 56dp → **12dp**

---

## 五、构建验证

```bash
$ ./gradlew assembleDebug
BUILD SUCCESSFUL in 1m 39s
38 actionable tasks: 15 executed, 23 up-to-date
```

---

## 六、改动文件清单

```
client/app/src/main/java/com/ragagent/
├── network/
│   ├── SseClient.kt          (修改：文本解析、tag 拆分处理、crash 修复)
│   └── ApiService.kt         (修改：新增 searchProducts、clearSession)
├── viewmodel/
│   └── ChatViewModel.kt      (修改：Done 时提取 product tag、多轮修复、清除会话)
└── res/layout/
    └── item_product_card.xml (修改：margin 对齐 AI 气泡)
```
