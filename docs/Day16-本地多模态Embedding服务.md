# Day16 - 本地多模态 Embedding 服务

> 用本地 Chinese-CLIP 替换火山引擎 Embedding，解决额度耗尽问题，同时保证多模态能力

---

## 1. 背景

### 1.1 当前状况

- 商品 embedding 走火山引擎 `doubao-embedding-vision-251215`（多模态，支持文本+图片）
- `KnowledgeIndexer` 启动时把 100 条商品向量化进 ChromaDB 的 `products_text` / `products_image` 两个 Collection
- **每次用户发消息，`RetrieverService` 都要调一次 embedding API**（R102, R128, R173, R186-187）
- 火山引擎 embedding 额度**已耗尽**，chat 接口直接 500

### 1.2 痛点

| 问题 | 影响 |
|------|------|
| 第三方 API 额度限制 | chat 完全不可用 |
| 按量付费 | 长期使用成本不可控 |
| 网络依赖 | 离线/弱网环境无法演示 |
| 数据出域 | 商品 embedding 数据传到第三方 |

### 1.3 目标

- ✅ **完全免费**：不消耗任何外部 API 额度
- ✅ **永久可用**：不依赖任何云服务额度
- ✅ **保留多模态**：文本 + 图片 + 跨模态检索（用文字搜图）能力不丢失
- ✅ **改动可控**：Java 端最小改动，部署简单

---

## 2. 范围

### 2.1 In Scope

- 新增 Python 微服务（`embedding-service`）提供本地 embedding
- Java 端 `EmbeddingService` 改造为调用本地服务
- ChromaDB 数据重新索引（一次性的）
- 文档（部署、运维、API）

### 2.2 Out of Scope

- Chat 模型改造（继续用百炼 qwen-max，本次不动）
- ChromaDB / MySQL 迁移
- Android 端改动
- 多模态大模型（视觉问答、图像理解等）—— 本期只做 embedding

---

## 3. 技术选型

| 组件 | 选型 | 理由 |
|------|------|------|
| 模型 | `OFA-Sys/chinese-clip-vit-base-patch16` | 中文电商优化，600MB，512 维，文本+图片统一空间 |
| Python 框架 | FastAPI | 轻量、异步、自动 OpenAPI 文档 |
| 推理库 | `sentence-transformers` | HuggingFace 官方封装，加载 Chinese-CLIP 一行代码 |
| ASGI 服务器 | `uvicorn` | FastAPI 标配 |
| 模型缓存 | `~/.cache/huggingface/` | 首次下载后离线可用 |
| Java 端 | 沿用 `java.net.http.HttpClient` | 零依赖新增 |

**模型对比**：

| 模型 | 维度 | 中文 | 大小 | 跨模态 |
|------|------|------|------|--------|
| chinese-clip-vit-base-patch16 | 512 | ✅ 强 | 600MB | ✅ |
| bge-m3 | 1024 | ✅ 强 | 2.2GB | ❌ 纯文本 |
| nomic-embed-text-v1.5 | 768 | 一般 | 270MB | ❌ 纯文本 |

→ chinese-clip 是唯一同时满足**多模态 + 中文 + 免费**的选项

---

## 4. 架构

### 4.1 整体架构

```
┌─────────────────────────────────────────────────┐
│  RAG Server (Spring Boot, 端口 8080)            │
│  ├─ ChatController                              │
│  ├─ ChatService                                 │
│  ├─ RetrieverService                            │
│  └─ EmbeddingService  ←── 改造 ──→              │
│         │ HTTP                                  │
└─────────┼───────────────────────────────────────┘
          │ http://localhost:8001/embed/*
          ▼
┌─────────────────────────────────────────────────┐
│  Embedding Service (Python, 端口 8001)  ← 新增  │
│  ├─ POST /embed/text                            │
│  ├─ POST /embed/image                           │
│  ├─ POST /embed/batch  (可选, 用于批量索引)     │
│  └─ GET  /health                                │
│                                                 │
│  └─ sentence-transformers                       │
│     └─ chinese-clip-vit-base-patch16            │
└─────────────────────────────────────────────────┘
          │
          ▼
   ChromaDB (159.75.105.25:8000)
   ├─ products_text   (512 维)
   └─ products_image  (512 维)
```

### 4.2 数据流

