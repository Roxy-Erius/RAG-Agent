# RAG 电商导购 Agent — API 接口文档（Day 0-2）

> 后端地址：`http://服务器IP:8080`
> 中文文档界面：`http://服务器IP:8080/doc.html`

---

## 1. 商品详情

```
GET /api/products/{productId}
```

**路径参数**

| 参数 | 类型 | 必填 | 示例 |
|------|------|------|------|
| productId | String | 是 | `p_beauty_001` |

**curl 示例**

```bash
curl http://localhost:8080/api/products/p_beauty_001
```

**成功响应**

```json
{
  "productId": "p_beauty_001",
  "title": "雅诗兰黛特润修护肌活精华露淡纹紧致保湿夜间修护抗初老精华30ml",
  "brand": "雅诗兰黛",
  "category": "美妆护肤",
  "subCategory": "精华",
  "basePrice": 720.00,
  "imagePath": "1_美妆护肤/images/p_beauty_001_live.jpg",
  "marketingDescription": "雅诗兰黛特润修护肌活精华露（小棕瓶）...",
  "createdAt": "2026-05-22T22:00:38",
  "updatedAt": "2026-05-22T22:00:38"
}
```

**错误响应**

| 状态码 | 说明 |
|--------|------|
| 404 | 商品不存在 |

---

## 2. 批量查询商品

```
GET /api/products/batch?ids={id1},{id2},{id3}
```

**查询参数**

| 参数 | 类型 | 必填 | 示例 |
|------|------|------|------|
| ids | String | 是 | `p_beauty_001,p_digital_001,p_food_003` |

**curl 示例**

```bash
curl "http://localhost:8080/api/products/batch?ids=p_beauty_001,p_digital_001"
```

**成功响应**（返回数组，不会因为部分ID不存在而报错）

```json
[
  {
    "productId": "p_beauty_001",
    "title": "雅诗兰黛特润修护肌活精华露...",
    "brand": "雅诗兰黛",
    "category": "美妆护肤",
    "subCategory": "精华",
    "basePrice": 720.00,
    "imagePath": "1_美妆护肤/images/p_beauty_001_live.jpg",
    "marketingDescription": "...",
    "createdAt": "2026-05-22T22:00:38",
    "updatedAt": "2026-05-22T22:00:38"
  },
  {
    "productId": "p_digital_001",
    "title": "华为FreeBuds Pro 3...",
    "brand": "华为",
    "category": "数码电子",
    "subCategory": "蓝牙耳机",
    "basePrice": 899.00,
    "imagePath": "2_数码电子/images/p_digital_001_live.jpg",
    "marketingDescription": "...",
    "createdAt": "2026-05-22T22:00:38",
    "updatedAt": "2026-05-22T22:00:38"
  }
]
```

---

## 3. 向量语义搜索（核心接口）

```
GET /api/products/search?query={自然语言描述}&topK={数量}&category={类目}
```

**查询参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| query | String | **是** | — | 自然语言搜索词，如"适合油皮的洗面奶" |
| topK | int | 否 | `3` | 返回的商品数量 |
| category | String | 否 | 不限定 | 限定类目：`美妆护肤` / `数码电子` / `服饰运动` / `食品生活` |

**内部流程**

```
用户输入 query
  → EmbeddingService 调用 Doubao-embedding-vision 转为 2048 维向量
  → RetrieverService 在 ChromaDB products_text 中做余弦相似度检索
  → （可选）按 category 元数据过滤
  → 返回 Top-K 商品ID
  → 通过 ProductRepository 查出完整商品信息
```

**curl 示例**

```bash
# 基本搜索
curl "http://localhost:8080/api/products/search?query=保湿面霜&topK=3"

# 限定类目
curl "http://localhost:8080/api/products/search?query=蓝牙耳机&topK=5&category=数码电子"
```

**成功响应**

```json
[
  {
    "productId": "p_beauty_012",
    "title": "薇诺娜舒敏保湿特护霜50g...",
    "brand": "薇诺娜",
    "category": "美妆护肤",
    "subCategory": "面霜",
    "basePrice": 268.00,
    "imagePath": "1_美妆护肤/images/p_beauty_012_live.jpg",
    "marketingDescription": "...",
    "createdAt": "2026-05-22T22:00:38",
    "updatedAt": "2026-05-22T22:00:38"
  },
  {
    "productId": "p_beauty_008",
    "title": "玉兰水光肌保湿水面霜50g...",
    "brand": "玉兰",
    "category": "美妆护肤",
    "subCategory": "面霜",
    "basePrice": 198.00,
    "imagePath": "1_美妆护肤/images/p_beauty_008_live.jpg",
    "marketingDescription": "...",
    "createdAt": "2026-05-22T22:00:38",
    "updatedAt": "2026-05-22T22:00:38"
  }
]
```

**无匹配结果时返回空数组 `[]`**（不是报错）

---

## 4. System Prompt 模板（供 Day 3 RAG 链路使用）

> 以下为你提供 LLM 调用时的 System Prompt 框架参考，具体实现将在 Day 3-4 完成。

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
{context}
```
