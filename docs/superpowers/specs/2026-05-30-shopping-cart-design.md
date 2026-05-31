# 购物车功能 — 设计文档

## 目标

为 AI 导购助手增加购物车功能：商品卡片一键加购、顶栏查看购物车、修改数量/删除。

## 设计决策

| 决策 | 选择 |
|---|---|
| 加购方式 | 先做手动按钮（卡片上"加入购物车"），后续加对话驱动 |
| 数据存储 | 纯后端 MySQL，sessionId 关联 |
| 购物车入口 | 聊天页顶栏右侧购物车图标 + 数量角标 |
| 加购反馈 | Toast + 按钮变色（C 方案） |

---

## 一、后端设计

### 1.1 数据库

```sql
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    quantity INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);
```

### 1.2 API 接口

| 方法 | 路径 | 参数 | 返回 |
|---|---|---|---|
| `POST` | `/api/cart/add` | `sessionId`, `productId`, `quantity`(默认1) | `CartItem` |
| `DELETE` | `/api/cart/{id}` | `sessionId` | 200/404 |
| `PUT` | `/api/cart/{id}` | `sessionId`, `quantity` | `CartItem` |
| `GET` | `/api/cart` | `sessionId` | `List<CartItem>`（含商品详情） |

### 1.3 新建文件

```
server/src/main/java/com/ragagent/
├── model/CartItem.java
├── repository/CartRepository.java   (JdbcTemplate)
├── service/CartService.java
└── controller/CartController.java
```

### 1.4 CartItem 模型

```java
public class CartItem {
    private Long id;
    private String sessionId;
    private String productId;
    private int quantity;
    private LocalDateTime createdAt;
    // 联查字段
    private String productTitle;
    private String productBrand;
    private Double productPrice;
    private String productImagePath;
}
```

---

## 二、前端设计

### 2.1 改动文件

| 文件 | 改动 |
|---|---|
| `layout/activity_main.xml` | header 右侧新增购物车图标 + 数量角标 |
| `layout/item_product_card.xml` | 新增"加入购物车"按钮 |
| `ui/ChatAdapter.kt` | ProductViewHolder 加按钮绑定 + Toast 反馈 |
| `viewmodel/ChatViewModel.kt` | 加购物车数量状态（角标用） |
| `network/ApiService.kt` | 新增 4 个购物车 API 方法 |

### 2.2 新建文件

| 文件 | 作用 |
|---|---|
| `ui/CartActivity.kt` | 购物车列表页 |
| `layout/activity_cart.xml` | 购物车页面布局（RecyclerView + 下单按钮） |
| `layout/item_cart_product.xml` | 购物车商品条目（图片 + 名称 + 价格 + 数量 ± + 删除） |
| `ui/CartAdapter.kt` | 购物车 RecyclerView 适配器 |

### 2.3 交互流程

```
加入购物车:
  商品卡片点击"加入购物车"
    → POST /api/cart/add + sessionId + productId
    → 成功 → Toast "✅ 已加入购物车" + 按钮变 "✓ 已添加"
    → 失败 → Toast "添加失败"

查看购物车:
  顶栏购物车图标点击
    → Intent → CartActivity
    → GET /api/cart?sessionId=xxx
    → RecyclerView 渲染列表

修改数量:
  点击 ± 按钮
    → PUT /api/cart/{id}?quantity=N
    → 刷新列表

删除:
  点击删除 / 左滑删除
    → DELETE /api/cart/{id}?sessionId=xxx
    → 刷新列表

下单（本次只做按钮占位）:
  底部"去下单"按钮（暂不实现逻辑）
```

### 2.4 数据流

```
ChatViewModel.sessionId (已有)
  → ApiService.addToCart/removeFromCart/updateQuantity/getCart (新增)
  → CartController → CartService → CartRepository → MySQL
  → 返回 CartItem 或 List<CartItem>
  → UI 更新
```

---

## 三、不做的（后续迭代）

- 对话语音加购（"把这款加到购物车"）
- 下单支付流程
- 商品图片加载到购物车列表
- 多端同步/用户登录

---

## 四、验收标准

- [ ] 商品卡片上出现"加入购物车"按钮
- [ ] 点击加购 → Toast 提示 + 按钮变色
- [ ] 顶栏购物车图标显示数量角标
- [ ] 点击图标进入购物车列表页
- [ ] 列表显示商品名称、价格、数量
- [ ] 可修改数量（±）
- [ ] 可删除商品
- [ ] 加购、删除、改数量均有 API 日志
