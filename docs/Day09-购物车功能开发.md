# Day 09 — 购物车功能开发

## 一、设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 加购方式 | 手动按钮（先做），后续加对话驱动 | 分步实现，不互相阻塞 |
| 数据存储 | 纯后端 MySQL，sessionId 关联 | 复用现有 JDBC + HikariCP 基础设施 |
| 购物车入口 | 聊天页顶栏右侧购物车图标 + 数量角标 | 跟 header 风格一致，不影响聊天主流程 |
| 加购反馈 | Toast + 按钮变色 ✓ | 即时确认 + 持久状态 |

## 二、后端实现

### 2.1 数据库

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

- `session_id` 关联聊天会话，无需用户登录
- 同商品重复加购 → 累加数量，不新增行

### 2.2 API 接口

| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| `POST` | `/api/cart/add` | `sessionId`, `productId`, `quantity` | 加购（同商品累加） |
| `DELETE` | `/api/cart/{id}` | `sessionId` | 删除单项 |
| `PUT` | `/api/cart/{id}` | `sessionId`, `quantity` | 修改数量（≤0 删除） |
| `GET` | `/api/cart` | `sessionId` | 购物车列表（LEFT JOIN 含商品详情） |

### 2.3 新建文件

```
server/src/main/java/com/ragagent/
├── model/CartItem.java              ← 模型（含联查字段）
├── repository/CartRepository.java   ← JdbcTemplate CRUD + 同商品累加
├── service/CartService.java         ← 业务层 + 日志
└── controller/CartController.java   ← REST 接口 + 日志
```

---

## 三、前端实现

### 3.1 新建文件

| 文件 | 作用 |
|---|---|
| `ui/CartActivity.kt` | 购物车列表页 |
| `ui/CartAdapter.kt` | RecyclerView 适配器 |
| `layout/activity_cart.xml` | 购物车页面布局 |
| `layout/item_cart_product.xml` | 购物车商品条目 |
| `drawable/bg_cart_badge.xml` | 角标背景（oval accent） |

### 3.2 改动文件

| 文件 | 改动 |
|---|---|
| `network/ApiService.kt` | 新增 `CartItemDto` + 4 个购物车方法 |
| `viewmodel/ChatViewModel.kt` | 新增 `cartCount` StateFlow + `addToCart()` |
| `ui/ChatAdapter.kt` | `ProductViewHolder` + `inner` 改 `inner class`，加购按钮状态切换 |
| `ui/MainActivity.kt` | Toast + 购物车角标绑定 + 图标点击跳转 |
| `layout/activity_main.xml` | header 改为 RelativeLayout + 购物车图标 |
| `layout/item_product_card.xml` | 新增"加入购物车"按钮 |
| `AndroidManifest.xml` | 注册 CartActivity |

### 3.3 按钮状态切换

```
点击 "加入购物车"
  → addedProductIds.add(productId)
  → onAddToCart(product)
  → bind(product) 重新渲染
  → 文字变成 "✓ 已加入购物车"（暖金色，透明背景）
```

重复点击已添加的按钮不会重复加购。

---

## 四、交互流程

```
商品卡片点击 "加入购物车"
  → POST /api/cart/add
  → 成功: Toast "✅ 已加入购物车" + 按钮变 "✓ 已加入购物车"
  → 失败: Toast "添加失败"

顶栏购物车图标点击
  → Intent → CartActivity
  → GET /api/cart?sessionId=xxx
  → RecyclerView 渲染列表

购物车列表:
  → 修改数量 ± → PUT /api/cart/{id}
  → 删除 → DELETE /api/cart/{id}
  → 去下单 → Toast "下单功能开发中"（占位）
```

---

## 五、Commit 历史

```
e0f88fe ui: update cart button to show checkmark after adding
0e09d72 fix: cart button toggles to checked state after adding product
3470683 feat: add CartActivity, CartAdapter and cart layouts
2e5d76f feat: add cart icon with badge to main header
f6428d8 feat: add cart state to ChatViewModel
6d35586 feat: add cart button to product card with Toast feedback
1557a64 feat: add cart API methods to ApiService
a9dcedc feat: add CartController REST endpoints
77e727a feat: add CartService with business logic
ee8ba18 feat: add CartRepository with CRUD operations
cc03081 feat: add CartItem model
```

共 11 个 commit，覆盖后端 4 文件 + 前端 8 文件。

---

## 六、验收结果

| 验收项 | 结果 |
|---|---|
| 商品卡片上出现"加入购物车"按钮 | ✅ |
| 点击加购 → Toast 提示 + 按钮变色 | ✅ |
| 顶栏购物车图标显示数量角标 | ✅ |
| 点击图标进入购物车列表页 | ✅ |
| 列表显示商品名称、价格、数量 | ✅ |
| 可修改数量（±） | ✅ |
| 可删除商品 | ✅ |
| 加购、删除、改数量均有 API 日志 | ✅ |
