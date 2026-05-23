# Day 03 — RAG 链路 + SSE 流式对话接口

## 一、RAG 原理

### 1.1 什么是 RAG

RAG = **Retrieval-Augmented Generation**（检索增强生成）。

一句话：**先查资料，再回答问题**。

类比：一个导购员接到顾客问"推荐一款面霜"，他会先翻商品手册找到相关商品，再根据商品信息回答顾客。而不是凭空编造。

### 1.2 为什么需要 RAG

直接让 LLM 回答"推荐一款保湿面霜"会有两个问题：

| 问题 | 表现 |
|------|------|
| **知识缺失** | LLM 的训练数据里没有你的商品库，它不知道你有哪些商品 |
| **幻觉（Hallucination）** | LLM 会编造不存在的商品名称、价格、规格 |

RAG 的解决方案：
1. 先从你的商品库（ChromaDB）里检索真实商品
2. 把检索到的商品信息塞进 prompt 里当"参考资料"
3. LLM 基于真实商品信息生成回答 → 不会编造

### 1.3 RAG 三步流程

```
用户: "推荐一款敏感肌用的面霜"
        │
        ▼
┌──────────────────────────────────────────────────────────┐
│  ① Retrieval（检索）                                      │
│  RetrieverService.retrieveByText("敏感肌面霜", 3)         │
│                                                          │
│  用户消息 → EmbeddingService.embedText() → 2048维向量     │
│  → ChromaDB cosine 检索 → Top-3 商品                      │
│  → 返回：薇诺娜面霜(p_beauty_007)、理肤泉面霜(p_beauty_012)│
└──────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────┐
│  ② Augmentation（增强 — 拼装 Prompt）                      │
│                                                          │
│  把 Top-3 商品信息格式化成文本，塞进 System Prompt 的       │
│  {context} 占位符位置：                                    │
│                                                          │
│  "你是一个电商导购助手...                                  │
│   ## 商品库                                               │
│   1. 薇诺娜舒敏保湿特护霜 | 薇诺娜 | 美妆护肤 | 268元     │
│      专为敏感肌打造，含马齿苋提取物...                      │
│   2. 理肤泉特安舒缓修复霜 | 理肤泉 | 美妆护肤 | 260元      │
│      专为干性敏感肌打造..."                                │
└──────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────┐
│  ③ Generation（生成 — 调用 LLM）                           │
│                                                          │
│  把 System Prompt + 对话历史 + 用户消息 一起发给 Doubao LLM │
│  LLM 基于"商品库"中的真实信息生成推荐回答                    │
│                                                          │
│  → "推荐薇诺娜舒敏保湿特护霜 [PRODUCT:p_beauty_007]，      │
│     专为敏感肌设计，含马齿苋提取物舒缓泛红干痒..."          │
└──────────────────────────────────────────────────────────┘
        │
        ▼
前端/Android 收到回答 + [PRODUCT:p_beauty_007] 标记 → 展示商品卡片
```

### 1.4 关键设计：为什么不让 LLM 自己推荐？

| 方式 | 有 RAG | 没有 RAG |
|------|--------|----------|
| 商品信息 | 来自你的商品库（100% 真实） | LLM 凭空编造 |
| 价格 | 真实价格 | 可能编造 |
| 推荐理由 | 基于商品实际特点 | 泛泛而谈 |
| 幻觉风险 | 极低（有参考资料约束） | 很高 |

---

## 二、技术实现

### 2.1 组件架构

```
server/src/main/java/com/ragagent/
├── config/
│   └── LangChain4jConfig.java    ← 配置 Doubao ChatModel Bean
├── service/
│   ├── EmbeddingService.java     (已有) 文本/图片向量化
│   ├── RetrieverService.java     (已有) 向量检索 + RRF 融合
│   ├── ChatService.java          ← 新建 RAG 核心编排
│   └── SessionService.java       ← 新建 多轮对话历史
├── controller/
│   └── ChatController.java       ← 新建 SSE + 非流式接口
└── model/
    ├── ChatRequest.java          ← 新建 请求 DTO
    └── ChatResponse.java         ← 新建 响应 DTO
```

