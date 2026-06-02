# Day 02 — 多模态 Embedding + 向量库 + 知识库构建

## 一、今日完成事项

### 1.1 核心组件实现

| 组件 | 文件 | 职责 |
|------|------|------|
| EmbeddingService | `service/EmbeddingService.java` | 调用 Doubao-embedding-vision API，支持文本/图片向量化 |
| KnowledgeIndexer | `service/KnowledgeIndexer.java` | 启动时自动将 MySQL 商品数据索引到 ChromaDB |
| RetrieverService | `service/RetrieverService.java` | 向量检索 + 双 Collection RRF 合并排序 |
| ProductRepository | `repository/ProductRepository.java` | 商品 CRUD（JdbcTemplate） |
| ProductController | `controller/ProductController.java` | 商品 REST 接口 |

### 1.2 配置变更

**application.yml 新增/修改项：**

```yaml
# ChromaDB 升级为 v2 API，需指定 tenant 和 database
chromadb:
  url: http://159.75.105.25:8000
  tenant: default_tenant
  database: default_database

# 火山引擎配置拆分为 chat 和 embedding 两个独立配置
volcengine:
  base-url: https://ark.cn-beijing.volces.com/api/v3
  chat:
    api-key: 赛方提供
    model-id: ep-20260514111645-lmgt2
  embedding:
    api-key: 自己开通
    model: doubao-embedding-vision-251215

# 新增数据集路径配置
dataset:
  path: ../ecommerce_agent_dataset
```

### 1.3 ChromaDB 双 Collection 设计

```
ChromaDB (v2 API)
├── products_text   ← 文本向量（标题+品牌+类目+营销描述）
└── products_image  ← 图片向量（商品图片 base64 → embedding）
```

- 文本向量和图片向量在同一向量空间（Doubao-embedding-vision 特性）
- 每条记录包含：id（product_id）、embedding（2048维）、metadata（product_id, title, brand, category）
- 检索时支持文本检索、图片检索、混合检索（RRF 加权合并）

### 1.4 启动时序

```
Spring Boot 启动
    │
    ├─ @Order(0) DataImportService
    │   └─ 检查 MySQL 是否有数据 → 无则从 JSON 文件导入 100 条商品
    │
    └─ @Order(1) KnowledgeIndexer
        └─ 检查 ChromaDB products_text 是否有数据
            ├─ 有 → 跳过（缓存命中，零 API 调用）
            └─ 无 → 调用 Doubao API 索引 100 条文本 + 100 条图片
```

### 1.5 Embedding API 调用

- **Endpoint**: `POST https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal`
- **模型**: `doubao-embedding-vision-251215`
- **向量维度**: 2048
- **输入格式**:
  - 文本: `{"type": "text", "text": "..."}`
  - 图片: `{"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}`
- **响应格式**: `{"data": {"embedding": [float, ...]}}`
- **缓存策略**: ChromaDB 持久化存储，首次启动调 API，后续启动零调用

---

## 二、接口文档

### 2.1 获取单个商品

```
GET /api/products/{id}
```

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 商品ID，如 `p_beauty_001` |

**响应示例：**

```json
{
  "productId": "p_beauty_001",
  "title": "雅诗兰黛特润修护肌活精华露淡纹紧致保湿夜间修护抗初老精华30ml",
  "brand": "雅诗兰黛",
  "category": "美妆护肤",
  "subCategory": "精华",
  "basePrice": 720.00,
  "imagePath": "1_美妆护肤/images/p_beauty_001_live.jpg",
  "marketingDescription": "雅诗兰黛特润修护肌活精华露（小棕瓶）是品牌经典抗初老单品...",
  "createdAt": "2026-05-22T22:00:38",
  "updatedAt": "2026-05-22T22:00:38"
}
```

**状态码：**
- 200：成功
- 404：商品不存在

---

### 2.2 批量获取商品

