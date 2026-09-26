# Docker + Prometheus + Grafana 可观测性搭建（学习笔记）

> 目标：用 **Docker** 跑起 **Prometheus + Grafana**，给 RagAgent 补上"系统/性能随时间变化"的可观测能力。
> 面向：想顺便学 Docker 的人。所以本文不只写"怎么敲命令"，更写**为什么**。

---

## 0. 先搞清楚定位

| 工具 | 看什么 | 时间维度 |
|---|---|---|
| 日志文件 / 后台"日志"页 | 单次事件的细节（报错栈、关键动作） | 事后翻 |
| 管理后台 | 业务**状态快照**（用户/订单/会话数） | 当前值 |
| **Prometheus + Grafana** | 系统/性能的**时间序列** | 随时间变化、可告警 |

**Docker 不是监控**，它只是"怎么把 Prometheus/Grafana 跑起来"的方式。用它是因为：
- 不用在 Windows 上原生装两个软件、不污染本机
- 一条命令起停，环境干净可复制
- 顺便学到容器/编排/网络/卷这些通用技能

---

## 1. 架构与数据流

```
┌──────────────────────────── 宿主机 (Windows) ────────────────────────────┐
│                                                                          │
│   Spring Boot (8080)                                                     │
│   ├─ /actuator/prometheus   ← Actuator + Micrometer 暴露的指标(文本)      │
│   └─ 业务埋点: rag.retrieval / llm.first_token / llm.total / sse.active   │
│                                                                          │
│   ┌─────────── Docker Desktop ──────────────────────────────┐            │
│   │  rag-prometheus (9090)  ──每15s拉取──►  host.docker.internal:8080   │
│   │        ▲                                                │            │
│   │        │ http://prometheus:9090  (compose 内部服务名)   │            │
│   │  rag-grafana (3000)  ←── 查询 ──►                       │            │
│   └─────────────────────────────────────────────────────────┘            │
└──────────────────────────────────────────────────────────────────────────┘
```

数据流一句话：**后端埋点 → Actuator 暴露 → Prometheus 定时"拉"走并存下来 → Grafana 查询并画图**。

> 关键认知：Prometheus 是 **pull（拉）** 模型——由 Prometheus 主动去目标端点抓，不是后端 push 给它。
> 所以是 Prometheus 的配置里写"去哪抓"，而不是后端配置"往哪推"。

---

## 2. Docker 核心概念（本次用到的）

| 概念 | 一句话 | 本项目对应 |
|---|---|---|
| **镜像 image** | 只读模板（像"安装包"） | `prom/prometheus:v2.55.0`、`grafana/grafana:11.3.0` |
| **容器 container** | 镜像跑起来的实例（像"进程"） | `rag-prometheus`、`rag-grafana` |
| **卷 volume** | 数据持久化，容器删了数据还在 | `prometheus-data`、`grafana-data` |
| **绑定挂载 bind mount** | 把宿主机文件/目录直接挂进容器 | `./prometheus/prometheus.yml:/etc/prometheus/...:ro` |
| **网络 network** | 容器之间互相通信的虚拟网络 | `monitoring_default`（compose 自动建） |
| **端口映射** | `宿主机端口:容器端口` | `9090:9090`、`3000:3000` |
| **compose** | 用一个 yml 声明式编排多个容器 | `docker-compose.yml` |

**卷 vs 绑定挂载**（容易混）：
- `./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro` → **绑定挂载**：把宿主机当前目录的文件挂进去，改完重启容器即生效，`:ro` 表示容器内只读。
- `prometheus-data:/prometheus` → **命名卷**：由 Docker 管理存储位置，用于持久化数据，`docker compose down -v` 才会删。

**容器间怎么互相找到？**
- 同一 compose 内的服务，用**服务名**当主机名：Grafana 配置里直接写 `http://prometheus:9090`。
- 容器要访问**宿主机**（我们的后端在宿主机 8080）：用 `host.docker.internal`（Docker Desktop for Windows/Mac 内置；Linux 需要 `extra_hosts: ["host.docker.internal:host-gateway"]`，本项目已加，跨平台通用）。

---

