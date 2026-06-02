# Day 04 — 防幻觉优化 + RAG 参数调优

## 一、Day 04 目标

| 角色 | 任务 | 说明 |
|------|------|------|
| B 同学 | 全局异常管理器 | 统一处理各类异常，返回规范 JSON |
| A 同学 | RAG 防幻觉优化 | 商品 ID 注入 + 后处理校验，杜绝 LLM 编造商品 |
| A 同学 | RAG 参数调优 | Top-K 可配置、相似度阈值过滤、Prompt 推荐质量提升 |

---

## 二、B 同学 — 全局异常管理器

### 2.1 问题

没有统一异常处理时，Spring Boot 默认返回 HTML 错误页面或裸 stack trace，前端无法解析。

### 2.2 实现

**文件**: `config/GlobalExceptionHandler.java`

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // JSON 解析失败 → 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadBody(...) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "请求体格式错误，请检查 JSON 是否正确",
            "status", 400, "timestamp", LocalDateTime.now().toString()));
    }

    // 参数类型错误 → 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleBadParam(...) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "参数 '" + e.getName() + "' 格式错误",
            "status", 400, "timestamp", LocalDateTime.now().toString()));
    }

    // 缺少必填参数 → 400
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(...) { ... }

    // 非法参数 → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(...) { ... }

    // 其他未处理异常 → 500（隐藏内部细节）
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(500).body(Map.of(
            "error", "服务器内部错误，请稍后重试",
            "status", 500, "timestamp", LocalDateTime.now().toString()));
    }
}
```

### 2.3 覆盖的异常类型

| 异常 | HTTP | 示例触发场景 |
|------|------|-------------|
| `HttpMessageNotReadableException` | 400 | POST body 不是合法 JSON |
| `MethodArgumentTypeMismatchException` | 400 | `?topK=abc` 参数类型不对 |
| `MissingServletRequestParameterException` | 400 | 缺少 `?message=` 必填参数 |
| `IllegalArgumentException` | 400 | 业务逻辑参数校验失败 |
| `Exception`（兜底） | 500 | 数据库断连、LLM 调用失败等 |

### 2.4 设计原则

- **对外隐藏内部错误细节**：500 统一返回 "服务器内部错误"，不泄露 stack trace
- **对内保留排查能力**：通过 `log.error("未处理异常", e)` 记录完整堆栈
- **统一 JSON 格式**：`{error, status, timestamp}` 方便前端统一处理

---

## 三、A 同学 — RAG 防幻觉优化

### 3.1 幻觉问题的根源

Day 03 上线后发现 LLM 偶尔会编造商品 ID，例如把 `p_digital_007` 写成 `p_digi_001`。

根本原因在 `formatProducts()` 方法——它传给 LLM 的文本里**没有包含真正的 productId**：

```java
// Day 03 版本（有问题）
sb.append(String.format("%d. %s | %s | %s | %.0f元\n   %s\n",
        i + 1,
        p.getTitle(),     // ← 只有标题、品牌、类目、价格
        p.getBrand(),
        p.getCategory(),
        p.getBasePrice(),
        truncate(p.getMarketingDescription(), 100)));
// LLM 看不到 productId，只能凭记忆"猜" → 经常猜错
```

### 3.2 防幻觉三层防线

```
LLM 生成回复
    │
    ▼
┌─────────────────────────────────────────────┐
│  第一层：Prompt 约束                         │
│  "推荐商品时必须使用下方准确的商品ID"          │
│  明确告诉 LLM 从哪里复制 ID                   │
└─────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────┐
│  第二层：商品文本注入真实 ID                  │
│  formatProducts() 输出格式改为：              │
│  "1. [ID:p_beauty_007] 薇诺娜面霜 | ..."     │
│  LLM 可以直接看到并复制真实 ID               │
└─────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────┐
│  第三层：sanitizeProductTags() 后处理校验    │
│  提取回复中所有 [PRODUCT:id] 标签            │
│  → 比对检索结果中的 validIds                │
│  → 不存在的 ID → 移除 + 记录 warn 日志       │
└─────────────────────────────────────────────┘
    │
    ▼