### 2.2 LangChain4j 配置

**文件**: `config/LangChain4jConfig.java`

```java
@Bean
public OpenAiStreamingChatModel streamingChatModel() {
    return OpenAiStreamingChatModel.builder()
            .baseUrl("https://ark.cn-beijing.volces.com/api/v3")  // Doubao API
            .apiKey("ark-xxx")       // 赛方提供的 chat key
            .modelName("ep-20260514111645-lmgt2")  // 火山引擎 endpoint ID
            .temperature(0.7)        // 生成随机性（0=确定性，1=随机）
            .maxTokens(1024)         // 最大生成 token 数
            .build();
}
```

**为什么用 LangChain4j 而不是直接 HttpClient？**
- LangChain4j 的 `OpenAiStreamingChatModel` 已经封装了 SSE 流式解析
- 直接用 HttpClient 需要自己解析 SSE 的 `data:` 行和 `[DONE]` 终止符
- LangChain4j 兼容 OpenAI 格式，Doubao API 也是 OpenAI 格式，天然适配

### 2.3 ChatService — RAG 核心

**文件**: `service/ChatService.java`

核心方法 `chatStream()` 的流程：

```java
public void chatStream(String sessionId, String userMessage, SseEmitter emitter) {
    // ① Retrieval: 检索相关商品
    List<ProductSearchResult> products = retrieverService.retrieveByText(userMessage, 3);

    // ② Augmentation: 拼装上下文
    String context = formatProducts(products);
    String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(context);

    // ③ 构建消息列表
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(SystemMessage.from(systemPrompt));   // 系统提示（含商品库）
    messages.addAll(sessionService.getHistory(sessionId)); // 对话历史
    messages.add(UserMessage.from(userMessage));       // 用户最新消息

    // ④ Generation: 调用 LLM 流式生成
    streamingChatModel.generate(messages, new StreamingResponseHandler<>() {
        public void onNext(String token) {
            emitter.send(SseEmitter.event().data(token));  // 每个 token 推送给前端
        }
        public void onComplete(Response<AiMessage> response) {
            sessionService.addUserMessage(sessionId, userMessage);
            sessionService.addAiMessage(sessionId, fullResponse);
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        }
        public void onError(Throwable error) {
            emitter.completeWithError(error);
        }
    });
}
```

**消息结构示意：**

```
发给 LLM 的完整消息列表：
┌─────────────────────────────────────────────┐
│ SystemMessage (系统提示)                      │
│ "你是一个电商导购助手...                       │
│  ## 商品库                                    │
│  1. 薇诺娜面霜 268元...                       │
│  2. 理肤泉面霜 260元..."                      │
├─────────────────────────────────────────────┤
│ UserMessage (历史)    "推荐面霜"              │
│ AiMessage (历史)      "推荐薇诺娜..."         │
├─────────────────────────────────────────────┤
│ UserMessage (当前)    "有没有更便宜的？"       │
└─────────────────────────────────────────────┘
```

### 2.4 System Prompt 设计

```
你是一个专业的电商导购助手。你的职责是根据用户需求，从商品库中推荐合适的商品。

## 严格规则
1. 你只能推荐下方【商品库】中列出的商品，绝对不能推荐不存在的商品
2. 你不能编造商品的价格、规格、优惠信息等任何参数
3. 如果商品库中没有匹配的商品，请如实告知用户，不要编造
4. 推荐时请给出具体理由，结合商品的实际特点
5. 回复要简洁专业，适合移动端阅读，控制在200字以内
6. 当用户需求模糊时，主动提问引导用户细化需求

## 输出格式
- 正常回复使用纯文本
- 当推荐具体商品时，在商品描述后插入标记：[PRODUCT:id]
  例如：这款洗面奶非常适合油皮使用 [PRODUCT:p_beauty_001]，价格也很实惠。

## 商品库
{context}   ← 由 RetrieverService 检索结果自动填充
```

