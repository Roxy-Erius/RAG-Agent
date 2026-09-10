# RagAgent · 电商导购 AI Agent

> 无职转生~到了ai全栈大赛就拿出真本事 团队出品

基于 RAG 的**多模态电商导购系统**：电商前台（浏览 / 搜索 / 购物车 / 订单）+ 全局内嵌的 **AI 导购助手**（SSE 流式对话 + 商品卡片 + 加购）。

> 本分支（`dev_zr`）已把前端从 Android 重构为 **React Web**，电商为主干、Agent 为粘在页面上的导购员。

---

## 1. 项目结构

```
RagAgent/
├── server/                    Spring Boot 后端（RAG 检索 / SSE 对话 / 商品·购物车·订单 / JWT 鉴权）
│   └── embedding-service/     本地多模态 Embedding 服务（Chinese-CLIP，端口 8001，随后端自动拉起）
├── web/                       React Web 前端（电商前台 + 全局 AI 导购浮窗）
├── client/                    Android 客户端（历史存档，不再维护）
├── docs/                      开发文档（含开发复盘、技术选型、接口文档）
├── ecommerce_agent_dataset/   商品数据集（图片 + JSON）——不在仓库内，需另行放置
├── start-dev.bat / .ps1       一键启动脚本
└── README.md
```

## 2. 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 3 · JDK 17 · MySQL · ChromaDB · 本地 Chinese-CLIP Embedding · OpenAI 兼容 Chat 模型（当前 DeepSeek） |
| 前端 | React 19 · Vite · TypeScript · Tailwind CSS · React Router · Zustand · Axios |
| 检索 | 多模态 RRF 融合：`text→text` + `text→image`（CLIP 跨模态） |

## 3. 环境要求

- **JDK 17+**
- **Maven 3.8+**
- **Node.js 18+**（含 npm）
- **Python 3.10+**（本地 embedding 服务用；后端会自动拉起）
- **MySQL** 与 **ChromaDB**（可用远程，见配置文件）

---

## 4. 快速开始

### 方式 A：一键启动（推荐）

双击 `start-dev.bat`，或在仓库根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-dev.ps1
```

脚本会自动：检查环境 → 安装前端依赖（首次）→ 启动后端（**自动拉起本地 embedding**）→ 启动前端。

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

- 后端：http://localhost:8080
- 本地 Embedding：http://localhost:8001（后端自动启动，无需手动）

> 若想手动单独跑 embedding：`server/embedding-service/start.bat`

**④ 启动前端**

```powershell
cd web
npm install
npm run dev
```

- 前端：http://localhost:5173

---

## 5. 访问入口

| 服务 | 地址 |
|------|------|
| 前端（欢迎页 → 游客进入 → 商品页） | http://localhost:5173 |
| 后端 API | http://localhost:8080/api/... |
| 商品图片 | http://localhost:8080/images/... |

## 6. 说明

- 前端通过 Vite 代理把 `/api` 转发到 `8080`（见 `web/vite.config.ts`），本地开发无需处理跨域。
- 鉴权为**可选**：游客用 `sessionId`（前端 localStorage 生成），登录后用 `Authorization: Bearer`。
- 本地 embedding 服务**首次启动**会安装依赖 / 下载 Chinese-CLIP 模型（较慢），之后走缓存。
- 更多细节见 `docs/`：`开发复盘.md`（进展与坑）、`Day17-Web端技术选型.md`（前端选型）、`API接口文档.md`（接口）。