```
GET /api/products/batch?ids=p_beauty_001,p_beauty_002
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| ids | String | 是 | 商品ID列表，逗号分隔 |

**响应：** 商品数组（格式同单个商品）

**状态码：**
- 200：成功（即使部分 ID 不存在也返回已有的）

---

### 2.3 向量语义搜索

```
GET /api/products/search?query=保湿面霜&topK=3&category=美妆护肤
```

**查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| query | String | 是 | - | 搜索关键词（自然语言） |
| topK | int | 否 | 3 | 返回结果数量 |
| category | String | 否 | null | 类目过滤，如"美妆护肤"、"数码电子" |

**内部流程：**
1. 将 query 文本通过 Doubao API 转为 2048 维向量
2. 如果传了 category，在 ChromaDB 做元数据过滤
3. 在 ChromaDB `products_text` collection 中做余弦相似度检索
4. 返回 Top-K 商品详情

**状态码：**
- 200：成功
- 400：参数错误

---

## 三、搜索质量排查与修复

### 3.1 问题现象

搜索"保湿面霜"返回耳机、手机等完全不相关的商品。同时发现带 category 过滤的查询返回空数组 `[]`。

### 3.2 排查过程

**第一步：确认 ChromaDB 数据存在**

用 curl 直接查询 ChromaDB，确认 collection 和数据都存在：

```bash
curl -s "http://159.75.105.25:8000/api/v2/tenants/default_tenant/databases/default_database/collections/products_text"
# 返回 200，collection 存在，id=80e4ff97-a297-4274-8fae-f93c4907544d
```

**第二步：确认 category 过滤语法正确**

用 curl 直接带 where 过滤查询 ChromaDB，返回正常结果：

```bash
curl -s -X POST ".../collections/{id}/query" \
  -H "Content-Type: application/json" \
  -d '{"query_embeddings": [...], "n_results": 3, "where": {"category": "美妆护肤"}}'
# 返回美妆护肤类商品，过滤语法没问题
```

**第三步：分析服务端启动日志**

重新启动 Spring Boot，发现关键日志：

```
INFO  KnowledgeIndexer : 开始向量索引，共 100 条商品（首次运行，需调用 Embedding API）...
WARN  KnowledgeIndexer : 创建 Collection 'products_text' 失败: status=409
WARN  KnowledgeIndexer : 创建 Collection 'products_image' 失败: status=409
ERROR KnowledgeIndexer : 创建 Collection 失败，终止索引
```

日志说明：第一次启动时已经成功创建了 collection 并写入了数据。但第二次启动时：
1. `createCollection` 返回了 409（collection 已存在）
2. 代码没有正确处理 409，返回了 `null`
3. 拿到 `null` 的 collection ID 后，直接终止了索引

**第四步：定位为什么缓存检测没有生效**

按设计，第二次启动应该走到"已有数据，跳过索引"的分支，但实际没有。追踪代码流程：

```java
// KnowledgeIndexer.run()
String existingId = getCollectionId(TEXT_COLLECTION);        // ① 获取 collection ID
if (existingId != null && collectionHasData(existingId)) {   // ② 检查是否有数据
    log.info("已有数据，跳过索引");
    return;
}
```

理论上 `getCollectionId` 能拿到 ID（curl 证明 collection 存在），然后 `collectionHasData` 应该返回 true。但实际没有。

**第五步：直接测试 `collectionHasData` 的查询逻辑**

用 curl 模拟 `collectionHasData` 的查询，传入 10 维 dummy 向量：

```bash
curl -s -X POST ".../collections/{id}/query" \
  -d '{"query_embeddings": [[0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1]], "n_results": 1}'
```

返回：

```json
{"error":"InvalidArgumentError","message":"Collection expecting embedding with dimension of 2048, got 10"}
```

**根因找到了。**

### 3.3 根因分析

`collectionHasData` 方法用了一个 10 维的 dummy 向量去查询 ChromaDB，但 collection 期望 2048 维。查询报错后被 catch 吞掉，返回 `false`。

整个故障链：

```
collectionHasData 用 10 维向量查询
    → ChromaDB 报错 "expecting 2048, got 10"
    → catch 捕获异常，返回 false
    → indexer 认为没有数据，尝试创建 collection
    → 409 已存在，createCollection 返回 null
    → textColId == null，终止索引
    → RetrieverService 查不到数据（或查到的是第一次索引的数据）
```

为什么第一次启动没问题？因为第一次启动时 collection 不存在，`getCollectionId` 返回 null，跳过 `collectionHasData` 直接走创建逻辑。数据创建成功后，搜索正常。但第二次启动时，缓存检测失效，导致 indexer 无法正确判断已有数据。

### 3.4 修复方案

**修复 1：dummy 向量维度从 10 改为 2048**

```java
// KnowledgeIndexer.java - collectionHasData 方法
// 修改前：
JsonArray dummyEmb = new JsonArray();
for (int i = 0; i < 10; i++) dummyEmb.add(0.1f);

// 修改后：
JsonArray dummyEmb = new JsonArray();
for (int i = 0; i < 2048; i++) dummyEmb.add(0.1f);
```

**修复 2：文本拼接去掉价格信息**

价格是数值信息，在向量空间中会干扰语义匹配（"720元" 和 "268元" 的向量距离可能比 "精华" 和 "面霜" 更近）。

```java
// KnowledgeIndexer.java - 文本拼接
// 修改前：
String text = String.format("%s %s %s %s %d元 %s",
    product.getTitle(), product.getCategory(),
    product.getSubCategory(), product.getBrand(),
    product.getBasePrice().intValue(), product.getMarketingDescription());

