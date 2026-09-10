# Day17 - Web 端技术选型

> 将项目从 Android App 转型为 Web 电商平台 + 内嵌 AI 导购 Agent

---

## 1. 背景

### 1.1 发起原因

- App 前端（Kotlin）调试困难，开发体验差
- 个人求职方向是 Java Web / 前端，希望 Web 项目作为核心作品
- 团队已推动至队友仓库 `Roxy-Erius/RAG-Agent`，本地工作在 `dev_zr` 分支

### 1.2 定位调整

从「AI 导购 Chat App」调整为 **「电商购物平台 + 内嵌导购 Agent」**：

```
之前：Agent 是产品主干，商品是对话的附属
现在：电商是主干，Agent 是粘在页面上的导购员
```

理由：
- 真实世界的 AI 导购（淘宝问问、京东助手）都嵌在电商里，不割裂
- 电商（商品/搜索/购物车/订单）是 Java Web 经典主战场，叠加 Agent 便于求职展示
- chat-first 的 AI 应用同质化严重；"电商 + Agent"差异化更强
- 后端已具备电商全链路接口，Web 端主要工作量在前端

### 1.3 目标

- ✅ Web 端覆盖 App 现有全部功能
- ✅ Agent 作为**全局悬浮组件**内嵌，支持 SSE 流式 + 商品卡片事件
- ✅ 后端尽量不动，前端零成本适配现有接口
- ✅ 用比赛数据（MySQL 已有 200 条），不另建数据库

---

## 2. 范围

### 2.1 In Scope

- Web 前端（React + Vite + TypeScript）
- `web/` 目录落地，放在现有仓库内
- Agent 导购悬浮组件（SSE 流式 + 商品卡片）
- 电商页面：商品列表/搜索/详情/购物车/登录/订单

### 2.2 Out of Scope（本期）

- 后端重构（保留当前 Spring Boot 3.2）
- Android App（`client/`）**冻结存档不删**：不再维护，但保留在仓库中作历史存档；`client/` 是独立 Gradle 项目，根目录无聚合构建文件，**不影响 server / web 编译**
- 支付真实接入（沿用现有虚拟支付）
- 多语言 / 国际化（结构上预留，暂不实装）
- SSR / SEO（Vite SPA，迁移成本后续评估）

---

## 3. 技术选型（含调研依据）

### 3.1 结论

| 层面 | 选型 | 理由 |
|------|------|------|
| 框架 | **React 19** | 见 §3.2 调研；Actions / `use` Hook 简化流式与数据加载 |
| 构建 | **Vite 7** | 纯 SPA，与 Spring Boot 前后端分离最搭；HMR/构建速度最优 |
| 语言 | **TypeScript 5（strict）** | 强类型，AI 生成代码时代更稳，求职加分 |
| UI 样式 | **Tailwind CSS v4** | 原子化，AI 生成最友好 |
| 组件库 | **shadcn/ui（Radix 系）** | 可复制、可改源码；接受"自己拼组件"成本换长期可控 |
| 服务端状态 | **TanStack Query v5** | 电商标配：列表缓存/失效、加购乐观更新、请求去重 |
| 客户端状态 | **Zustand**（轻量）| 购物车/会话/上下文足够，不必上 Redux |
| HTTP | **fetch（原）+ Axios** | 普通 REST 走 Axios 拦截器；SSE 走原生 fetch + ReadableStream |
| 路由 | **React Router v7** | 框架模式可选；先用 library 模式，简单 |
| 测试 | **Vitest + React Testing Library + Playwright** | Vitest 与 Vite 同源；Playwright 跑关键链路 E2E |
| 工程化 | **ESLint + Prettier + lint-staged + husky + GitHub Actions** | 求职作品质量门槛 |
| 图表/画布 | 暂无（后续 Agent 编排页需要时用 React Flow）| P1+ |

> 版本说明：以上均为 2026-08 当前稳定版。如团队/教学环境锁旧版，则同步降版本并在 README 注明。