**Prompt 各部分的作用：**

| 部分 | 作用 |
|------|------|
| 角色设定 | 让 LLM 知道自己是导购助手 |
| 严格规则 | 约束 LLM 不编造、不超出商品库范围 |
| 输出格式 | 要求插入 `[PRODUCT:id]` 标记，方便前端展示商品卡片 |
| `{context}` | 检索到的商品信息，每次查询动态填充 |

### 2.5 SessionService — 对话历史

**文件**: `service/SessionService.java`

```java
// 用 ConcurrentHashMap 存储每个 session 的对话历史
// sessionId -> List<ChatMessage>
private final ConcurrentHashMap<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();
private static final int MAX_HISTORY = 10;  // 最多保留 10 条消息
```

**为什么需要对话历史？**

没有历史：
```
用户: 推荐面霜
AI: 推荐薇诺娜面霜...
用户: 有没有更便宜的？    ← LLM 不知道"更便宜"是跟什么比
AI: ???（编造一个商品）
```

有历史：
```
用户: 推荐面霜
AI: 推荐薇诺娜面霜 268元...
用户: 有没有更便宜的？    ← LLM 知道是跟 268 元的薇诺娜比
AI: 推荐珂润面霜 128元... ← 基于上下文的合理推荐
```

### 2.6 ChatController — 对外接口

**文件**: `controller/ChatController.java`

```java
// SSE 流式对话（前端逐字显示）
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@RequestParam String message, @RequestParam String sessionId);

// 非流式对话（等待完整回答后一次性返回）
@PostMapping
public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request);

// 清除会话历史
@DeleteMapping("/session/{sessionId}")
public ResponseEntity<Void> clearSession(@PathVariable String sessionId);
```

**SSE（Server-Sent Events）是什么？**

HTTP 协议通常是"请求-响应"模式：客户端发一次请求，服务端返回一次响应。

SSE 是一种让服务端**持续推送数据**给客户端的方式：
- 客户端发一次请求
- 服务端每生成一个 token 就推一次（`data: 推`、`data: 荐`、`data: 薇`...）
- 推完后发 `[DONE]` 表示结束
- 前端收到一个 token 就显示一个 → 用户看到"打字机效果"

---

## 三、数据流全景图

```
前端 / Android
    │
    │  GET /api/chat/stream?message=推荐面霜&sessionId=user123
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│  ChatController                                         │
│  创建 SseEmitter(60s超时)                                │
│  调用 ChatService.chatStream()                          │
└─────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│  ChatService.chatStream()                               │
│                                                         │
│  ① RetrieverService.retrieveByText("推荐面霜", 3)       │
│     │                                                   │
│     ├─ EmbeddingService.embedText("推荐面霜")            │
│     │  → Doubao API → float[2048]                       │
│     │                                                   │
│     └─ ChromaDB query(products_text, vector, topK=3)    │
│        → Top-3 ProductSearchResult                      │
│                                                         │
│  ② formatProducts() → 拼装商品上下文文本                  │
│                                                         │
│  ③ buildMessages() → [SystemMessage, History..., User]   │
│                                                         │
│  ④ OpenAiStreamingChatModel.generate(messages, handler) │
│     │                                                   │
│     ├─ onNext(token) → SseEmitter.send(token)           │
│     │  → 前端收到 "data: 推荐薇诺娜..."                  │
│     │                                                   │
│     ├─ onComplete() → 保存对话历史 → SseEmitter.complete │
│     │  → 前端收到 "data: [DONE]"                         │
│     │                                                   │
│     └─ onError() → SseEmitter.completeWithError()       │
└─────────────────────────────────────────────────────────┘
```

---

## 四、接口文档

### 4.1 SSE 流式对话

```
GET /api/chat/stream?message={message}&sessionId={sessionId}
```