// 修改后：
String text = String.format("%s %s %s %s %s",
    product.getTitle(), product.getBrand(),
    product.getCategory(), product.getSubCategory(),
    product.getMarketingDescription() != null ? product.getMarketingDescription() : "");
```

**修复 3：ProductController 支持 category 过滤**

新增 `category` 查询参数，先按类目过滤再做向量检索，缩小搜索范围：

```java
@GetMapping("/search")
public ResponseEntity<List<Product>> search(
        @RequestParam String query,
        @RequestParam(defaultValue = "3") int topK,
        @RequestParam(required = false) String category) {
    List<String> productIds = retrieverService.retrieveByText(query, topK, category);
    List<Product> products = productRepository.findByIds(productIds);
    return ResponseEntity.ok(products);
}
```

RetrieverService 中用 ChromaDB 的 `$eq` 操作符做元数据过滤：

```java
if (category != null && !category.isEmpty()) {
    JsonObject categoryEq = new JsonObject();
    categoryEq.addProperty("$eq", category);
    whereFilter = new JsonObject();
    whereFilter.add("category", categoryEq);
}
```

### 3.5 修复后验证

```bash
# 带 category 过滤
curl "http://localhost:8080/api/products/search?query=保湿面霜&topK=3&category=美妆护肤"
# → 薇诺娜保湿面霜、玉兰水面霜、理肤泉修复霜 ✅

# 不带 category 过滤
curl "http://localhost:8080/api/products/search?query=保湿面霜&topK=3"
# → 同样返回 3 个面霜商品 ✅

# 其他查询
curl "http://localhost:8080/api/products/search?query=蓝牙耳机&topK=3"
# → 华为 FreeBuds Pro、Apple AirPods Pro ✅
```

---

## 四、已验证项

| 验证项 | 结果 | 说明 |
|--------|------|------|
| Embedding API 连通性 | ✅ 通过 | 返回 2048 维向量 |
| 图片 base64 编码 | ✅ 通过 | JPG/PNG 均可正确编码 |
| ChromaDB v2 Collection 创建 | ✅ 通过 | status=200 |
| ChromaDB v2 数据写入 | ✅ 通过 | status=201 (Created) |
| 文本索引 100 条 | ✅ 通过 | products_text collection 有数据 |
| 图片索引 100 条 | ✅ 通过 | products_image collection 有数据 |
| 缓存命中跳过 | ✅ 通过 | 修复 dummy 向量维度后正常工作 |
| `/api/products/{id}` | ✅ 通过 | 返回正确 JSON，中文正常 |
| `/api/products/batch` | ✅ 通过 | 批量查询正常 |
| `/api/products/search` | ✅ 通过 | 修复后返回相关商品 |
| category 过滤 | ✅ 通过 | 元数据过滤 + 向量检索联合生效 |

---

## 五、待优化项

### 5.1 索引耗时

**现状：** 首次启动索引 100 条商品约需 1 分钟（200 次 API 调用）。

**优化方向：**
- 批量调用 Embedding API（当前是逐条调用）
- 异步并行处理（当前是串行）

### 5.2 图片检索未接入 ProductController

**现状：** `RetrieverService` 已实现 `retrieveByImage()` 和 `retrieveHybrid()`，但 `ProductController` 只暴露了文本搜索接口。

**待做：** Day 3-4 接入 Android 端图片上传时，添加图片搜索接口。

### 5.3 ChromaDB 数据持久化

**风险：** 如果服务器 Docker 容器重启，ChromaDB 数据可能丢失，需要重新索引。

**优化方向：**
- Docker 挂载 volume 持久化（部署时已配置）
- 添加数据备份机制

---

## 六、依赖关系

```
pom.xml 新增依赖：无（Embedding 通过 HttpClient 直接调 API，不依赖 LangChain4j 的 EmbeddingModel）

pom.xml 保留依赖：
- langchain4j (0.36.2) — Day 3-4 RAG 链路用
- langchain4j-open-ai (0.36.2) — Day 3-4 ChatModel 用
- langchain4j-chroma (0.36.2) — 备用，当前用 HTTP 直接调 ChromaDB API
- langchain4j-embeddings-all-minilm-l6-v2 — 已注释，降级方案
```

---

## 七、文件清单

```
server/src/main/java/com/ragagent/
├── RagServerApplication.java          (已有)
├── model/
│   └── Product.java                   (已有)
├── repository/
│   └── ProductRepository.java         ← 新增
├── service/
│   ├── DataImportService.java         (已有，加了 @Order(0))
│   ├── EmbeddingService.java          ← 新增
│   ├── KnowledgeIndexer.java          ← 新增（含 bug 修复）
│   └── RetrieverService.java          ← 新增
└── controller/
    └── ProductController.java         ← 新增

server/src/main/resources/
├── application.yml                    (修改：拆分 volcengine 配置 + ChromaDB v2)
└── application.yml.example            (同步修改)
```