### 3.2 调研依据（2026-08）

**开源 Agent/RAG 项目前端选型**（GitHub 一手证据）：

| 项目 | 前端 | star |
|------|------|------|
| Dify | React（Next.js）| 153k |
| RAGFlow | React（Vite）| 89k |
| Langflow | React（Vite + shadcn + React Flow）| 154k |
| FastGPT | React（Next.js）| 29k |
| AnythingLLM | React（Vite）| 63k |
| LibreChat | React（Vite）| 30k |
| n8n | Vue3 | — |
| OpenWebUI | Svelte | 小众 |
| MaxKB | Vue3 | 22k |
| QAnything（网易）| Vue3 | 14k |

→ **React 系头部项目数量与体量约是 Vue3 的 3~5 倍**。

**招聘市场**（字节、Zoom、软通动力等真实 JD）：
- 明确要求 React/Next.js 的 Agent 前端岗居多
- "React/Vue 皆可"常见
- **只要求 Vue3 的 Agent 前端岗样本为 0**

**结论**：Agent 细分赛道 React 明显主导。选 React 与求职方向直接对齐。

### 3.3 为什么不选 Next.js

- 后端是现成的 Spring Boot，无需同构渲染/SSR
- Vite SPA 简单，构建快，跟前后端分离最匹配
- 电商 SEO 后续要做时，**只迁移商品详情/列表页**到 Next.js（增量改造），不必全栈上 SSR
- 等触发 SEO/首屏指标需求时再演进

### 3.4 为什么不选 Mantine / Ant Design

shadcn/ui 的取舍：
- ✅ 源码在手，无版本绑架；Tailwind 主题统一；与 Dify/RAGFlow 同阵营
- ❌ 原语偏少，电商重组件（表格、规格选择器、评价列表、分页）需自己拼
- 可接受 Phase 1 多花半天写通用组件，换长期可控

> 如 Phase 1 中发现"自写组件远超预期"，切换到 Mantine v7 兜底（API 接近 React Aria，迁移成本可控）。

---

## 4. 架构

### 4.1 整体结构

```
RagAgent 仓库
├── server/   ← Spring Boot 后端（保留，按需微调）
├── client/   ← Android App（存档，不再维护）
├── docs/     ← 文档
└── web/      ← 新增 React Web 前端
    ├── src/
    │   ├── api/          # Axios 封装 + SSE（fetch）客户端
    │   ├── components/   # 通用组件（基于 shadcn/ui）
    │   ├── components/agent/   # 导购 Agent 悬浮组件
    │   ├── pages/        # 路由页面
    │   ├── stores/       # Zustand 客户端状态
    │   ├── queries/      # TanStack Query hooks
    │   ├── types/        # TS 类型定义
    │   ├── lib/          # utils、错误处理、env
    │   └── main.tsx
    ├── tests/
    │   ├── unit/         # Vitest
    │   └── e2e/          # Playwright
    ├── index.html
    ├── package.json
    ├── tsconfig.json
    ├── vite.config.ts
    ├── tailwind.config.ts
    └── .github/workflows/ci.yml
```

### 4.2 与后端对接 — 完整接口矩阵

> 凡 App 端已调用、Web 端也要用的接口全部列出。**未列出 = 本期不要**。

> 鉴权列说明：后端所有接口均为「**可选鉴权**」——不带 token 匿名可用（返回默认数据），带 `Authorization: Bearer` 时 `JwtAuthFilter` 识别出 `userId` 增强数据（关联用户购物车 / 持久化 / 推荐）。Web 端登录后才带 header。

