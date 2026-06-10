# RAG-Agent  README
# 无职转生~到了ai全栈大赛就拿出真本事 团队出品


> **团队：** 无职转生~到了AI全栈大赛就拿出真本事
>
> **项目：** 基于 RAG 的电商智能导购 AI Agent

---

## 一、产品简介

本项目是一个基于 RAG（检索增强生成）技术的电商智能导购助手。用户通过 Android App 以自然语言对话的方式，发现、比较和购买商品。

**核心亮点：**
- 语义级商品检索（文本 + 图片多模态 Embedding）
- SSE 流式对话，逐字渲染，体验流畅
- 三层防幻觉机制，保证推荐基于真实商品
- 多轮对话上下文记忆，支持追问和反选排除
- 对话驱动购物车，语音/文字即可加购

---

## 二、部署架构

```
评委本地电脑                          远程服务器（已部署，无需操作）
┌─────────────────┐                 ┌─────────────────┐
│ Spring Boot 后端 │ ── 连接 ──→   │   MySQL 8.0     │
│ (端口 8080)      │               │   159.75.105.25  │
│                  │               │   端口 3306      │
│                  │               ├─────────────────┤
│                  │ ── 连接 ──→   │   ChromaDB      │
│                  │               │   159.75.105.25  │
│                  │               │   端口 8000      │
└────────┬────────┘               └─────────────────┘
         │
         │ HTTP/SSE
         ▼
┌─────────────────┐
│ Android 模拟器   │
│ (同机访问)       │
└─────────────────┘
```

- **远程服务器**：MySQL + ChromaDB（已就绪，无需操作）
- **评委本地**：Spring Boot 后端 + Android 模拟器

---

## 三、环境要求

| 依赖 | 版本 | 下载地址 |
|------|------|---------|
| **JDK** | 17+ | https://adoptium.net/ |
| **Maven** | 3.9+ | https://maven.apache.org/download.cgi |
| **Git** | 任意 | https://git-scm.com/ |
| **Android Studio** | 最新版 | https://developer.android.com/studio |

> Maven 下载慢？在 `~/.m2/settings.xml` 中加阿里云镜像：
>
> ```xml
> <settings>
>   <mirrors>
>     <mirror>
>       <id>aliyun</id>
>       <mirrorOf>central</mirrorOf>
>       <url>https://maven.aliyun.com/repository/public</url>
>     </mirror>
>   </mirrors>
> </settings>
> ```

---

## 四、部署步骤（约 10 分钟）

### Step 1：克隆代码

```bash
git clone <仓库地址> rag-agent
cd rag-agent
```

### Step 2：配置后端

```bash
cp server/src/main/resources/application.yml.example server/src/main/resources/application.yml
```

编辑 `server/src/main/resources/application.yml`，完整内容如下（直接填值）：

```yaml
server:
  port: 8080

spring:
  application:
    name: rag-server
  datasource:
    url: jdbc:mysql://159.75.105.25:3306/rag_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: rag_agent
    password: 见部署指南指南
    driver-class-name: com.mysql.cj.jdbc.Driver

chromadb:
  url: http://159.75.105.25:8000
  tenant: default_tenant
  database: default_database

volcengine:
  base-url: https://ark.cn-beijing.volces.com/api/v3
  chat:
    api-key: 见部署指南指南
    model-id: ep-20260514111645-lmgt2
  embedding:
    api-key: 见部署指南指南
    model: doubao-embedding-vision-251215

rag:
  top-k: 3
  similarity-threshold: 0.5
  max-context-messages: 10

dataset:
  path: ./data/ecommerce_agent_dataset
```

### Step 3：启动后端

**在项目根目录**执行：

```bash
mvn spring-boot:run -f server/pom.xml
```

> **首次启动约 1-2 分钟**，需要调用 Embedding API 对 100 条商品构建向量索引。后续启动会自动跳过，几秒完成。

看到以下日志表示成功：

```
Started RagServerApplication in X.XXX seconds
```

保持终端窗口打开，不要关闭。

### Step 4：运行 Android 模拟器

1. 打开 Android Studio → File → Open → 选择 `client/` 目录
2. 等待 Gradle 同步完成
3. 创建模拟器：Device Manager → Create Device → Pixel 6 → API 35
4. 启动模拟器

### Step 5：安装运行 App

**方式 A：Android Studio 直接运行**

点击工具栏绿色 Run 按钮（Shift + F10），选择模拟器。

**方式 B：命令行安装**

