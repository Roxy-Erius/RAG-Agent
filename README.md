# RagAgent · 电商导购 AI Agent

> 无职转生~到了ai全栈大赛就拿出真本事 团队出品

基于 RAG 的**多模态电商导购系统**：电商前台（浏览 / 搜索 / 购物车 / 订单）+ 全局内嵌的 **AI 导购助手**（SSE 流式对话 + 商品卡片 + 加购）+ **管理后台**（业务数据 / 日志 / 评测 / 监控）。

> 本分支（`dev_zr`）已把前端从 Android 重构为 **React Web**，电商为主干、Agent 为粘在页面上的导购员，并补齐了 **管理后台 + 可观测性 + 评测落库**。

---

## 1. 项目结构

```
RagAgent/
├── server/                    Spring Boot 后端（RAG 检索 / SSE 对话 / 商品·购物车·订单 / JWT 鉴权）
│   ├── embedding-service/     本地多模态 Embedding 服务（Chinese-CLIP，端口 8001，随后端自动拉起）
│   └── src/main/resources/db/migration/   Flyway 数据库迁移脚本（V1~）
├── web/                       React Web 前端（电商前台 + 全局 AI 导购浮窗），端口 5173
├── admin/                     管理后台（独立 React 应用，Ant Design），端口 5174
├── monitoring/                Docker 监控栈（Prometheus + Grafana）
├── client/                    Android 客户端（历史存档，不再维护）
├── docs/                      开发文档（开发复盘 / 技术选型 / Bug记录 / Docker监控搭建 …）
├── ecommerce_agent_dataset/   商品数据集（图片 + JSON）——不在仓库内，需另行放置
├── start-dev.bat / .ps1       一键启动脚本（后端 + 前台 + 后台）
└── README.md
```

## 2. 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 3.2 · JDK 17 · MySQL · ChromaDB · Flyway（数据库版本管理）· OpenAI 兼容 Chat 模型（当前 DeepSeek） |
| 检索 | 本地 Chinese-CLIP 多模态 Embedding · 多模态 RRF 融合：`text→text` + `text→image`（跨模态） |
| 前台 | React 19 · Vite · TypeScript · Tailwind CSS · React Router · Zustand · Axios |
| 后台 | React 19 · Vite · TypeScript · **Ant Design 6** · ECharts |
| 可观测性 | Spring Boot Actuator · Micrometer · **Prometheus · Grafana（Docker）** |
| 评测 | LLM-as-Judge（本地 M-Prometheus / Ollama），结果落库可看趋势 |

## 3. 环境要求

- **JDK 17+**、**Maven 3.8+**
- **Node.js 18+**（含 npm）
- **Python 3.10+**（本地 embedding 服务用；后端会自动拉起）
- **MySQL** 与 **ChromaDB**（可用远程，见配置文件）
- **Docker Desktop**（仅监控栈需要；不跑监控可跳过）

---

## 4. 快速开始

### 方式 A：一键启动（推荐）

双击 `start-dev.bat`，或在仓库根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-dev.ps1
```

脚本会自动：检查环境 → 安装依赖（首次）→ 启动后端（**自动拉起本地 embedding**）→ 启动前台 → 启动管理后台。

### 方式 B：手动启动

**① 配置后端**

```powershell
copy server\src\main\resources\application.yml.example server\src\main\resources\application.yml
```

编辑 `application.yml`，填入 MySQL、ChromaDB 连接信息和 chat 模型 API key
（`application.yml` 含密钥，已被 `.gitignore` 忽略，不会提交）。

**② 确认数据集就位**

把 `ecommerce_agent_dataset/` 放到仓库根目录（含商品图片与 JSON）。缺失会导致商品图 404。

**③ 启动后端**（会自动拉起本地 embedding 到 8001）

```powershell
mvn -f server/pom.xml spring-boot:run
```

> 首次启动会由 **Flyway** 自动建表（全新库直接建出全部表；已有库用 `baseline-on-migrate` 标记现状，不动数据）。
> 若想手动单独跑 embedding：`server/embedding-service/start.bat`

**④ 启动前台 / 后台**

```powershell
cd web   ; npm install ; npm run dev    # 前台 http://localhost:5173
cd admin ; npm install ; npm run dev    # 后台 http://localhost:5174
```

**⑤（可选）启动监控栈**

```powershell
cd monitoring
docker compose up -d                     # Prometheus :9090 · Grafana :3000
```

---

## 5. 访问入口

| 服务 | 地址 | 说明 |
|------|------|------|
| 导购前台 | http://localhost:5173 | 欢迎页 → 游客进入 → 商品页 + AI 导购浮窗 |
| **管理后台** | http://localhost:5174 | 概览 / 用户 / 会话 / 订单 / 行为 / 日志 / 评测 / 监控 |
| 后端 API | http://localhost:8080/api/... | |
| 商品图片 | http://localhost:8080/images/... | |
| Prometheus | http://localhost:9090 | 指标查询、targets 状态 |
| Grafana | http://localhost:3000 | 看板（默认 `admin/admin`，首次登录请改） |

## 6. 管理后台

- 入口独立于导购前台，需 **`role=ADMIN`** 才能登录。
- 注册任意账号后提权：
  ```sql
  UPDATE users SET role = 'ADMIN' WHERE username = '你的用户名';
  ```
- 能力：业务数据只读查询、**日志**（grep 式命令查询 + 点击展开全文）、**评测**（每次迭代的分数趋势）、**监控**（内嵌 Grafana）。

## 7. 可观测性

- 后端经 **Actuator + Micrometer** 暴露 `/actuator/prometheus`。
- 业务指标：`rag.retrieval.duration`、`llm.first_token.duration`、`llm.total.duration`、`sse.active.connections`、`cart.tool.calls`。
- `monitoring/` 下一键起 Prometheus + Grafana，看板已预置（详见 `docs/Docker监控搭建.md`）。

## 8. 说明

- 前端/后台通过 Vite 代理把 `/api` 转发到 `8080`，本地开发无需处理跨域（后端 CORS 已放行 5173/5174）。
- 鉴权为**可选**：游客用 `sessionId`（localStorage 生成），登录后用 `Authorization: Bearer`。
- 数据库结构由 **Flyway** 版本化管理：结构变更请新增 `server/src/main/resources/db/migration/V{n}__xxx.sql`，**不要手工改线上**。
- 本地 embedding 服务**首次启动**会安装依赖 / 下载 Chinese-CLIP 模型（较慢），之后走缓存。
- 更多细节见 `docs/`：`开发复盘.md`、`Bug记录.md`、`管理后台技术选型.md`、`Docker监控搭建.md`、`LLM-as-Judge.md`。