## 3. 目录结构

```
monitoring/
├─ docker-compose.yml                       # 编排：两个容器 + 两个数据卷
├─ prometheus/
│  └─ prometheus.yml                        # 抓取配置：去哪抓、多久抓一次
└─ grafana/
   ├─ provisioning/
   │  ├─ datasources/prometheus.yml         # 自动配置数据源(指向 prometheus 容器)
   │  └─ dashboards/dashboards.yml          # 自动加载看板
   └─ dashboards/
      └─ rag-agent.json                     # 预置看板(8 个面板)
```

> **provisioning（自动配置）** 是 Grafana 的省心点：启动时读这些 yml，自动建好数据源、自动导入看板，
> 不用进 UI 手点。适合"配置即代码"。

---

## 4. 关键配置逐行解读

### 4.1 docker-compose.yml（节选）
```yaml
services:
  prometheus:
    image: prom/prometheus:v2.55.0
    ports: ["9090:9090"]                  # 宿主机9090 → 容器9090
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro   # 配置(bind mount)
      - prometheus-data:/prometheus                                     # 数据(named volume)
    extra_hosts: ["host.docker.internal:host-gateway"]                  # 让容器能访问宿主
    restart: unless-stopped

  grafana:
    image: grafana/grafana:11.3.0
    ports: ["3000:3000"]
    environment:
      GF_SECURITY_ADMIN_USER: admin         # GF_ 前缀 = Grafana 配置项
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - ./grafana/dashboards:/var/lib/grafana/dashboards:ro
      - grafana-data:/var/lib/grafana
    depends_on: [prometheus]

volumes:
  prometheus-data:
  grafana-data:
```
- `depends_on` 只保证**启动顺序**（先起 prometheus），**不保证对方已就绪**。
- `restart: unless-stopped`：异常退出自动重启；手动 `down` 后不再拉起。

### 4.2 prometheus.yml
```yaml
global:
  scrape_interval: 15s        # 每 15 秒抓一次
scrape_configs:
  - job_name: 'rag-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
```
- `targets` 是"抓取目标"，即后端 Actuator 端点。
- 每个 target 在 Prometheus UI 的 **Status → Targets** 里能看到 `UP/DOWN` 和上次错误。

### 4.3 数据源 provisioning
```yaml
datasources:
  - name: Prometheus
    uid: prometheus              # 显式指定 uid，看板 JSON 里才好引用
    type: prometheus
    url: http://prometheus:9090  # 用 compose 服务名访问
    isDefault: true
```

---

## 5. 后端这边做了什么

1. **加依赖**（`pom.xml`）：
   - `spring-boot-starter-actuator`：暴露健康检查/指标端点
   - `micrometer-registry-prometheus`：把指标转成 Prometheus 文本格式
2. **暴露端点**（`application.yml`）：
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics,prometheus
     metrics:
       tags:
         application: rag-server     # 所有指标统一带 application 标签
   ```
3. **业务埋点**（`MetricsService`，用 `MeterRegistry`）：
   | 指标名 | 类型 | 埋点位置 |
   |---|---|---|
   | `rag.retrieval.duration` | Timer | `ChatService` 检索调用前后 |
   | `llm.first_token.duration` | Timer | 流式首个 token 到达时 |
   | `llm.total.duration` | Timer | 整轮生成结束 |
   | `sse.active.connections` | Gauge | chatStream 进入/结束(三路径各减一次) |
   | `cart.tool.calls{result=ok/fail}` | Counter | `CartController` 加购/删车 |

> **Micrometer 命名 → Prometheus 命名**：`rag.retrieval.duration`(Timer) 会变成
> `rag_retrieval_duration_seconds_bucket/_count/_sum`（点变下划线、Timer 加 `_seconds`）。
> 所以 PromQL 里查的是 `rag_retrieval_duration_seconds_*`。

**指标类型速记**：
- **Counter**：只增不减（请求数、错误数）→ 用 `rate()` 看速率
- **Gauge**：可增可减的瞬时值（活跃连接、内存）→ 直接看
- **Timer/Histogram**：耗时分布 → 用 `histogram_quantile()` 算分位数

---

## 6. 常用命令速查

```bash
# 进入目录
cd monitoring