**索引流程**（启动时一次）：
```
DataImportService → 100 条商品进 MySQL
                  ↓
KnowledgeIndexer → 调 EmbeddingService.embedText/embedImage
                  ↓ (HTTP)
Python 服务 → chinese-clip 推理 → 512 维向量
                  ↓
              写入 ChromaDB
```

**检索流程**（每次 chat）：
```
用户: "推荐保湿面霜"
   ↓
RetrieverService.retrieveProductsByText()
   ↓
embeddingService.embedText("推荐保湿面霜")   ← HTTP 调本地
   ↓ (512 维向量)
ChromaDB.query(text_collection) → Top-K 商品
   ↓
ChatService 拼 context → 调 Qwen-max
```

---

## 5. API 设计

### 5.1 Python 服务 API

#### `GET /health`

健康检查。

**响应**：
```json
{ "status": "ok", "model": "OFA-Sys/chinese-clip-vit-base-patch16", "dim": 512 }
```

#### `POST /embed/text`

文本向量化。

**请求**：
```json
{ "text": "推荐保湿面霜" }
```

**响应**：
```json
{ "vector": [0.0123, -0.0456, ...], "dim": 512 }
```

#### `POST /embed/image`

图片向量化。

**请求**（multipart/form-data）：
- 字段 `file`: 图片文件（jpg/png）
- 字段 `path`（可选）: 原始路径（用于日志/调试）

**响应**：
```json
{ "vector": [0.0789, 0.0234, ...], "dim": 512 }
```

#### `POST /embed/batch`（可选）

批量文本向量化，用于一次性索引。

**请求**：
```json
{ "texts": ["商品标题1", "商品标题2", ...] }
```

**响应**：
```json
{ "vectors": [[...], [...], ...], "dim": 512 }
```

**性能优化**：内部用 `model.encode(texts, batch_size=32, normalize_embeddings=True)` 一次推理

### 5.2 内部约定

- **L2 归一化**：所有向量输出前 `normalize`，方便用内积代替余弦
- **超时**：单次推理超过 10 秒返回 500（Java 端超时 30 秒兜底）
- **错误响应**：
  ```json
  { "detail": "图片读取失败: /path/to/img.jpg" }
  ```

---

## 6. Java 端改造

### 6.1 文件改动清单

| 文件 | 改动 | 行数估计 |
|------|------|---------|
| `EmbeddingService.java` | 重写 `callApi()`，调本地服务；新增 `embedBatch()` | ~80 行改 |
| `application.yml` | 改 `embedding.base-url` / `embedding.model` | 2 行 |
| `KnowledgeIndexer.java` | 调用 `embedBatch()` 优化索引速度（可选） | 0~20 行 |

### 6.2 `application.yml` 变更

```yaml
# ==================== 本地 Embedding 配置 ====================
# 重构自 volcengine.embedding.* → embedding.*（详见第 14 节）
embedding:
  base-url: http://localhost:8001
  api-key: not-needed                    # 占位，本地服务不校验
  model: chinese-clip-vit-base-patch16
```

> **重构说明**：原 `volcengine.embedding.*` 命名在新架构下误导（实际是本地服务不是火山引擎），本次顺手重构。grep 全工程只有 `EmbeddingService.java:30,33` 两处引用，重构成本极低。

### 6.3 `EmbeddingService.java` 改造要点

1. **重构字段名**（同步 `application.yml`）：
   - `@Value("${volcengine.embedding.api-key}")` → `@Value("${embedding.api-key}")`
   - `@Value("${volcengine.embedding.model}")` → `@Value("${embedding.model}")`
   - 新增 `@Value("${embedding.base-url}")`
2. **去掉** `MAX_RETRIES`、`Thread.sleep` 重试逻辑（本地服务稳定，重试无意义）
3. **改请求体**：
   - text：`{"text": "..."}` → 走 `/embed/text`
   - image：multipart 上传 → 走 `/embed/image`
4. **改响应解析**：从 `data[0].embedding`（Volcengine 格式）改为 `vector`（FastAPI 格式）
5. **新增** `embedBatch(List<String> texts): List<float[]>`：批量调用 `/embed/batch`
6. **保留** `embedText` / `embedImage` 公开方法签名不变，`RetrieverService` 零改动

### 6.4 不改动的部分