| 能力 | 后端接口 | Web 是否需要 | 现状 | 备注 |
|------|---------|------------|------|------|
| 商品列表 | `GET /api/products?page=&size=` | ✅ | **不存在，需新增** | 首页/分类。后端补列表接口（含分页） |
| 商品搜索（向量）| `GET /api/products/search?query=&topK=&category=` | ✅ | 存在 | 可以走 SSE 触发 |
| 商品详情 | `GET /api/products/{id}` | ✅ | 存在 | |
| 商品批量 | `GET /api/products/batch?ids=...` | ✅ | 存在 | 详情页相关推荐 |
| 商品 SKU | `GET /api/products/{id}/skus` | ✅ | 存在 | 规格选择 |
| 商品评价 | `GET /api/products/{id}/reviews` | ✅ | 存在 | 详情页评价区 |
| 商品 FAQ | `GET /api/products/{id}/faqs` | ✅ | 存在 | 详情页常见问题 |
| 个性化推荐 | `GET /api/recommendations` | ✅ | 存在 | 首页推荐位 |
| SSE 对话 | `GET /api/chat/stream?message=&sessionId=&conversationId=` | ✅（核心）| 存在 | **GET + query string**，非 POST。鉴权可选，见 §4.3 |
| 非流式对话 | `POST /api/chat` | ✅ | 存在 | 备用通道 |
| 清除会话 | `DELETE /api/chat/session/{sessionId}` | ✅ | 存在 | P2 用 |
| 登录 | `POST /api/auth/login` | ✅ | 存在 | 返回 JWT |
| 注册 | `POST /api/auth/register` | ✅ | 存在 | |
| 绑定会话 | `POST /api/auth/link-session?sessionId=...` | ✅ | 存在 | 登录后把匿名 session 绑定到用户 |
| 购物车查询 | `GET /api/cart?sessionId=...` | ✅ | 存在 | |
| 购物车加购 | `POST /api/cart/add` | ✅ | 存在 | 联调时确认 body 格式 |
| 购物车设置 | `POST /api/cart/set` | ✅ | 存在 | 改规格/设数量 |
| 购物车改数量 | `PUT /api/cart/{id}` | ✅ | 存在 | |
| 购物车删除 | `DELETE /api/cart/{id}` | ✅ | 存在 | |
| 订单结算 | `POST /api/orders/checkout` | ✅ | 存在（**需改造**）| 现为模拟支付不落库，见下文 |
| 订单列表 | `GET /api/orders` | ✅ | **不存在，需新增** | 需先落库 + 列表接口 |
| 历史会话 | `GET /api/conversations?q=...` | ✅ | 存在 | P2 |
| 会话消息 | `GET /api/conversations/{id}/messages` | ✅ | 存在 | P2 |
| 删除会话 | `DELETE /api/conversations/{id}` | ✅ | 存在 | P2 |
| 行为上报 | `POST /api/behaviors` | ✅ | 存在 | 浏览/加购埋点，fire-and-forget |

**后端改动清单（集中一次 PR，Phase 0 完成）**：
1. **新增 `GET /api/products` 列表接口**（含分页，首页/分类用）
2. **订单持久化改造**：`checkout` 落订单表 + **新增 `GET /api/orders` 列表接口**
   - 现状：`checkout` 是模拟支付（算总额 → sleep 1s → 删购物车 → 返回 orderId，**不落库**，见 OrderController.java:37）
   - 目标：电商平台需真实订单记录，个人中心"我的订单"才有数据
3. **CORS**：放行 Vite dev（`http://localhost:5173`）+ 生产域
4. **静态资源映射**：暴露商品图片（详见 §4.5），否则首页/详情页图全挂
5. **SSE 鉴权确认**：可选鉴权，登录用户带 Bearer header，匿名兜底（详见 §4.3）

### 4.3 SSE 方案（Phase 0 定稿）

**问题**：浏览器原生 `EventSource` 不支持自定义 Header；Axios 在浏览器里对流式响应支持差。

**决策**：
- Web 端 SSE 用 **`fetch + ReadableStream`** 自实现，自带 buffer 按 `\n\n` 分隔
- **后端接口是 `GET`，参数走 query string**（`message`/`sessionId`/`conversationId`），**不是 POST body**（见 §4.2）
- JWT 通过 **`Authorization: Bearer ...` Header** 传递（可用 fetch 自定义 Header）；**匿名不带 header 也能用**（后端可选鉴权）
- 注：当前 `JwtAuthFilter` 只从 header 读 token（JwtAuthFilter.java:13），不支持 cookie——与 §9 方案 A 一致