**参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| message | String | 是 | - | 用户消息 |
| sessionId | String | 否 | "default" | 会话 ID（多轮对话需要保持一致） |

**响应：** SSE 事件流（`Content-Type: text/event-stream`）

```
data:推荐薇诺娜舒敏保湿特护霜 [PRODUCT:p_beauty_007]...
data:另外理肤泉特安舒缓修复霜 [PRODUCT:p_beauty_012]...
data:[DONE]
```

**状态码：**
- 200：成功
- 500：LLM 调用失败

---

### 4.2 非流式对话

```
POST /api/chat
Content-Type: application/json

{"sessionId": "test1", "message": "推荐蓝牙耳机"}
```

**响应：**

```json
{
  "sessionId": "test1",
  "reply": "为你推荐两款蓝牙耳机：\n华为FreeBuds Pro 5 [PRODUCT:p_digital_007]...",
  "productIds": ["p_digital_007", "p_digital_018"]
}
```

**状态码：**
- 200：成功
- 400：参数错误

---

### 4.3 清除会话历史

```
DELETE /api/chat/session/{sessionId}
```

**状态码：**
- 200：成功

---

## 五、已验证项

| 验证项 | 结果 | 说明 |
|--------|------|------|
| Doubao Chat API 连通 | ✅ 通过 | LangChain4j OpenAiStreamingChatModel 正常调用 |
| SSE 流式推送 | ✅ 通过 | 前端逐字收到 token |
| 非流式对话 | ✅ 通过 | 返回完整 JSON |
| RAG 检索+生成联动 | ✅ 通过 | LLM 基于检索商品生成推荐 |
| [PRODUCT:id] 标记 | ✅ 通过 | LLM 正确插入商品标记 |
| 多轮对话历史 | ✅ 通过 | 同一 sessionId 记住上下文 |
| 商品推荐准确性 | ✅ 通过 | 推荐的商品与查询相关 |

---

## 六、待优化项

### 6.1 Prompt 优化

**现状：** LLM 推荐的商品可能不在检索结果中（LLM 自行"联想"了其他商品）。

**优化方向：**
- 在 Prompt 中更强调"只能推荐下方列出的商品"
- 添加"如果商品库中没有匹配的商品，请告知用户"的规则

### 6.2 流式输出的 token 粒度

**现状：** 中文被拆成很细的 token（如"推"、"荐"、"薇"、"诺"、"娜"），前端需要自己拼接。

**优化方向：**
- 前端做 token 合并（按句号/逗号/换行分割显示）
- 或后端做 buffer 合并后推送

### 6.3 对话历史持久化

**现状：** 对话历史存在内存（ConcurrentHashMap），服务重启后丢失。

**优化方向：**
- 存入 MySQL 或 Redis
- 添加过期清理机制

---

## 七、依赖关系

```
LangChain4j 依赖（pom.xml 已有）：
- langchain4j (0.36.2)          ← 核心库
- langchain4j-open-ai (0.36.2)  ← OpenAI 兼容接口（Doubao 用这个）
- langchain4j-chroma (0.36.2)   ← 备用，当前未使用

无新增依赖。
```

---

## 八、文件清单

```
server/src/main/java/com/ragagent/
├── config/
│   ├── OpenApiConfig.java          (已有)
│   └── LangChain4jConfig.java      ← 新建
├── model/
│   ├── Product.java                (已有)
│   ├── ProductSearchResult.java    (已有)
│   ├── ChatRequest.java            ← 新建
│   └── ChatResponse.java           ← 新建
├── service/
│   ├── DataImportService.java      (已有)
│   ├── EmbeddingService.java       (已有)
│   ├── KnowledgeIndexer.java       (已有)
│   ├── RetrieverService.java       (已有)
│   ├── ChatService.java            ← 新建
│   └── SessionService.java         ← 新建
└── controller/
    ├── ProductController.java      (已有)
    └── ChatController.java         ← 新建
```
