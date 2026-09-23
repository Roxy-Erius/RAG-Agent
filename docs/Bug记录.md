# RagAgent Bug 记录

> 记录项目开发中遇到的 Bug：症状 / 根因 / 影响 / 解决方案 / 验证。
> 新增条目请复制末尾「模板」。

## 目录

| 编号 | 标题 | 严重级别 | 状态 |
|------|------|:--------:|:----:|
| [BUG-001](#bug-001-chatservice-用成员变量保存请求级状态导致-sse-并发串流) | ChatService 用成员变量保存请求级状态，导致 SSE 并发串流 | 严重 | ✅ 已修复 |

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