**前端封装**（`src/api/sse.ts`）：
```ts
// 伪代码 —— 注意是 GET + query string，不是 POST body
export async function sseChat(params: {
  message: string; sessionId?: string; conversationId?: string; token?: string;
}, onEvent: (e: SSEEvent) => void) {
  const url = new URL('http://localhost:8080/api/chat/stream');
  Object.entries(params).forEach(([k, v]) => { if (v != null) url.searchParams.set(k, v); });

  const headers: Record<string, string> = {};
  if (params.token) headers.Authorization = `Bearer ${params.token}`;

  const res = await fetch(url.toString(), { method: 'GET', headers });
  const reader = res.body!.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    let idx;
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const frame = buf.slice(0, idx);
      buf = buf.slice(idx + 2);
      onEvent(parseFrame(frame));
    }
  }
}
```

### 4.4 CORS 配置

后端新增 `WebMvcConfigurer` 或 `@CrossOrigin`：
- 允许来源：`http://localhost:5173`（Vite dev）+ 生产域名
- 允许方法：GET/POST/DELETE/PUT
- 允许 header：`Authorization`, `Content-Type`
- SSE：`Access-Control-Allow-Origin` 必须，`allowCredentials=true`

### 4.5 商品图片静态资源（Phase 0 必须落地）

**问题**：商品图片相对路径存数据库，Web 端无法直接访问。

**后端最小改动**：`WebConfig` 加一行
```java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/images/**")
            .addResourceLocations("file:" + datasetPath + "/images/");
}
```
- 验证：用 `http://localhost:8080/images/{filename}` 能直接拿到图
- 不改：上传逻辑、不动 DB

---

## 5. Agent 导购悬浮组件（核心亮点）

### 5.1 定位

全局悬浮聊天窗（右下角抽屉），**粘在所有页面之上**，不是独立页面。让 Agent 成为"随逛随问的导购员"。

### 5.2 关键交互

| 场景 | 交互 | 技术点 |
|------|------|------|
| 商品列表页提问 | 用户打开浮窗 → 问"油皮面霜" → Agent 流式回复 + 商品卡片 | SSE + 事件分发 |
| 详情页提问 | 问"这个和 xx 比呢" → 注入当前商品上下文 | 上下文注入 |
| 加购 | Agent 输出 `add_to_cart` 事件 → 浮窗按钮触发 | 事件解析 |
| 跳转 | 点商品卡片 → 跳详情页 | 路由联动 |

### 5.3 SSE 解析（复用后端事件协议）

后端 SSE 事件类型（`ChatService` 现有协议）：
```
{"type":"token","content":"..."}           → 流式文本
{"type":"product","productId":"p_xxx"}     → 商品卡片
{"type":"add_to_cart", ...}                → 加购按钮
{"type":"done", ...}                       → 结束
```

前端解析（区别于 AI 生成代码的方向）：
- `fetch + ReadableStream`（见 §4.3）
- 注释说明 buffer 机制：不完整 JSON 缓冲合并
- 复用 App 端已验证的事件分类逻辑

### 5.4 难点预案

| 难点 | 预案 |
|------|------|
| SSE 解析不完整事件 | 缓冲拼接，等 `\n\n` 分隔符 |
| 流式 + React 渲染卡顿 | 逐 token 追加到 state，微批量（每帧一批）|
| 商品卡片点击与路由联动 | `useNavigate` 全局可用 |
| Agent 上下文感知当前页面 | 页面挂载时把商品上下文写入 store |
| 浮窗关闭再开恢复对话 | 会话 ID 持久化到 sessionStorage |

---

## 6. 页面规划

### 6.1 按优先级

