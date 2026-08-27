"""
本地多模态 Embedding 服务

基于 Chinese-CLIP (chinese-clip-vit-base-patch16)，提供文本 / 图片 / 跨模态向量化接口。
设计目标：完全离线、零外部 API 调用、永久免费。

启动方式见 start.bat 或 README.md。
"""

import os
import io
import base64
import logging
from typing import List, Optional

# 必须在 import transformers 之前设置（虽然 Python 已加载 transformers 后没效果，但留作兜底）
os.environ.setdefault("HF_HOME", "D:/JavaCode/RagAgent/server/embedding-service/models")
os.environ.setdefault("HF_HUB_CACHE", "D:/JavaCode/RagAgent/server/embedding-service/models")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "0")  # 首次必须联网下模型

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import torch
from PIL import Image

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("embedding-service")

# 强制 stdout 不缓冲（否则在 uvicorn 子进程里 print 不会立即显示）
import sys
sys.stdout.reconfigure(line_buffering=True)
sys.stderr.reconfigure(line_buffering=True)

MODEL_NAME = "OFA-Sys/chinese-clip-vit-base-patch16"

print("=" * 60, flush=True)
print(f"Loading model: {MODEL_NAME}", flush=True)
print(f"HF_HOME = {os.environ.get('HF_HOME')}", flush=True)
print("=" * 60, flush=True)

try:
    from transformers import ChineseCLIPModel, ChineseCLIPProcessor
    model = ChineseCLIPModel.from_pretrained(MODEL_NAME)
    processor = ChineseCLIPProcessor.from_pretrained(MODEL_NAME)
    model.eval()
    EMBED_DIM = int(model.config.projection_dim)
    print(f"Model loaded. Embedding dim = {EMBED_DIM}", flush=True)
except Exception as e:
    print(f"Failed to load model: {e}", flush=True)
    raise

app = FastAPI(title="Local Embedding Service", version="1.0.0")


# ==================== 请求模型 ====================

class TextRequest(BaseModel):
    text: str

class BatchTextRequest(BaseModel):
    texts: List[str]

class ImageRequest(BaseModel):
    image_base64: str
    mime_type: Optional[str] = None  # png / jpeg


# ==================== 工具函数 ====================

def _normalize(t: torch.Tensor) -> torch.Tensor:
    """L2 归一化"""
    return torch.nn.functional.normalize(t, p=2, dim=-1)


def _to_python_list(t: torch.Tensor) -> list:
    """转 Python float list（去除 batch 维）"""
    return t[0].tolist() if t.dim() == 2 else t.tolist()


# ==================== API 端点 ====================

@app.get("/health")
def health():
    """健康检查 + 模型元信息"""
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "dim": EMBED_DIM,
    }


@app.post("/embed/text")
def embed_text(req: TextRequest):
    """文本向量化"""
    if not req.text:
        raise HTTPException(status_code=400, detail="text is required")
    try:
        inputs = processor(
            text=req.text,
            padding=True,
            truncation=True,
            max_length=128,
            return_tensors="pt",
        )
        with torch.no_grad():
            feats = model.get_text_features(**inputs)
        feats = _normalize(feats)
        return {"vector": feats[0].tolist(), "dim": feats.shape[-1]}
    except Exception as e:
        log.exception("embed_text failed")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/embed/image")
def embed_image(req: ImageRequest):
    """图片向量化（base64 上传）"""
    try:
        image_bytes = base64.b64decode(req.image_base64)
    except Exception:
        raise HTTPException(status_code=400, detail="invalid base64 image")
    try:
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"image decode failed: {e}")
    try:
        inputs = processor(images=image, return_tensors="pt")
        with torch.no_grad():
            feats = model.get_image_features(**inputs)
        feats = _normalize(feats)
        return {"vector": feats[0].tolist(), "dim": feats.shape[-1]}
    except Exception as e:
        log.exception("embed_image failed")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/embed/batch")
def embed_batch(req: BatchTextRequest):
    """批量文本向量化（用于索引阶段加速）"""
    if not req.texts:
        raise HTTPException(status_code=400, detail="texts is required")
    try:
        inputs = processor(
            text=req.texts,
            padding=True,
            truncation=True,
            max_length=128,
            return_tensors="pt",
        )
        with torch.no_grad():
            feats = model.get_text_features(**inputs)
        feats = _normalize(feats)
        return {"vectors": feats.tolist(), "dim": feats.shape[-1]}
    except Exception as e:
        log.exception("embed_batch failed")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 直接运行 ====================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)