```bash
cd client
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 6：开始体验

打开 App → 注册账号 → 登录 → 输入你的问题。

---

## 五、推荐体验话术

| 场景 | 输入示例 | 预期效果 |
|------|---------|---------|
| 基础推荐 | "推荐一款适合油皮的洗面奶" | 推荐 1-3 款洁面产品 + 商品卡片 |
| 价格筛选 | "200 元以下的蓝牙耳机" | 返回符合条件的耳机 |
| 模糊需求 | "我想买护肤品" | AI 追问肤质/功效需求 |
| 多轮对话 | "推荐跑鞋" → "要轻量的" → "500 以内" | 逐步收敛结果 |
| 反选排除 | "不要含酒精的，200 元以内" | 排除含酒精产品 |
| 加购操作 | "把这款加到购物车" | 自动识别意图并执行加购 |
| 幻觉检测 | "推荐 1000 万像素的卡片相机" | "暂时没有找到符合您需求的商品" |

---

## 六、验证清单

### 后端验证

```bash
# 健康检查
curl http://localhost:8080/api/health

# 商品查询（验证 MySQL）
curl http://localhost:8080/api/products/batch?ids=p_beauty_001

# 语义搜索（验证 ChromaDB + Embedding）
curl "http://localhost:8080/api/products/search?query=保湿面霜&topK=3"

# 流式对话（验证 RAG 全链路）
curl -N "http://localhost:8080/api/chat/stream?message=推荐面霜&sessionId=test1"

# API 文档（浏览器）
http://localhost:8080/doc.html
```

### 客户端验证

| 验证项 | 期望结果 |
|--------|---------|
| App 启动 | 进入欢迎页 |
| 注册/登录 | 成功进入对话界面 |
| 发送"推荐保湿面霜" | 流式显示回复 + 商品卡片 |
| 点击商品卡片 | 跳转商品详情页 |
| 说"把这款加到购物车" | 购物车中有该商品 |

---

## 七、常见问题

**Q: 连不上远程数据库？**
检查网络能否访问 `159.75.105.25`：
```bash
telnet 159.75.105.25 3306
curl http://159.75.105.25:8000/api/v2/heartbeat
```
公司内网可能有限制，换手机热点试试。

**Q: 首次启动很慢？**
正常，首次需要调 Embedding API 构建向量索引（1-2 分钟）。后续启动秒开。

**Q: App 提示网络错误？**
确认后端在跑（`curl http://localhost:8080/api/health`）。模拟器用 `10.0.2.2:8080` 访问宿主机。

**Q: Maven 依赖下载慢？**
配阿里云 Maven 镜像，见上方「环境要求」。

---

## 八、API 接口速览

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| SSE 流式对话 | GET | `/api/chat/stream?message={msg}&sessionId={id}` | 核心接口 |
| 商品详情 | GET | `/api/products/{id}` | 查询单个商品 |
| 语义搜索 | GET | `/api/products/search?query={q}` | 向量检索 |
| 用户注册 | POST | `/api/auth/register` | 注册 |
| 用户登录 | POST | `/api/auth/login` | 登录 |
| 购物车 | GET/POST/DELETE | `/api/cart/**` | 购物车 CRUD |
| 会话列表 | GET | `/api/conversations` | 历史会话 |
| API 文档 | — | `http://localhost:8080/doc.html` | Swagger |

---

## 九、技术架构

```
┌─────────────────────────────────────────────────┐
│              Android 客户端                       │
│  对话界面 → 商品卡片 → 购物车 → 会话历史          │
└───────────────────────┬─────────────────────────┘
                        │ HTTP / SSE
                        ▼
┌─────────────────────────────────────────────────┐
│            Spring Boot 后端                       │
│  ChatController → RAG Service → LangChain4j      │
│  ProductController → CartController               │
│  AuthController (JWT) → ConversationController    │
└────────┬──────────────────────┬─────────────────┘
         │                      │
         ▼                      ▼
┌─────────────────┐    ┌─────────────────┐
│   MySQL 8.0     │    │   ChromaDB      │
│   商品底表       │    │   向量数据库     │
│   用户/购物车   │    │   文本+图片向量  │
└─────────────────┘    └─────────────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │   火山引擎 API     │
                    │   豆包大模型       │
                    │   Embedding 模型   │
                    └───────────────────┘
```

**RAG 核心链路：** 用户问题 → Embedding 向量化 → ChromaDB 语义检索 Top-K 商品 → 组装 Prompt → 豆包大模型流式生成 → SSE 逐字推送

**三层防幻觉：**
1. Prompt 级：硬约束"只能推荐商品库中的商品"
2. 检索级：仅注入相似度 > 阈值的 Top-K 商品
3. 后处理级：校验 `[PRODUCT:id]` 标记，过滤不存在的商品

---

> **最后更新：** 2026-06-10