**P0（核心，先做）**：
- 商品列表页 + 搜索
- 商品详情页
- 全局 Agent 悬浮组件（SSE 流式）

**P1（电商闭环）**：
- 登录 / 注册
- 购物车
- 订单确认页

**P2（补全体验）**：
- 个人中心（历史订单 / 评价）
- 历史会话列表
- 结算成功页

### 6.2 路由设计

```
/                 → 商品列表（首页）
/search?q=...     → 搜索结果
/product/:id      → 商品详情
/login            → 登录
/cart             → 购物车
/checkout         → 订单确认
/orders           → 我的订单
（Agent 浮窗全局存在，不进路由）
```

---

## 7. 数据源（沿用比赛数据，不另建库）

| 数据 | 现状 | 用途 |
|------|------|------|
| 商品 200 条 | MySQL `products` 表 | 电商展示 + RAG 数据源 |
| 文本/图片向量 | ChromaDB（512 维）| 语义检索（后端内部用，Web 不直连）|
| 用户/会话/购物车/订单 | MySQL 对应表 | 电商业务 |

- Web 前端只消费后端接口，不直连数据库
- 不新增数据表（除非将来要存 Web 端特有数据，再议）

**已知注意事项**：
- 数据集用户评价多为差评（防幻觉测试设计），详情页评价区做排序/筛选
- ⚠️ **图片静态资源暴露是 P0 阻断**：见 §4.5，Phase 0 必须落地

---

## 8. 实施计划

### Phase 0: 脚手架 + 后端最小适配（1 天）

**前端脚手架**：
- [ ] `web/` 目录：Vite 7 + React 19 + TS 初始化（`strict: true`）
- [ ] Tailwind v4 + shadcn/ui 接入（`init` + 装 5 个起步组件）
- [ ] TanStack Query Provider + Axios 封装 + 环境变量（dev API base）
- [ ] React Router v7 library 模式 + 路由骨架
- [ ] Zustand store 雏形（auth / cart / agent-context）
- [ ] ESLint + Prettier + lint-staged + husky 接入
- [ ] GitHub Actions：`lint` + `typecheck` + `test` + `build`

**SSE 客户端**：
- [ ] `src/api/sse.ts` 实现 fetch + ReadableStream + buffer（§4.3）
- [ ] 最小 demo：`/api/chat/stream` 连通 + 打印 `token` 事件

**后端最小改动**（集中一次 PR，含 §4.2 清单）：
- [ ] 新增 `GET /api/products` 列表接口（分页）
- [ ] 订单落库改造：`checkout` 写订单表 + 新增 `GET /api/orders`
- [ ] CORS 配置（§4.4）
- [ ] 商品图片静态资源映射（§4.5）
- [ ] 验证 SSE 可选鉴权（登录带 header / 匿名兜底）

**冒烟**：
- [ ] 浏览器能渲染首页（即使只是占位）
- [ ] SSE demo 能收到一条 `token`
- [ ] 商品图能加载

### Phase 1: P0 电商骨架 + Agent（2~3 天）

- [ ] 商品列表页（卡片网格 + 搜索 + 分类筛选）
- [ ] 商品详情页（图/规格/评价/FAQ/加购）
- [ ] 全局 Agent 浮窗（SSE 流式 + 商品卡片 + 加购事件 + 路由跳转）
- [ ] Agent 上下文注入（详情页挂载写入 store）

**测试**：
- [ ] 商品列表/详情组件单测
- [ ] SSE 解析单测（mock fetch）

### Phase 2: P1 电商闭环（1~2 天）

- [ ] 登录 / 注册（JWT 存 **localStorage** + Bearer header，见 §9 方案 A）
- [ ] TanStack Query 接购物车：乐观更新加购、回滚失败
- [ ] 购物车页
- [ ] 订单确认页 + 结算

**测试**：
- [ ] 登录流程单测
- [ ] 购物车加购 E2E（Playwright）

### Phase 3: P2 补全 + 体验打磨（2 天）

