# Day 14 — V2/V3 推荐系统补全

## 一、V2 语义推荐

### 设计

`RetrieverService.retrieveByText()` — 拼接历史消息 → ChromaDB query_embeddings → 语义检索 Top 3 商品。

降级链：V2 Exception → V1 关键词匹配。

## 二、V3 行为加权推荐

### 2.1 核心算法

新建 `RecommendationService`：

```
user_behaviors 表
  ├── VIEW    × 1.0
  ├── CART    × 3.0
  └── PURCHASE × 5.0
         ×
  时间衰减 = e^(-λ·days)  (λ=0.05)
         ↓
  品类得分聚合 → Top 3 品类 → 每品类 Top N 商品
```

降级链：V3 → V2 → V1。

### 2.2 服务端新文件

| 文件 | 说明 |
|------|------|
| `service/RecommendationService.java` | 行为加权推荐核心 |
| `repository/UserBehaviorRepository.java` | 行为记录 + 查询（@PostConstruct 建表） |
| `controller/RecommendationController.java` | GET /api/recommendations |
| `controller/BehaviorController.java` | POST /api/behaviors/record |
| `model/UserBehavior.java` | 行为模型 |

### 2.3 客户端 API

| 方法 | 说明 |
|------|------|
| `getRecommendations()` | 获取个性化推荐 |
| `recordBehavior(productId, actionType)` | 记录 VIEW/CART 行为 |

## 三、已购过滤

`ProductRepository.findPurchasedProductIds()` — 从 purchase_history 查已购 → V2/V3 过滤 → 插入同品类/同价位段替代品。

## 四、推荐卡片 Adapter 修复

滚动中 Adapter 反复 new ViewHolder 导致闪退 → 改为复用。

## 五、提交记录（2026-06-03）

```
3f05813 docs: 更新优化清单 — V1-V3 已完成，补充附带完成项
afc5a60 fix: V3/V2 推荐过滤已购买商品，推荐相似替代品而非已购
0d876b3 feat: V3 行为加权推荐 + 时间衰减，降级链 V3→V2→V1
159541c fix: 推荐卡片 adapter 复用，避免滚动中新建导致闪退
39c837f feat: V2 语义推荐 — 历史消息 Embedding 检索替代关键词匹配，V1 降级保留
```

5 commits.