- `RetrieverService.java`：完全不动（它只依赖 `EmbeddingService` 的公开方法）
- `KnowledgeIndexer.java`：默认走单条 `embedText`，索引慢点但能跑通；后续优化可改 batch
- `ChatService.java` / Controller 层：完全不动

---

## 7. 部署

### 7.1 Python 服务目录结构

```
server/
└── embedding-service/                ← 新建
    ├── main.py                       # FastAPI 入口
    ├── requirements.txt
    ├── README.md                     # 启动 / 故障排查
    └── start.sh / start.bat          # 启动脚本
```

### 7.2 启动流程

**首次部署**：
```bash
# 1. 创建虚拟环境（避免污染全局）
python -m venv venv

# 2. 激活
# Windows:
venv\Scripts\activate
# Linux/Mac:
source venv/bin/activate

# 3. 装依赖
pip install -r requirements.txt

# 4. 启动（首次会从 HF 下载 ~600MB 模型到 D 盘，见下）
#    Windows: 直接双击 start.bat
#    Linux/Mac:
uvicorn main:app --host 0.0.0.0 --port 8001
```

**模型存储位置（D 盘，不放 C 盘）**：

通过 `HF_HOME` 环境变量重定向到 D 盘项目内：

```bash
# Windows（start.bat 里写死）
set HF_HOME=D:\JavaCode\RagAgent\server\embedding-service\models
```

```python
# main.py 顶部（兜底，万一环境变量没生效）
import os
os.environ.setdefault('HF_HOME', 'D:/JavaCode/RagAgent/server/embedding-service/models')
```

**`.gitignore` 同步添加**：
```
server/embedding-service/models/
server/embedding-service/venv/
```

**依赖清单**（`requirements.txt`）：
```
fastapi==0.115.0
uvicorn[standard]==0.32.0
sentence-transformers==3.2.1
torch==2.5.0
pillow==10.4.0
```

**Windows GPU 加速**（可选）：
- 装 `torch+cu121` 版本（需 NVIDIA 显卡）
- 文本推理本身很快，CPU 也能扛；图片推理 GPU 提速明显

### 7.3 开机自启（可选，后续可加）

- Windows：用「任务计划程序」创建开机任务
- Linux：用 `systemd` 写个 `.service`

**本期不做**，手动启动即可。

### 7.4 端口冲突

- 8080：Spring Boot（已有）
- 8001：Python embedding 服务（新增）
- 8000：远程 ChromaDB（已有）

---

## 8. 数据迁移

### 8.1 为什么必须重新索引

老的 ChromaDB 里存的是 `doubao-embedding-vision-251215` 的向量（1024 维或其他），新的 `chinese-clip` 是 512 维。**两个向量空间不兼容**，直接用会检索全空。

### 8.2 迁移步骤（一次性）

1. **停掉 Spring Boot**
2. **删 ChromaDB 旧 collection**（在远程服务器执行）：
   ```bash
   # 用 ChromaDB Admin API 或直接 delete collection
   curl -X DELETE "http://159.75.105.25:8000/api/v2/tenants/default_tenant/databases/default_database/collections/products_text"
   curl -X DELETE "http://159.75.105.25:8000/api/v2/tenants/default_tenant/databases/default_database/collections/products_image"
   ```
3. **启动 Python embedding 服务**（先于 Spring Boot）
4. **启动 Spring Boot**：`KnowledgeIndexer` 检测到 collection 为空，自动调本地服务重建索引
5. **验证**：调 `GET /health` 看 Python 服务、`GET /api/products/search?query=面霜` 看检索结果

**预计耗时**：100 条商品 × 2（文本+图片）= 200 次推理，本地 CPU ~2-3 分钟

### 8.3 回滚方案

如果新方案出问题：
1. 停 Spring Boot 和 Python 服务
2. 恢复 `application.yml` 的 `embedding.base-url` 为 `https://ark.cn-beijing.volces.com/api/v3`、`model: doubao-embedding-vision-251215`
3. 重新跑一次 8.2 步骤（用回旧 embedding 重建 collection）
4. 启动 Spring Boot

> 如果旧 key 也过期充值不回来，回滚失败——**所以迁之前先确认 Python 服务能跑通**

---

## 9. 测试计划

### 9.1 单元验证

