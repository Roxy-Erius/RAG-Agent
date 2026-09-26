# RagAgent Bug 记录

> 记录项目开发中遇到的 Bug：症状 / 根因 / 影响 / 解决方案 / 验证。
> 新增条目请复制末尾「模板」。

## 目录

| 编号 | 标题 | 严重级别 | 状态 |
|------|------|:--------:|:----:|
| [BUG-001](#bug-001-chatservice-用成员变量保存请求级状态导致-sse-并发串流) | ChatService 用成员变量保存请求级状态，导致 SSE 并发串流 | 严重 | ✅ 已修复 |
| [BUG-002](#bug-002-web-前端未维护-conversationid历史无法恢复android-端已有对照实现) | Web 前端未维护 conversationId，历史无法恢复（Android 端已有实现） | 一般 | ✅ 已修复 |
| [BUG-003](#bug-003-数据库-schema-漂移仓库-ddl-与线上真实结构不一致) | 数据库 Schema 漂移：仓库 DDL 与线上真实结构不一致 | 严重 | ✅ 已修复 |

---

## BUG-001: ChatService 用成员变量保存请求级状态，导致 SSE 并发串流

- **发现时间**：2026-09-10
- **严重级别**：严重（跨用户数据串流）
- **状态**：✅ 已修复（2026-09-10）
- **相关文件**：`server/src/main/java/com/ragagent/service/ChatService.java`
- **回归测试**：`server/src/test/java/com/ragagent/ChatServiceConcurrencyTest.java`

### 症状

多人**同时**使用导购对话（SSE 流式）时可能出现：
- A 用户收到 B 用户的回复片段（内容交叉、串台）
- 商品标签 `[PRODUCT:id]` 纠错到**别人请求**的商品 ID
- SSE 流内容错乱、标签解析异常
- 极端情况下 `StringBuilder` 并发读写导致内容损坏或异常

单用户 / 低并发时几乎不出现，所以容易漏测。

### 根因

`ChatService` 是 Spring **单例**（`@Service`，默认单例），但用**成员变量**保存了**每次请求**才该有的状态：

```java
// 第 136 行：SSE 缓冲，累积 LLM token
private final StringBuilder sseBuffer = new StringBuilder();
// 第 138 行：当前请求的合法商品 ID，供 SSE 阶段校验纠错
private volatile Set<String> currentValidIds = Set.of();
```

`chatStream` 每次请求都会写入/清空它们：

```java
this.currentValidIds = validIds;              // 210 行：覆盖成员变量
...
sseBuffer.append(token);                      // 221 行：向共享缓冲追加
flushSseBuffer(emitter);                      // 222 行：读并可能清空共享缓冲
...
emitTokens(emitter, sseBuffer.toString());    // 232 行
sseBuffer.setLength(0);                       // 233 行：清空共享缓冲
```

而 `flushSseBuffer` 里也会读 `sseBuffer` / `currentValidIds`（612~700 行），并在解析到不完整标签时 `sseBuffer.append(...)`（693 行）。

**并发下两个请求共用同一个 `sseBuffer` 和 `currentValidIds`**：
- 请求 A 的 token 与请求 B 的 token 追加进同一个 buffer → 内容串台
- `currentValidIds` 被后发起的请求覆盖 → 校验/纠错用了别人的商品集合
- `StringBuilder` 本身非线程安全（`volatile` 只能保证可见性，不能隔离请求）

> 注意：`fullResponse`（215 行）是**局部变量**，是对的；问题只在 `sseBuffer` 和 `currentValidIds` 这两个成员变量。

### 复现

1. 启动服务，用两个浏览器（或两个 `curl -N`）在**同一两秒内**分别发起：
   `GET /api/chat/stream?message=推荐保湿面霜&sessionId=A`
   `GET /api/chat/stream?message=推荐跑步鞋&sessionId=B`
2. 观察两个 SSE 流是否出现**对方的内容** / 商品 ID 被纠错成对方商品。

### 影响

- **跨用户内容泄漏**（隐私 / 正确性问题）
- 商品标签纠错到错误 ID → 前端展示错误商品卡片
- 并发下流式内容错乱、偶发异常

### 解决方案

把这两个「请求级状态」改为 `chatStream` 方法内的**局部变量**，并作为参数传给 `flushSseBuffer`：

```java
// chatStream 内（请求级）
StringBuilder sseBuffer = new StringBuilder();
Set<String> validIds = products.stream()
        .map(ProductSearchResult::getProductId)
        .collect(Collectors.toSet());

// 回调内
sseBuffer.append(token);
flushSseBuffer(emitter, sseBuffer, validIds);

// onComplete
emitTokens(emitter, sseBuffer.toString());
sseBuffer.setLength(0);
```

```java
// 方法签名改为接收局部状态
private void flushSseBuffer(SseEmitter emitter, StringBuilder sseBuffer, Set<String> validIds)
        throws java.io.IOException { ... }
```

并**删除**成员变量 `sseBuffer`、`currentValidIds`。

> 原理：Spring 单例 Bean 只应持有**无状态的依赖**（`final` 注入的 Service/Config）。任何「随请求变化」的状态必须放在**方法局部变量 / 方法参数 / ThreadLocal / `@RequestScope` Bean** 里。

### 验证

- **回归测试** `ChatServiceConcurrencyTest`：用 Mockito 把 LLM 换成可控假流式（A 线程吐 "A"、B 线程吐 "B"，
  各 300 次 × 300 字符），`CyclicBarrier` 强制两路同时流式，自定义 `CapturingEmitter` 捕获每一帧。
  - 修复前：第 1 轮即复现，`A帧含B内容=true | B帧含A内容=true` → 测试失败
  - 修复后：5 轮全部 `含B内容=false | 含A内容=false` → 测试通过
- 原有单轮对话 / 检索评测（`RetrievalEvaluationTest`）通过
- 日志中 `flushSseBuffer` 的 buffer 内容只属于当前 session

### 修复提交

- `ChatService`：删除成员变量 `sseBuffer` / `currentValidIds`；在 `chatStream` 内声明局部
  `StringBuilder sseBuffer`；`flushSseBuffer(emitter, sseBuffer, validIds)` 改为传参。

---

## 同类复查结论（2026-09-10 全项目扫描）

扫描了 `com.ragagent` 下所有非 static 字段，判断哪些是「单例里的可变请求级状态」：

| 字段 | 位置 | 结论 |
|------|------|------|
| `sseBuffer`, `currentValidIds` | `ChatService` | ❌ **BUG-001** |
| `gson` | `DataImportService` / `EmbeddingService` / `KnowledgeIndexer` / `RetrieverService` | ✅ 线程安全（Gson 实例并发安全） |
| `sessions` (`ConcurrentHashMap`) | `SessionService` | ✅ 有意共享的会话存储，线程安全 |
| `encoder` (`BCryptPasswordEncoder`) | `UserService` | ✅ 线程安全 |
| `textCollectionId` / `imageCollectionId` | `RetrieverService` | ✅ 幂等懒加载缓存，无请求级差异 |
| `pythonProcess` | `EmbeddingServiceManager` | ✅ 生命周期字段，非请求级 |
| `@Value` 注入字段（`apiKey` 等） | 多个类 | ✅ 启动注入后只读 |
| 模型 DTO 字段（`Product`/`Order` 等） | `model/*` | ✅ 每个实例独立，非共享 |

**预防清单**：
- 单例 Bean（`@Service`/`@Controller`/`@Component`）**不要**用可变成员变量保存请求级状态
- 流式回调（`onNext`/`onComplete`）里的累加器用**局部变量**，别提升成字段
- Code Review 时重点看：`StringBuilder` / `Set` / `List` / `Map` 等**非 static 非 final-injected** 字段

---

## BUG-002: Web 前端未维护 conversationId，历史无法恢复（Android 端已有对照实现）

- **发现时间**：2026-09-26
- **严重级别**：一般（Web 端功能缺失，不影响安全）
- **状态**：✅ 已修复（2026-09-26）
- **相关文件**：`web/src/components/agent/AgentDock.tsx`（问题所在）、`client/app/src/main/java/com/ragagent/viewmodel/ChatViewModel.kt` + `HistoryActivity.kt`（Android 端正确实现，可作对照）

### 症状

- **Web 端**：登录用户与「小买」的对话，**刷新页面 / 重新打开浮窗后聊天窗口全空**，历史无法找回。
- **Web 端**：每发一句都会在 `conversations` 表**新建一个会话**（因为 `conversationId` 永远传 `undefined`）。
  > 注：当前库里 `conversations` 多为 2 / 4 / 6 / 8… 条消息的**多轮**会话，是 **Android 端**产生的（Android 端会复用 conversationId）。Web 端一旦被使用即会产生碎片会话。
- 「会话历史关键词搜索」（`GET /api/conversations?q=`，提交 `909bee4`）在 **Web 端无法使用**（既无入口，也不传 conversationId）。

### 根因

后端长期记忆**已完整实现**（`conversations`/`messages` 表 + `ConversationService` + `ConversationController`，登录用户每轮落库）；**Android 端也已正确接入**（`ChatViewModel` 维护 `conversationId`、接收 `done` 事件回写、`HistoryActivity` 提供历史列表）。**唯独 Web 前端从未把 `conversationId` 用起来**：

> **更根本的缺陷**：`AgentDock` 给 SSE 传 `token: undefined` → 请求**不带 `Authorization` 头** → 后端 `JwtAuthFilter.getUserId(request)` 返回 null → **Web 端登录用户连落库都没发生**（`ChatService` 仅在 `userId != null` 时持久化）。所以 Web 端历史问题实际有**两个**：① 不传 token（根本不落库）② 不维护 conversationId（即便落库也会碎片化）。

1. `AgentDock.tsx:100` 每次发送都硬编码 `conversationId: undefined`：
   ```ts
   sseChat({ message: text, sessionId, conversationId: undefined, token: undefined }, ...)
   ```
2. 后端 `ConversationService.getOrCreate(userId, conversationId, ...)` 在 `conversationId == null` 时**每次都新建会话**：
   ```java
   if (conversationId != null) { /* 找已存在 */ }
   String cid = conversationId != null ? conversationId : UUID.randomUUID().toString();
   conversationRepo.create(userId, cid, title);   // 每轮都走这里
   ```
3. `done` 事件其实会返回 `conversationId`（`ChatService:265`），但 `AgentDock` 的 `done` 分支**没有接收**，下一轮依旧是 `undefined`。
4. 前端 `messages` 仅存于组件 state，刷新即丢；`conversationApi`（列表/读消息，`lib/api.ts:112`）已写好但**无人调用**，也没有加载历史的 UI。

### 复现

1. 在 **Web 端**登录，向小买发 3 条消息。
2. 刷新页面 → 聊天窗口为空（历史丢失）。
3. 查库：`SELECT conversation_id, COUNT(*) FROM messages GROUP BY conversation_id;` → 这 3 条消息落在 **3 个不同**的会话里（每次发送都新建）。

### 影响

- **Web 端**用户：无历史记忆，每次刷新 / 重开都是全新窗口（Android 端正常）。
- 数据侧：Web 端产生的会话碎片化（每轮一条），与 Android 端数据混杂。
- 已有功能（会话搜索 `909bee4`）在 Web 端不可用。
- **多端体验不一致**：同一账号，Android 有历史、Web 没有。

### 解决方案（已实施，2026-09-26）

后端与 Android 端均已就绪，只补了 Web 端：

1. **SSE 带上 token**：`AgentDock` 从 `useAuthStore` 取 token 传给 `sseChat` → 后端拿到 `userId`，恢复落库。
2. **conversationId 持久化**：新增 `lib/conversation.ts`（localStorage），发送时带上；接收 `done` 的 `conversationId` 回写。
3. **会话列表抽屉**：新增 `ConversationHistory.tsx`（列表 / 关键词搜索防抖 / 删除），复用 `conversationApi`。
4. **加载历史**：选择会话 → 拉 `messages` → `mapHistory()` 映射气泡（`productIds` 反查商品卡片 + `stripChatTags()` 去结构化标签）。
5. **新对话按钮** + 登录/登出清理会话 ID（防跨用户串会话）。
6. （进阶未做）打开会话时把 DB 最近 N 条 rehydrate 回 `SessionService`，实现跨会话"记忆"。

### 验证

- 注册测试用户 `webhistory_test`（密码 `test1234`）→ **带 token** 连续两轮对话：
  - 两轮 `done` 返回**同一个** `conversationId`（`e67515e1-...`）→ 不再碎片化。
  - `GET /api/conversations` → 1 个会话；`GET /api/conversations/{cid}/messages` → **4 条**（2 轮 user+ai）。
  - AI 消息 `productIds=["p_beauty_007"]` 正确落库 → 历史可还原商品卡片。
- `npm run build` 通过。

---

## BUG-003: 数据库 Schema 漂移——仓库 DDL 与线上真实结构不一致

- **发现时间**：2026-09-26
- **严重级别**：严重（新环境无法启动、项目不可复现）
- **状态**：✅ 已修复（2026-09-26，Flyway 版本化迁移）
- **相关文件**：`data/init.sql`、`ConversationRepository.java`、`OrderRepository.java`、`UserBehaviorRepository.java`、`docs/Day10-用户认证系统.md`

### 症状

- 全新环境**只按仓库里的 DDL 建库**后，应用无法正常使用：注册/登录报错（`users` 表不存在）。
- 线上 `cart_items` 有 `sku_id` / `sku_label` / `user_id` 三列，但 `init.sql` 里**没有** → 用 `init.sql` 重建后，加购/购物车直接 SQL 报错（Unknown column）。
- 同一批表的定义分散在四处，互相矛盾，且无任何版本记录。

### 根因（DDL 分散 + 手工改线上）

| 表 | DDL 位置 | 与线上是否一致 |
|---|---|---|
| `products` / `product_skus` / `product_faqs` / `product_reviews` | `data/init.sql`（Day0-1 后未再更新） | 一致 |
| `cart_items` | `init.sql` 仅 5 列 | ❌ 线上多 3 列（手工 `ALTER` 加的） |
| `users` | **仅存在于 `docs/` 文档** | ❌ 无可执行 DDL |
| `conversations` / `messages` | Java `@PostConstruct`（`ConversationRepository`） | 一致（运行时建） |
| `orders` | Java `@PostConstruct`（`OrderRepository`） | 一致（运行时建） |
| `user_behaviors` | Java `@PostConstruct`（`UserBehaviorRepository`） | 一致（运行时建） |

即 **「仓库里描述的结构」≠「线上真实结构」**：有人直接在线上 MySQL 手工改列/建表，没同步回仓库；`init.sql` 早就是过期快照。

> 术语：**Schema 漂移（Schema Drift）** —— 数据库真实结构与代码/版本库中定义的结构发生分歧。

### 复现

1. 找一台干净 MySQL，**只执行 `data/init.sql`**。
2. 启动应用 → 注册/登录失败（`users` 不存在）；加购失败（`cart_items.sku_id` 不存在）。

### 影响

- **项目不可复现**：新机器 / 面试官 clone 后跑不起来（对作品集项目是硬伤）。
- 结构变更无审计、无版本；多人协作易互相覆盖。
- 是后续所有功能（admin 需加 `users.role`、监控、历史对话）的**地基风险**——在漂移上继续盖只会更乱。

### 解决方案（已实施，2026-09-26）

采用 **Flyway 版本化迁移**：

1. **以线上为唯一真相**：用 `SHOW CREATE TABLE` 导出线上 10 张表的真实结构。
2. 落成基线脚本 `server/src/main/resources/db/migration/V1__baseline.sql`（按外键依赖排序：`products` → `product_skus`/`product_faqs`/`product_reviews`/`cart_items`）。
3. `pom.xml` 引入 `flyway-core` + `flyway-mysql`（版本由 Spring Boot 父 POM 管理，9.22.3）。
4. `application.yml` / `.example` 配置 `spring.flyway`（`baseline-on-migrate: true`、`baseline-version: 1`）。
5. **删除 Java 建表逻辑**：`ConversationRepository` / `UserBehaviorRepository` 的 `@PostConstruct initTables()`、`OrderRepository.ensureTableExists()` 及 `OrderController` 中的调用。
6. `data/init.sql` 瘦身为"仅建库"，建表交给 Flyway。

### 验证

- **线上库（已有数据）**：启动后日志 `Successfully baselined schema with version: 1` → `Schema is up to date. No migration necessary.`（**V1 未执行，数据无损**）；`flyway_schema_history` 写入 `(rank=1, version='1', description='baseline', type=BASELINE, success=1)`。
- **数据无损核对**：`products=100 / users=11 / conversations=29 / messages=168 / orders=3 / cart_items=12 / user_behaviors=173`。
- **接口冒烟**：`GET /api/products`、`POST /api/cart/add`、`GET /api/cart`、`GET /api/chat/stream` 均通过。
- **全新空库（待补验）**：`rag_agent` 用户仅有 `rag_agent.*` 权限、无 `CREATE DATABASE`，暂无法在干净库上实跑 V1。脚本内容逐字取自线上 `SHOW CREATE TABLE`，语法可靠；后续可借本地 MySQL / Docker 补验。

---

## 模板

```markdown
## BUG-XXX: 标题

- **发现时间**：YYYY-MM-DD
- **严重级别**：致命 / 严重 / 一般 / 轻微
- **状态**：待修复 / 修复中 / 已修复
- **相关文件**：`path/to/File.java`

### 症状
（现象、报错信息、出现条件）

### 根因
（代码/配置层面的原因，贴关键代码）

### 复现
（最小复现步骤）

### 影响
（影响范围与后果）

### 解决方案
（改动点 + 原理）

### 验证
（如何确认修好了）
```