最终回复（ID 100% 真实）
```

### 3.3 第一层 — System Prompt 强化

```diff
- ## 输出格式
+ ## 输出格式（重要！）
- - 当推荐具体商品时，在商品描述后插入标记：[PRODUCT:id]
-   例如：这款洗面奶非常适合油皮使用 [PRODUCT:p_beauty_001]，价格也很实惠。
+ - 推荐商品时必须使用下方准确的商品ID，格式为 [PRODUCT:商品ID]
+ - 示例：这款洗面奶非常适合油皮使用 [PRODUCT:p_beauty_001]，价格也很实惠。
```

关键改动：从"插入标记"改为"必须使用准确的商品ID"，语气更强制。

### 3.4 第二层 — formatProducts 注入真实 ID

**文件**: `service/ChatService.java`

```java
// Day 04 版本（修复后）
sb.append(String.format("%d. [ID:%s] %s | %s | %s | %.0f元\n   %s\n",
        i + 1,
        p.getProductId(),  // ← 新增！LLM 能看到真实 ID 并直接复制
        p.getTitle(),
        p.getBrand(),
        p.getCategory(),
        p.getBasePrice(),
        truncate(p.getMarketingDescription(), 100)));
```

现在发送给 LLM 的商品文本变成：

```
1. [ID:p_beauty_007] 薇诺娜舒敏保湿特护霜 | 薇诺娜 | 美妆护肤 | 268元
   专为敏感肌打造，含马齿苋提取物舒缓泛红干痒...

2. [ID:p_beauty_012] 理肤泉特安舒缓修复霜 | 理肤泉 | 美妆护肤 | 260元
   专为干性敏感肌打造，添加高矿温泉水+神经酰胺...
```

LLM 看到 `[ID:p_beauty_007]` → 回复时直接复制 → 不再编造。

### 3.5 第三层 — sanitizeProductTags 后处理

**文件**: `service/ChatService.java`

```java
/**
 * 防幻觉后处理：移除 LLM 编造的不存在的 [PRODUCT:id] 标签
 */
private String sanitizeProductTags(String reply, Set<String> validProductIds) {
    Matcher matcher = PRODUCT_TAG_PATTERN.matcher(reply);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
        String id = matcher.group(1);
        if (validProductIds.contains(id)) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
        } else {
            log.warn("检测到幻觉 Product ID: {}，已从回复中移除", id);
            matcher.appendReplacement(sb, "");  // 移除编造的标签
        }
    }
    matcher.appendTail(sb);
    return sb.toString();
}
```

**校验逻辑：**
1. 用正则 `\[PRODUCT:(\w+)\]` 提取回复中所有商品标签
2. 逐一比对 `validProductIds`（检索结果中的真实 ID 集合）
3. 真实 ID → 保留原样
4. 编造 ID → 移除标签 + 记录 warn 日志

**集成到两个对话路径：**

```java
// 流式路径 chatStream() — onComplete 回调中
String reply = sanitizeProductTags(fullResponse.toString(), validIds);

// 非流式路径 chat() — onComplete 回调中
String reply = sanitizeProductTags(fullResponse.toString(), validIds);
```

### 3.6 效果对比

| 指标 | Day 03（无防幻觉） | Day 04（三层防线） |
|------|-------------------|-------------------|
| 商品 ID 准确率 | ~70%（LLM 有时猜错） | 100%（直接复制 + 后处理兜底） |
| 幻觉检测 | 无 | 自动移除 + warn 日志告警 |
| 回退策略 | 无 | 编造标签被静默移除，不影响阅读 |

---

## 四、A 同学 — RAG 参数调优

### 4.1 配置可管理化

**问题：** Top-K 和相似度阈值硬编码在代码里，调参需要改代码+重编译。

**解决：** 新建 `RagConfig` 配置类，从 yml 动态读取。

**文件**: `config/RagConfig.java`

```java
@Component
@ConfigurationProperties(prefix = "rag")
public class RagConfig {
    private int topK = 3;                    // 检索返回数量
    private double similarityThreshold = 0.5; // 相似度阈值
    private int maxContextMessages = 10;      // 最大对话历史条数
}
```

**对应 yml 配置**（`application.yml`）：

```yaml
rag:
  top-k: 3
  similarity-threshold: 0.5
  max-context-messages: 10
```

### 4.2 Top-K 可配置化

**文件**: `service/ChatService.java`

```diff
- private static final int RETRIEVAL_TOP_K = 3;
+ // 注入 RagConfig，通过 ragConfig.getTopK() 动态读取