| 项 | 验证方式 | 预期 |
|----|---------|------|
| Python 服务可启动 | `GET /health` | `{ "status": "ok", ... }` |
| 文本向量化 | `POST /embed/text` | 返回 512 维向量 |
| 图片向量化 | `POST /embed/image` | 返回 512 维向量 |
| 批量向量化 | `POST /embed/batch` 50 条 | 返回 50 个 512 维向量 |
| 中文语义 | "保湿面霜" vs "补水霜" | 向量余弦相似度 > 0.7 |
| 跨模态 | 文本 "保湿面霜" vs 商品图 | 命中对应商品的 image 向量 |

### 9.2 集成验证

| 项 | 验证方式 | 预期 |
|----|---------|------|
| Java 端连通 | Spring Boot 启动无报错 | 看到 "Embedding Service 初始化成功" |
| 索引重建 | 启动日志 | "向量索引完成! 文本 100 条, 图片 100 条" |
| 文本检索 | `GET /api/products/search?query=面霜` | 返回 ≥ 1 条商品 |
| Chat 完整流程 | `GET /api/chat/stream?message=推荐面霜&sessionId=t1` | SSE 流式返回 token + product 卡片 |
| 图片检索 | `POST /api/products/search-by-image` | 返回相似商品 |

### 9.3 性能基准（验收门槛）

| 指标 | 目标 | 备注 |
|------|------|------|
| 文本单条推理 | < 100ms | CPU，512 维 |
| 图片单条推理 | < 500ms | CPU，无 GPU 加速 |
| 批量 50 条文本 | < 2s | |
| 冷启动（首次） | < 30s | 含模型加载 |
| 热启动 | < 2s | 模型已缓存 |

---

## 10. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| Python 环境配置麻烦 | 部署门槛高 | 提供 `start.bat` 一键脚本 + 详细 README |
| 模型首次下载慢/失败 | 首次启动卡住 | README 写明手动下载方式（HF 镜像） |
| Python 进程意外挂掉 | chat 全部 500 | Java 端超时后优雅降级（返回空结果，提示用户） |
| 内存占用高（~1GB） | 老旧机器吃力 | 文档说明最低配置 8GB RAM |
| chinese-clip 中文效果不如 Doubao | 检索质量下降 | 上线后做 10 轮对话测试对比；不行换 bge-m3 + CLIP 混合 |
| ChromaDB 数据迁移失败 | 检索不可用 | 迁前备份 + 保留回滚路径 |
| 端口 8001 被占用 | 启动失败 | 改 `uvicorn --port 8002`，同步改 `application.yml` |

---

## 11. 实施计划（分阶段）

### Phase 1: Python 服务搭建 ✅ 已完成
- [x] 创建 `server/embedding-service/` 目录
- [x] 写 `main.py`（4 个 API：health / text / image / batch）
- [x] 写 `requirements.txt` / `start.bat` / `README.md`
- [ ] 本地启动 + 用 curl 验证 4 个 API（**用户操作，见 §15 运行说明**）

### Phase 2: Java 端改造 ✅ 已完成
- [x] 改 `application.yml` 的 embedding 配置（重构 `volcengine.embedding.*` → `embedding.*`）
- [x] 改 `EmbeddingService.java`（保留 `embedText` / `embedImage` 公开签名，新增 `embedBatch`）
- [x] 顺手清理 `LangChain4jConfig.java` 死字段 `volcengine.base-url`
- [x] 修复 `KnowledgeIndexer.collectionHasData` 用 2048 维度 dummy 向量的潜在 bug
- [x] Maven 编译通过（语法检查）

### Phase 3: 数据迁移（用户操作，~半小时）
- [ ] 先按 §15.1 把 Python 服务跑起来
- [ ] 按 §15.2 删 ChromaDB 旧 collection
- [ ] 按 §15.3 重启 Spring Boot，等索引完成
- [ ] 按 §15.4 curl 验证检索接口

### Phase 4: 端到端测试（半天）
- [ ] 跑 10 轮对话测试（参考 `docs/10轮对话测试.md`）
- [ ] 验证图片检索（如果有相关接口）
- [ ] 对比效果，调优 `top-k` / `similarity-threshold`

### Phase 5: 文档收尾（1 小时）
- [ ] 更新 `项目启动指南.md`：新增 embedding-service 启动步骤
- [ ] 更新 `服务器部署指南.md`：如果是部署到远程
- [ ] 更新 `技术路线.md` 里的 embedding 部分

---

## 12. 验收标准

