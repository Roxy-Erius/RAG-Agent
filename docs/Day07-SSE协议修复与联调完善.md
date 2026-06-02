# Day 07 — SSE 协议修复与联调完善

## 一、SSE 格式标准化（按技术路线 2.5 节）

### 1.1 问题

后端实际发送原始文本 token（`data:推`、`data:[PRODUCT:p_xxx]`、`data:[DONE]`），与技术路线规定的结构化 JSON 不一致。且 `[PRODUCT:id]` 被 LLM token 化拆成 11 个碎片逐个发送，前端无法识别完整标记。

### 1.2 后端修复（ChatService.java）

**核心思路：** 累积 LLM token 到 `sseBuffer`，检测完整 `[PRODUCT:id]` 后分类发送。

```
修复前:
  onNext("推") → emitter.send("推")

修复后:
  onNext("推") → sseBuffer.append("推") → flushSseBuffer()
    ├─ 检测到完整 [PRODUCT:id] → 发送 {"type":"product","productId":"..."}
    ├─ 检测到 [DONE] → 发送 {"type":"done"}
    ├─ 检测到不完整标签前缀 → 保留在缓冲等待后续 token
    └─ 普通文本 → 发送 {"type":"token","content":"..."}
```

**关键实现：**
- `sseBuffer`（StringBuilder）累积所有 LLM 输出
- `flushSseBuffer()` 每收到新 token 调用一次，正则匹配完整标签
- 不完整标签（如 `[PRODUCT:p_digital_007` 缺少 `]`）保留在缓冲，等后续 token 拼接
- `emitTokens()` 整段发送文本 token（非逐字拆分，避免事件数量爆炸）

**踩坑：** `possibleTag.length() < 15` 条件把长商品 ID（22 字符）的未闭合标签误判为普通文本发出，导致 product 事件永远触发不了。

### 1.3 前端简化（SseClient.kt + ChatViewModel.kt）

**修复前（~90 行）：**
```
parseEvents() 用正则从原始文本中切割 Token/ProductRef/Done
ChatViewModel 在 Done 时从累积文本提取 [PRODUCT:id] 标记
```

**修复后（~30 行）：**
```kotlin
parseEvent(data) → Gson.fromJson(data, SseEventDto) → toSseEvent()
// 直接映射 {"type":"token/product/done",...} → SseEvent
```

ChatViewModel 删除 `PRODUCT_TAG_REGEX` 和 Done 时的文本提取逻辑，ProductRef 作为内联事件直接到达。

---

## 二、文字丢失问题排查

### 2.1 事件丢弃

**现象：** AI 回复文字只显示一部分，后半段消失。

**原因：** `callbackFlow` 默认缓冲区 64。后端 JSON 事件量大时，`trySend()` 静默丢弃溢出事件。

**修复：** 加 `buffer(Channel.UNLIMITED)`，事件绝不丢弃。

### 2.2 商品卡片后文字覆盖

**现象：** 卡片前的文字显示正常，卡片后的文字消失。

**原因：** ProductRef 处理中 `aiText = ""` 把累积文字清空，后续 Token 用空文本覆盖 Ai 气泡。

```
修复前:
  ProductRef → aiText=""     ← 清空
  Token("售价") → aiText="售价" → updateLastAiMessage("售价") → 覆盖原文

修复后:
  ProductRef → 不碰 aiText
  Token("售价") → aiText="这款耳机很适合你售价" → updateLastAiMessage(完整文本)
```

**修复：** 去掉 `aiText = ""` 和 `finalizeLastAiMessage()`，卡片插入后文字继续追加当前气泡。

---

## 三、UI 细节修复

### 3.1 用户气泡尾巴方向

**问题：** 用户气泡尖角在左下角（bottomLeftRadius=6dp），应指向用户（右下角）。

**修复：** 交换 bottomLeftRadius 和 bottomRightRadius：
```xml
<!-- 修复前 -->
android:bottomRightRadius="22dp"
android:bottomLeftRadius="6dp"   ← 尖在左下 ✗

<!-- 修复后 -->
android:bottomRightRadius="6dp"  ← 尖在右下 ✓
android:bottomLeftRadius="22dp"
```

---

## 四、后端稳定性

### 4.1 MySQL 连接超时

**问题：** 空闲一段时间后请求报 `ConnectionIsClosedException`。

**原因：** HikariCP 连接池未配置心跳，MySQL 服务端超时断开连接。

**修复：** `application.yml` 加 HikariCP 配置：
```yaml
hikari:
  max-lifetime: 300000        # 5 分钟换新连接
  keepalive-time: 120000      # 2 分钟心跳保活
  connection-timeout: 10000
  validation-timeout: 5000
```

### 4.2 检索相似度阈值

**问题：** 宽泛查询（"推荐美容护肤产品"）返回空结果，精确查询（"推荐面霜"）正常。

**原因：** `similarity-threshold: 0.45` 对 100 条小数据集过于严苛，宽泛查询向量距离偏大被过滤。

**修复：** `similarity-threshold: 0.0`，不做阈值过滤，始终返回 Top-3。

---

## 五、版本管理

将 Android 项目的基础文件纳入 Git 跟踪：
- 构建脚本：`build.gradle.kts` ×2、`settings.gradle.kts`、`gradle.properties`、`proguard-rules.pro`
- 清单：`AndroidManifest.xml`
- Kotlin 源码：`RagAgentApp.kt`、`MainActivity.kt`、`ProductCardActivity.kt`、`model/*.kt`
- 资源：`strings.xml`、`edittext_bg.xml`、`ic_launcher_foreground.xml`、`mipmap/`、`xml/`

共 17 个文件从 `??` 变为已跟踪。

---

## 六、改动文件清单

```
server/src/main/java/com/ragagent/service/
└── ChatService.java              (修改：SSE 缓冲发送、结构化 JSON)

server/src/main/resources/
└── application.yml               (修改：HikariCP、相似度阈值)

client/app/src/main/java/com/ragagent/
├── network/
│   └── SseClient.kt              (修改：Gson JSON 解析、unlimited buffer)
├── viewmodel/
│   └── ChatViewModel.kt          (修改：删除 Done 文本提取、修复文字覆盖)
└── res/drawable/
    └── bubble_user.xml           (修改：尾巴方向)

client/ (17 个文件纳入版本管理)
```
