# 本地多模态 Embedding 服务

基于 **Chinese-CLIP (chinese-clip-vit-base-patch16)** 的本地向量化服务，替代火山引擎 `doubao-embedding-vision`。

> 完全离线、零外部 API、永久免费。设计文档见 `docs/Day16-本地多模态Embedding服务.md`。

---

## 1. 前置条件

| 组件 | 版本 |
|------|------|
| Python | 3.10+（推荐 3.11/3.12） |
| 磁盘空间 | ≥ 2GB（模型 600MB + torch 200MB + 缓存） |
| 内存 | ≥ 4GB（模型常驻 ~1GB） |
| 网络 | 首次启动需要联网下载模型；之后离线可用 |

> GPU 不是必需 —— 文本/单张图片推理 CPU 也够用。有 N 卡的话图片批量索引会快很多（详见 §5）。

---

## 2. 启动

### Windows

```cmd
:: 双击或运行：
start.bat
```

脚本会自动：创建虚拟环境 → 装依赖 → 启动服务。

### Linux / macOS

```bash
cd server/embedding-service

python3.11 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# 重定向 HF 缓存到本目录（可选，默认会进 ~/.cache/huggingface）
export HF_HOME="$(pwd)/models"
export HF_HUB_CACHE="$(pwd)/models"

uvicorn main:app --host 0.0.0.0 --port 8001
```

---

## 3. 验证

服务启动后（首次启动会有几分钟模型下载），开新终端测试：

```bash
# 健康检查
curl http://localhost:8001/health
# 期望：{"status":"ok","model":"OFA-Sys/chinese-clip-vit-base-patch16","dim":512}

# 文本向量化
curl -X POST http://localhost:8001/embed/text \
     -H "Content-Type: application/json" \
     -d '{"text":"保湿面霜"}'
# 期望：{"vector":[0.xxx, ...] ,"dim":512}

# 批量文本
curl -X POST http://localhost:8001/embed/batch \
     -H "Content-Type: application/json" \
     -d '{"texts":["保湿面霜","补水保湿精华","油皮洗面奶"]}'

# 图片向量化（base64）
python -c "import base64; print('{\"image_base64\":\"' + base64.b64encode(open('test.jpg','rb').read()).decode() + '\"}')" \
  | curl -X POST http://localhost:8001/embed/image \
         -H "Content-Type: application/json" -d @-
```

---

## 4. API 一览

| 端点 | 方法 | 用途 |
|------|------|------|
| `/health` | GET | 健康检查 + 模型元信息 |
| `/embed/text` | POST `{"text":"..."}` | 单条文本向量化 |
| `/embed/image` | POST `{"image_base64":"...","mime_type":"jpeg"}` | 单张图片向量化 |
| `/embed/batch` | POST `{"texts":["a","b",...]}` | 批量文本向量化（索引加速） |

**所有向量都做了 L2 归一化**，可用内积代替余弦做相似度计算。

**响应格式（统一）**：
```json
{
  "vector": [0.012, -0.034, ...],
  "dim": 512
}
```
批量端点返回 `{"vectors": [[...],[...]], "dim": 512}`。

---

## 5. GPU 加速（可选）

如果机器有 NVIDIA 显卡：

```bash
# 先在 venv 里卸载 CPU 版
pip uninstall torch -y

# 装 CUDA 12.1 版本
pip install torch --index-url https://download.pytorch.org/whl/cu121
```

重启服务后，启动日志会显示 `Using cuda`。图片索引速度提升 5-10x。

---

## 6. 故障排查

### 启动报 `ModuleNotFoundError: No module named 'fastapi'`

→ 虚拟环境没激活。手动执行：
```cmd
cd server\embedding-service
venv\Scripts\activate
pip install -r requirements.txt
```

### 启动卡在 "Loading model..." 不动

→ 首次启动要从 HuggingFace 下载 ~600MB 模型。**`start.bat` 默认已开启国内镜像 `hf-mirror.com`**，开箱即用。

如果还是慢/卡，**优先怀疑两种情况**：
1. 镜像本身也限速 → 多等几分钟，或挂代理
2. 之前下载到一半中断过 → `models/` 里有损坏文件，删掉重新下：

```cmd
rmdir /s /q models
```

如果镜像挂了想换回官方：
```cmd
:: start.bat 顶部把 set "HF_ENDPOINT=https://hf-mirror.com" 注释掉（加 rem ）
rem set "HF_ENDPOINT=https://hf-mirror.com"
```

或者用 `huggingface-cli` 手动下：
```bash
pip install huggingface-cli
HF_ENDPOINT=https://hf-mirror.com huggingface-cli download OFA-Sys/chinese-clip-vit-base-patch16 --local-dir ./models/chinese-clip-vit-base-patch16
```

### 启动报 `Connection error` 或 `trust_remote_repo`

→ transformers 版本太新，4.46 改了远程加载策略。在 `main.py` 顶部加：
```python
from transformers import AutoConfig
AutoConfig.from_pretrained(MODEL_NAME, trust_remote_code=True)
```
或降级到 `transformers==4.40.0`。

### 端口 8001 被占用

→ 改端口：
```cmd
:: start.bat 最后一行改成
python -m uvicorn main:app --host 0.0.0.0 --port 8002
```
同步改 `application.yml` 的 `embedding.base-url: http://localhost:8002`。

### 内存爆掉 / 进程被杀

→ 模型本身 ~1GB。如果机器只有 4GB 内存：
- 关掉其他大型程序
- 用更小的模型（但需要重新索引数据）

### Java 端报 `Connection refused`

→ 服务没起来。先 `curl http://localhost:8001/health` 看 Python 服务是否在跑。

### 检索结果变差

→ chinese-clip 维度是 512，旧的 doubao 是 1024。**必须重新索引** ChromaDB（见 PRD §8）。
没重新索引直接搜会全空或乱七八糟。

---

## 7. 维护

### 模型升级

把 `main.py` 里的 `MODEL_NAME` 换成：
- `OFA-Sys/chinese-clip-vit-large-patch14` —— 精度更高，1.2GB
- `OFA-Sys/chinese-clip-vit-large-patch14-336px` —— 更高分辨率

注意：换模型后 **向量维度可能变**，必须重新索引 ChromaDB。

### 清理缓存

```cmd
:: 关掉服务后
rmdir /s /q models
:: 下次启动会重新下载
```

---

## 8. 目录结构

```
server/embedding-service/
├── main.py              ← FastAPI 应用
├── requirements.txt     ← Python 依赖
├── start.bat            ← Windows 一键启动脚本
├── README.md            ← 本文件
├── models/              ← 模型缓存（HF_HOME 重定向到此，gitignored）
└── venv/                ← Python 虚拟环境（gitignored）
```