- retrieverService.retrieveProductsByText(userMessage, RETRIEVAL_TOP_K, null);
+ retrieverService.retrieveProductsByText(userMessage, ragConfig.getTopK(), null);
```

好处：修改 `application.yml` 中 `rag.top-k: 5` → 重启即生效，无需改代码。

### 4.3 相似度阈值过滤

**问题：** 检索会返回所有结果，即使相似度很低（如 score=0.001 的完全不相关商品）。

**解决：** 在三个检索方法中加入 `score >= similarityThreshold` 过滤。

**文件**: `service/RetrieverService.java`

```java
double threshold = ragConfig.getSimilarityThreshold();

// retrieveByText() — 文本检索
return results.stream()
        .filter(r -> r.getScore() >= threshold)  // ← 新增过滤
        .sorted(Comparator.comparingDouble(ScoredResult::getScore).reversed())
        .limit(topK)
        .map(r -> r.productId)
        .toList();

// retrieveProductsByText() — 文本检索 + DB 联查
// retrieveByImage() — 图片检索
// 同样加入 .filter(r -> r.getScore() >= threshold)
```

**score 的含义：** ChromaDB 返回的是 cosine distance，score = 1.0 - distance。阈值 0.5 意味着只保留与查询向量相似度 ≥ 0.5 的结果。

### 4.4 Prompt 推荐技巧强化

**文件**: `service/ChatService.java`

新增 `## 推荐技巧` section：

```
## 推荐技巧
- 结合商品的实际卖点和用户需求，给出1-2句有说服力的推荐理由
- 优先推荐与用户需求最匹配的商品，而非简单罗列
- 如果有多款合适的商品，简要对比它们的关键差异
- 提及价格时结合性价比做出评价
```

**效果对比：**

| 维度 | 优化前 | 优化后 |
|------|--------|--------|
| 推荐理由 | "这款面霜不错" | "专为干性敏感肌打造，高浓度温泉水+神经酰胺，长效保湿修护屏障" |
| 多商品处理 | 简单罗列 | 分场景对比："华为适合通勤办公，Apple适合苹果生态用户" |
| 价格表述 | "售价268元" | "售价268元，性价比很高" |
| 预算约束 | 忽略 | 诚实告知"两款耳机均超1500预算" |

---

## 五、能力全景评估

Day 04 结束后，对 8 个典型场景做了实测评估：

| 难度 | 场景 | 结果 | 说明 |
|------|------|------|------|
| 基础 | 单轮模糊推荐 | ✅ | "油皮洗面奶" → 精准推荐珊珂 |
| 基础 | 条件筛选 | ⚠️ | 价格过滤靠 LLM 判断，检索层不支持结构化过滤 |
| 进阶 | 多轮追问 | ❌ | 第二轮"要轻量的"检索漂移，丢失跑鞋上下文 |
| 进阶 | 对比决策 | ❌ | 不支持按商品 ID 精确检索 |
| 进阶 | Agent 主动反问 | ❌ | Prompt 约束力不足，LLM 直接推荐而非反问 |
| 高级 | 反选/排除 | ✅ | "不要酒精+不要日系" → 正确推荐理肤泉（法国品牌） |
| 高级 | 场景组合推荐 | ❌ | 跨类目检索命中率低 |
| 高级 | 拍照找货 | ❌ | `retrieveByImage` 已实现但未接入 ChatService |

**当前定位：基础层稳定，进阶层三个场景全部受限于检索瓶颈。**

---

## 六、文件变更清单

```
server/src/main/java/com/ragagent/
├── config/
│   ├── GlobalExceptionHandler.java  ← B 同学新建
│   └── RagConfig.java               ← A 同学新建
└── service/
    ├── ChatService.java             ← 修改（防幻觉 + Prompt + RagConfig）
    └── RetrieverService.java        ← 修改（相似度阈值过滤）

server/src/test/java/com/ragagent/
└── RetrievalEvaluationTest.java     (无变更，评测仍通过)
```

---

## 七、Day 04 Commit 记录

| Commit | 内容 |
|--------|------|
| `760f69f` | 添加全局异常管理器 |
| `35ff7e3` | Day 04: RAG 防幻觉优化 — 商品ID注入 + 后处理校验 |
| `b2f7d15` | Day 04 补充：RAG 参数可配置化 + 相似度阈值过滤 + Prompt 推荐技巧优化 |