# 启动（-d 后台）
docker compose up -d

# 看容器状态 / 端口
docker compose ps

# 看日志（-f 跟随）
docker compose logs -f prometheus
docker compose logs -f grafana

# 重启单个服务（改完配置后）
docker compose restart prometheus

# 停止（保留数据卷）
docker compose down

# 停止并删除数据卷（慎用，指标/看板偏好会清空）
docker compose down -v

# 拉取镜像
docker compose pull
```

其他有用命令：
```bash
docker ps                      # 正在跑的容器
docker images                  # 本地镜像
docker volume ls               # 数据卷
docker exec -it rag-prometheus sh   # 进容器内部(排障)
docker stats                   # 容器资源占用(CPU/内存)
```

---

## 7. 怎么用（访问入口）

| 服务 | 地址 | 账号 |
|---|---|---|
| Prometheus | http://localhost:9090 | 无 |
| Grafana | http://localhost:3000 | `admin` / `admin` |

- **Prometheus**：`Status → Targets` 看抓取是否 `UP`；顶部查询框直接写 PromQL。
- **Grafana**：左侧 `Dashboards → RAG Agent` 打开预置看板。

### 看板面板
1. SSE 活跃连接
2. 购物车工具调用速率（按 result）
3. HTTP 请求速率（按接口）
4. RAG 检索耗时 P95 / 均值
5. LLM 首 token 延迟 P95 / 均值
6. LLM 整轮耗时 P95 / 均值
7. JVM 堆内存使用
8. HTTP 接口延迟 P95

### 常用 PromQL（可以直接贴到 Prometheus/Grafana）
```promql
# 检索耗时 P95
histogram_quantile(0.95, sum(rate(rag_retrieval_duration_seconds_bucket[5m])) by (le))

# 检索平均耗时
sum(rate(rag_retrieval_duration_seconds_sum[5m])) / sum(rate(rag_retrieval_duration_seconds_count[5m]))

# 当前活跃 SSE 连接
sse_active_connections

# 各接口 QPS
sum(rate(http_server_requests_seconds_count[5m])) by (uri)

# 加购成功率
sum(rate(cart_tool_calls_total{result="ok"}[5m])) / sum(rate(cart_tool_calls_total[5m]))
```

---

## 8. 踩坑记录（真踩过的）

| 坑 | 现象 | 解决 |
|---|---|---|
| **Docker daemon 没起** | `docker` 报 `cannot find the file specified / pipe` | 先启动 **Docker Desktop**，等托盘图标变绿 |
| **容器访问不到宿主机后端** | Prometheus target 一直 `DOWN` | 用 `host.docker.internal` 而非 `localhost`（容器里的 localhost 是容器自己）；Linux 需 `extra_hosts: host-gateway` |
| **Grafana 看板引用不到数据源** | 面板显示 "datasource not found" | provisioning 里给数据源**显式指定 `uid`**，看板 JSON 用同一个 uid |
| **改完配置没生效** | 还是旧配置 | Prometheus 配置是启动时读的，改完要 `docker compose restart prometheus` |
| **数据重启后没了** | 容器重建后指标/看板偏好丢失 | 用**命名卷**持久化（本项目已配 `prometheus-data`/`grafana-data`） |

---

## 9. 一句话总结

> 后端把"发生了什么事"变成**数字**（Micrometer 埋点）→ 暴露成一个**文本端点**（Actuator）→
> Prometheus 定时**拉走存成时间序列** → Grafana **画成图**。
> Docker 负责把 Prometheus/Grafana 干净地跑起来，顺便让你学会容器/编排/卷/网络。

---

## 10. 后续可做（本次未做，避免臃肿）

- **告警**：Alertmanager + 规则（如"LLM 首 token P95 > 3s 持续 5 分钟"）
- **更多埋点**：判官分数趋势、检索命中数分布
- **更多 dashboard**：导入 Grafana 官方的 JVM / Spring Boot 模板（按 ID 导入，免手搓）
- **日志聚合**：Loki（和 Prometheus 同源思路，用于日志检索）