- [ ] 个人中心（历史订单 / 评价）
- [ ] 历史会话列表（含搜索）
- [ ] 结算成功页
- [ ] 横切关注点：错误/空态/加载骨架屏、表单校验、a11y 基础

### Phase 4: 演示与文档（1~2 天，预留缓冲）

- [ ] 演示脚本 + 录屏
- [ ] README：跑起来步骤、架构图、关键技术点
- [ ] Bug 修复 + 体验细节

> ⚠️ 工期说明：原计划 Phase 3 一日仓促，已拆分 Phase 3 + Phase 4 预留缓冲。如果 P1/P2 卡住，**优先保 P0 + Agent 浮窗完整**，P2 可降级。

---

## 9. 风险与缓解

| 风险 | 影响 | 缓解 | 阶段 |
|------|------|------|------|
| 图片静态资源未暴露 | 详情页/首页图全挂，P0 阻断 | 后端 `addResourceHandlers` 一行配置；Phase 0 必做 + 冒烟 | Phase 0 |
| SSE 鉴权方案不统一 | 联调卡 | Phase 0 定稿 fetch + Bearer Header；后端确认支持 | Phase 0 |
| CORS 配置错误 | 联调卡 | 后端提前加 CORS；冒烟时跨域调试 | Phase 0 |
| shadcn/ui 组件不够用 | Phase 1 速度慢 | 接受自写成本；若超出预期切 Mantine v7（§3.4）| Phase 1 |
| Vite/TS 不熟 | 前期慢 | 骨架用 AI 生成后**逐文件 review**；ESLint 兜底风格 | Phase 0 |
| SSE 流式渲染 | Agent 核心，做好最难 | 提前做最小 demo 验证 fetch 流读取 | Phase 0 |
| Agent 上下文泄漏 | 推荐/对话质量差 | Zustand 严格划分 store；页面卸载清空上下文 | Phase 1 |
| JWT 存 localStorage | XSS 风险 | **方案 A（定稿）**：Phase 2 前端 token 存 localStorage + Bearer header，零后端改动快速跑通；后端无高危接口、鉴权可选，风险可接受。HttpOnly Cookie（方案 B）列为上生产前的安全加固待办，不改本期范围 | Phase 2 |
| 后端小改需重编译 | 联调慢 | 改动清单集中一次 PR；用 `spring-boot-devtools` 热重启 | Phase 0 |
| 工期偏紧 | P2 砍掉 | Phase 3/4 拆分；预留缓冲；P2 可降级 | 全程 |

---

## 10. 决策记录

| 项 | 决策 | 理由 |
|----|------|------|
| 产品形态 | 电商平台 + 内嵌 Agent | 求职 + 差异化 + 后端复用 |
| 前端框架 | React 19 + Vite 7 + TS（strict） | 调研：Agent 赛道 React 3~5 倍于 Vue3；当前稳定版 |
| 服务端状态 | TanStack Query v5 | 电商标配：缓存/失效/乐观更新 |
| UI 体系 | Tailwind v4 + shadcn/ui | 与头部项目（Dify/RAGFlow）同阵营；接受自写成本 |
| 网络层 | Axios（REST）+ fetch（SSE） | SSE 在浏览器必须用 fetch；REST 走 Axios 拦截器 |
| 路由 | React Router v7（library 模式）| 标准方案 |
| 数据源 | 沿用比赛 200 条，不另建库 | 已入库、够用、保持一致 |
| 后端改动 | CORS + 图片静态资源 + SSE 鉴权确认 | 前端先行；改动清单集中一次 PR |
| 鉴权存储 | localStorage + Bearer header（方案 A）| 零后端改动；HttpOnly Cookie 留作生产前加固待办 |
| 测试 | Vitest + RTL + Playwright + GitHub Actions | 求职作品质量门槛 |
| App | `client/` 存档冻结 | 并存不删，不维护 |

---

> **下一步**：确认本选型后，进入 Phase 0（脚手架 + 后端最小适配 + SSE demo）。
