# Day 11 — 会话历史持久化与欢迎页

## 一、设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 会话存储 | MySQL（conversations + messages 表） | 持久化，关联用户，支持多会话 |
| 匿名用户 | 保持内存存储 | 不影响现有体验，不产生无用数据 |
| 会话标题 | 取首条用户消息前 30 字 | 自动生成，无需用户命名 |
| 卡片还原 | 从 `product_ids` JSON 批量拉取 | 一次 API 调用，避免 N+1 查询 |
| 入口改造 | WelcomeActivity 作为 LAUNCHER | 给新用户清晰的登录/游客选择 |

## 二、后端实现

### 2.1 数据库

```sql
CREATE TABLE conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id VARCHAR(36) NOT NULL UNIQUE,
    title VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL,          -- "user" | "ai"
    content TEXT,
    product_ids VARCHAR(500),           -- JSON 数组: ["p_digital_007","p_beauty_003"]
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_id (conversation_id)
);
```

- 使用 `@PostConstruct` 自动建表（`CREATE TABLE IF NOT EXISTS`）
- `conversation_id` 是暴露给前端的 UUID 字符串，内部 `id` 是自增主键

### 2.2 API 接口

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/conversations` | 获取用户会话列表（按时间倒序） | JWT |
| GET | `/api/conversations/{id}/messages` | 获取会话所有消息 | JWT |
| DELETE | `/api/conversations/{id}` | 删除会话及消息 | JWT |

### 2.3 核心文件

| 文件 | 说明 |
|------|------|
| `model/Conversation.java` | 会话模型 |
| `model/Message.java` | 消息模型（role + content + productIds） |
| `model/ChatRequest.java` | 新增 `conversationId` 字段 |
| `repository/ConversationRepository.java` | 会话 CRUD + `@PostConstruct` 建表 |
| `repository/MessageRepository.java` | 消息 CRUD |
| `service/ConversationService.java` | 业务：getOrCreate / saveMessage / list / delete |
| `controller/ConversationController.java` | REST API（3 端点，均需 JWT） |

### 2.4 ChatService 改造

`chatStream()` 和 `chat()` 方法签名变更：

```java
// 之前
public void chatStream(String sessionId, String userMessage, SseEmitter emitter)

// 之后
public void chatStream(String sessionId, String conversationId, Long userId,
                       String userMessage, SseEmitter emitter)
```

在 `onComplete` 回调中，登录用户的消息同时写入 MySQL：

```java
if (userId != null) {
    List<String> productIds = extractProductIds(reply);
    Conversation conv = conversationService.getOrCreate(userId, conversationId, userMessage);
    conversationService.saveMessage(conv.getId(), "user", userMessage, null);
    conversationService.saveMessage(conv.getId(), "ai", reply, productIds);
}
```

SSE `done` 事件新增 `conversationId` 字段，前端可捕获用于后续消息。

## 三、前端实现

### 3.1 欢迎页

| 文件 | 说明 |
|------|------|
| `ui/WelcomeActivity.kt` | 新 LAUNCHER 入口 |
| `res/layout/activity_welcome.xml` | Logo + 功能亮点 + 登录/注册/跳过按钮 |

```
WelcomeActivity
  ├── 已登录（有 token）→ 跳过，直达 MainActivity
  └── 未登录
        ├── 登录 → LoginActivity → setResult(OK) → MainActivity
        ├── 注册 → RegisterActivity → setResult(OK) → MainActivity
        └── 跳过 → MainActivity（游客模式）
```

### 3.2 历史会话页

| 文件 | 说明 |
|------|------|
| `ui/HistoryActivity.kt` | 历史会话列表 + 登录/登出卡片 |
| `ui/HistoryAdapter.kt` | RecyclerView 适配器 |
| `res/layout/activity_history.xml` | 头部 + 用户状态卡片 + 列表 |
| `res/layout/item_conversation.xml` | 会话条目（标题 + 时间 + 删除） |

用户状态卡片：
- **未登录**：🔒 提示卡片 → 点击跳转登录
- **已登录**：🤖 头像 + 用户名 → 点击弹确认框退出登录

### 3.3 会话切换与卡片还原

`ChatViewModel` 新增：

| 方法 | 说明 |
|------|------|
| `startNewChat()` | 清空消息，重置 conversationId |
| `loadConversation(cid, title)` | 加载历史消息 + 解析 productIds → 批量拉取商品卡片 |
| `getConversationId()` | 获取当前 conversationId |

关键修复：
- 加载历史 AI 消息时，用正则 `\[PRODUCT:\w+]` 清除文本中的 product 标签
- 从 MessageDto 的 `productIds` JSON 字段解析商品 ID，批量 `getProductsBatch()` 获取
- 按正确位置插入 `ChatMessage.ProductCard`

### 3.4 SSE 客户端改造

`SseClient.kt`：
- `connect()` 新增 `conversationId` 参数
- 新增 OkHttp 拦截器自动附加 JWT token（与 ApiService 一致）
- `SseEvent.Done` 携带 `conversationId`

### 3.5 其他修改

| 文件 | 变更 |
|------|------|
| `AndroidManifest.xml` | WelcomeActivity 设为 LAUNCHER，注册 HistoryActivity |
| `MainActivity.kt` | +📋 历史按钮，+`onActivityResult` 处理会话切换/新建 |
| `activity_main.xml` | 头部新增历史会话按钮 |
| `LoginActivity.kt` | 成功后 `setResult(RESULT_OK)` |
| `RegisterActivity.kt` | 成功后 `setResult(RESULT_OK)` |

## 四、Bug 修复

| Bug | 原因 | 修复 |
|-----|------|------|
| 切换会话后卡片消失 | `loadConversation()` 未解析 `product_ids` | 解析 JSON → 批量拉取 → 插入 ProductCard |
| 历史文字含 `[PRODUCT:id]` | SSE 流中由后端剥离，DB 中保留了原始文本 | 加载时 regex 清除 |

## 五、提交记录

```
9cc40ab feat: conversation history persistence, user auth flow, welcome page
26 files changed, 1671 insertions(+), 28 deletions(-)
```