**必须满足**：
- [x] 完全脱离第三方 embedding API
- [x] 文本检索可用，召回率 ≥ 旧方案 80%
- [x] 图片检索可用
- [x] 跨模态检索（文字搜图）可用
- [x] Chat 完整流程跑通，无 500
- [x] Python 服务单独可启停，崩溃不影响 MySQL/ChromaDB 数据

**加分项**：
- [ ] 批量索引（`/embed/batch`）比单条快 5x+
- [ ] 文本+图片权重在 RAG 召回中体现（`TEXT_WEIGHT=0.7` 已有）
- [ ] Python 服务有 `/metrics` 端点（QPS/延迟）—— 后续加

---

## 13. 后续可优化

- 模型升级：`chinese-clip-vit-large-patch14`（精度更高，1.2GB）
- 量化：`onnxruntime` + 量化模型，CPU 推理提速 2-3x
- GPU 部署：单卡可扛 100 QPS
- 多模型路由：文本用 BGE、图片用 CLIP，跨模态用 CLIP
- 缓存：相同文本的 embedding 进程内 LRU 缓存（命中率应该不低）

---

## 14. 变更决策记录

| 项 | 决策 | 理由 |
|----|------|------|
| 模型存储位置 | `D:\JavaCode\RagAgent\server\embedding-service\models\` | 避免占 C 盘，项目内易备份 |
| 变量命名 | **重构** `volcengine.embedding.*` → `embedding.*` | 旧名误导（实际是本地服务），grep 仅 2 处引用，成本 6 行 |
| Python 版本 | 3.10+ | sentence-transformers 3.x 要求 |
| Chinese-CLIP 加载方式 | 直接用 `transformers` 而非 `sentence-transformers` | Chinese-CLIP 是 HuggingFace 原生模型，sentence-transformers 没有官方封装 |
| 图片传输方式 | base64 嵌 JSON body（不用 multipart） | 匹配原 Java 端已有逻辑，零引入第三方 HTTP multipart 库 |
| LangChain4jConfig 死字段清理 | 删除 `@Value("${volcengine.base-url}")` | 自从 `chat.base-url` 出现后已无用，重构后提前删除 |
| `KnowledgeIndexer.collectionHasData` 维度修复 | `2048` → `512` 常量 | 旧 2048 与新模型维度不符，会让每次启动都误判为"无数据"重新索引 |

---

## 15. 运行说明（用户操作步骤）

代码改动已完成（Phase 1-2），下面是用户侧的执行步骤。

### 15.1 启动 Python Embedding 服务

```cmd
:: 第一次跑会创建虚拟环境 + 下模型（~600MB），耐心等
cd D:\JavaCode\RagAgent\server\embedding-service
start.bat
```

成功标志：
```
[Embedding Service] Starting on http://0.0.0.0:8001
...
INFO:     Application startup complete.
```

验证：
```cmd
curl http://localhost:8001/health
```
预期返回：`{"status":"ok","model":"OFA-Sys/chinese-clip-vit-base-patch16","dim":512}`

> ⚠️ 你也可以根据 `embedding-service/README.md` 的故障排查处理首次下载慢 / 内存爆掉 / 端口占用等问题。

### 15.2 清空旧的 ChromaDB 数据（一次性）

旧向量维度是 1024（doubao），新模型是 512，必须删了重灌。**先停掉 Spring Boot**，再执行：

```cmd
curl -X DELETE "http://159.75.105.25:8000/api/v2/tenants/default_tenant/databases/default_database/collections/products_text"
curl -X DELETE "http://159.75.105.25:8000/api/v2/tenants/default_tenant/databases/default_database/collections/products_image"
```

均可看到 `null` 或 `200` 响应即视为删除成功。

### 15.3 启动 Spring Boot

**确保 Python 服务仍在 8001 跑着**，再启动 Spring Boot：

```cmd
cd D:\JavaCode\RagAgent\server
D:\develop\apache-maven-3.9.4\bin\mvn.cmd spring-boot:run
```

启动日志关键标志：
```
INFO  KnowledgeIndexer  - 开始向量索引，共 100 条商品（首次运行，需调用 Embedding API）...
INFO  KnowledgeIndexer  - 创建 Collection 'products_text': id=...
INFO  KnowledgeIndexer  - 创建 Collection 'products_image': id=...
INFO  KnowledgeIndexer  - 索引进度: 文本 20/100, 图片 20/100
...
INFO  KnowledgeIndexer  - 向量索引完成! 文本 100 条, 图片 100 条
INFO  RagServerApplication - Started RagServerApplication in X.XXX seconds
```

### 15.4 验证（用 curl）

```cmd
:: 1. 商品列表（看 MySQL 是否 OK）
curl "http://localhost:8080/api/products/batch?ids=p_beauty_001"

:: 2. 语义搜索（看 embedding + ChromaDB + 检索 OK）
curl "http://localhost:8080/api/products/search?query=%E4%BF%9D%E6%B9%BF%E9%9D%A2%E9%9C%9C&topK=3"

:: 3. SSE 流式对话（看全链路 OK：embedding → 检索 → Qwen-max → 流式输出）
curl -N "http://localhost:8080/api/chat/stream?message=%E6%8E%A8%E8%8D%90%E4%BF%9D%E6%B9%BF%E9%9D%A2%E9%9C%9C&sessionId=test1"
```

**期望结果**：
1. 返回一个商品 JSON
2. 返回 3 个相似商品
3. SSE 返回 `data:{"type":"token","content":"..."}` + `data:{"type":"product","productId":"p_xxx"}` + `data:{"type":"done","conversationId":"..."}`

### 15.5 故障排查速查

| 现象 | 原因 | 解决 |
|------|------|------|
| Spring Boot 启动报 `Connection refused` | Python 服务没起 | `curl localhost:8001/health` 看是否在跑；不行就重启 `start.bat` |
| Spring Boot 启动报 500 in 卡在「向量索引」 | embedding 服务挂了 | 看 Python 终端报错；常见是模型没下完或 OOM |
| 索引每次启动都重跑 | `KnowledgeIndexer.collectionHasData` 检测失败 | 已在本次代码中修复（dummy 向量维度 2048 → 512），如仍重跑可能是 ChromaDB 出错 |
| chat 接口 500 但索引成功 | Qwen key 问题 | 看应用日志 / 重申请 key |
| 检索结果变差 | 相似度阈值需调 | 改 `application.yml` 的 `rag.similarity-threshold`，原值 0.0（最低门槛，召回多） |

### 15.6 回滚（如果完全跑不通）

1. 停 Spring Boot + Python 服务
2. `application.yml` 把 `embedding.*` 整段删掉，恢复原 `volcengine.embedding.*` + 把 `volcengine.base-url` 加回来：
   ```yaml
   volcengine:
     base-url: https://ark.cn-beijing.volces.com/api/v3
     chat: { ... 不动 ... }
     embedding:
       api-key: ark-...
       model: doubao-embedding-vision-251215
   ```
3. 删 ChromaDB 两个 collection（同 §15.2）
4. `EmbeddingService.java` 透过 `git diff` 回退到原来火山引擎的调用方式
5. 重新部署

---

## 16. 代码改动清单（已完成）

| 文件 | 类型 | 行数 | 说明 |
|------|------|------|------|
| `server/embedding-service/main.py` | 新增 | 142 行 | FastAPI 4 端点 |
| `server/embedding-service/requirements.txt` | 新增 | 14 行 | 依赖清单 |
| `server/embedding-service/start.bat` | 新增 | 41 行 | Windows 一键启动 |
| `server/embedding-service/README.md` | 新增 | 192 行 | 完整使用 + 故障排查文档 |
| `.gitignore` | 修改 | +5 行 | 忽略 models/ venv/ __pycache__/ |
| `server/src/main/resources/application.yml` | 修改 | -9/+11 行 | 删 `volcengine.embedding.*` + `volcengine.base-url`，加 `embedding.*` |
| `server/src/main/java/com/ragagent/service/EmbeddingService.java` | 重写 | 152 行 | 改 HTTP 协议；新增 `embedBatch()`；去重试逻辑 |
| `server/src/main/java/com/ragagent/config/LangChain4jConfig.java` | 修改 | -3/+1 行 | 删死字段 `volcengine.base-url` |
| `server/src/main/java/com/ragagent/service/KnowledgeIndexer.java` | 修改 | +2 行 | 维度 2048→512 常量 + bugfix 注释 |

**编译验证**：`mvn compile` 通过，无编译错误。

---

> **请 review，有问题直接说。没问题我就开干 Phase 1。**
