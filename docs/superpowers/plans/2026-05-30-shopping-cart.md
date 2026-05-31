# 购物车功能 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AI 导购助手增加购物车功能：商品卡片一键加购、顶栏购物车图标、列表管理（数量/删除）。

**Architecture:** 后端 MySQL → CartRepository(JdbcTemplate) → CartService → CartController(REST)，前端 ApiService → ChatViewModel → Adapter → Activity。

**Tech Stack:** Spring Boot 3.2.5 + JdbcTemplate + Android Kotlin + OkHttp + Gson + ViewBinding + RecyclerView

---

### Task 1: 创建 cart_items 数据库表

**Files:**
- Create: 直接执行 SQL（无需文件）

- [ ] **Step 1: 执行建表 SQL**

在 MySQL 中执行：

```sql
USE rag_agent;
CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    quantity INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 验证**

```bash
echo "DESC cart_items; SELECT COUNT(*) FROM cart_items;" | mysql -h 159.75.105.25 -u rag_agent -p rag_agent
```

Expected: 表存在，0 条数据。

---

### Task 2: 创建 CartItem 模型

**Files:**
- Create: `server/src/main/java/com/ragagent/model/CartItem.java`

- [ ] **Step 1: 编写 CartItem.java**

```java
package com.ragagent.model;

import java.time.LocalDateTime;

public class CartItem {
    private Long id;
    private String sessionId;
    private String productId;
    private int quantity;
    private LocalDateTime createdAt;
    // 联查字段 — 查询购物车列表时填充
    private String productTitle;
    private String productBrand;
    private Double productPrice;
    private String productImagePath;

    public CartItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getProductTitle() { return productTitle; }
    public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
    public String getProductBrand() { return productBrand; }
    public void setProductBrand(String productBrand) { this.productBrand = productBrand; }
    public Double getProductPrice() { return productPrice; }
    public void setProductPrice(Double productPrice) { this.productPrice = productPrice; }
    public String getProductImagePath() { return productImagePath; }
    public void setProductImagePath(String productImagePath) { this.productImagePath = productImagePath; }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd server && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/ragagent/model/CartItem.java
git commit -m "feat: add CartItem model"
```

---

### Task 3: 创建 CartRepository

**Files:**
- Create: `server/src/main/java/com/ragagent/repository/CartRepository.java`

- [ ] **Step 1: 编写 CartRepository.java**

```java
package com.ragagent.repository;

import com.ragagent.model.CartItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class CartRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<CartItem> ROW_MAPPER = (rs, rowNum) -> {
        CartItem item = new CartItem();
        item.setId(rs.getLong("id"));
        item.setSessionId(rs.getString("session_id"));
        item.setProductId(rs.getString("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        // 联查字段（可能为 null）
        try { item.setProductTitle(rs.getString("title")); } catch (Exception e) {}
        try { item.setProductBrand(rs.getString("brand")); } catch (Exception e) {}
        try { item.setProductPrice(rs.getDouble("base_price")); } catch (Exception e) {}
        try { item.setProductImagePath(rs.getString("image_path")); } catch (Exception e) {}
        return item;
    };

    private static final RowMapper<CartItem> PLAIN_MAPPER = (rs, rowNum) -> {
        CartItem item = new CartItem();
        item.setId(rs.getLong("id"));
        item.setSessionId(rs.getString("session_id"));
        item.setProductId(rs.getString("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return item;
    };

    public CartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CartItem add(String sessionId, String productId, int quantity) {
        // 先查是否已存在同一商品
        List<CartItem> existing = jdbc.query(
                "SELECT * FROM cart_items WHERE session_id = ? AND product_id = ?",
                PLAIN_MAPPER, sessionId, productId);
        if (!existing.isEmpty()) {
            // 已存在 → 累加数量
            CartItem item = existing.get(0);
            jdbc.update("UPDATE cart_items SET quantity = quantity + ? WHERE id = ?",
                    quantity, item.getId());
            item.setQuantity(item.getQuantity() + quantity);
            return item;
        }
        // 新增
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO cart_items (session_id, product_id, quantity) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionId);
            ps.setString(2, productId);
            ps.setInt(3, quantity);
            return ps;
        }, keyHolder);
        CartItem item = new CartItem();
        item.setId(keyHolder.getKey().longValue());
        item.setSessionId(sessionId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    public boolean remove(Long id, String sessionId) {
        return jdbc.update("DELETE FROM cart_items WHERE id = ? AND session_id = ?",
                id, sessionId) > 0;
    }

    public CartItem updateQuantity(Long id, String sessionId, int quantity) {
        if (quantity <= 0) {
            remove(id, sessionId);
            return null;
        }
        jdbc.update("UPDATE cart_items SET quantity = ? WHERE id = ? AND session_id = ?",
                quantity, id, sessionId);
        List<CartItem> items = jdbc.query(
                "SELECT * FROM cart_items WHERE id = ?", PLAIN_MAPPER, id);
        return items.isEmpty() ? null : items.get(0);
    }

    public List<CartItem> findBySessionId(String sessionId) {
        return jdbc.query(
                "SELECT c.*, p.title, p.brand, p.base_price, p.image_path " +
                "FROM cart_items c LEFT JOIN products p ON c.product_id = p.product_id " +
                "WHERE c.session_id = ? ORDER BY c.created_at DESC",
                ROW_MAPPER, sessionId);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd server && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/ragagent/repository/CartRepository.java
git commit -m "feat: add CartRepository with CRUD operations"
```

---

### Task 4: 创建 CartService

**Files:**
- Create: `server/src/main/java/com/ragagent/service/CartService.java`

- [ ] **Step 1: 编写 CartService.java**

```java
package com.ragagent.service;

import com.ragagent.model.CartItem;
import com.ragagent.repository.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private final CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    public CartItem addToCart(String sessionId, String productId, int quantity) {
        log.info("加购 | sessionId={} | productId={} | quantity={}", sessionId, productId, quantity);
        return cartRepository.add(sessionId, productId, quantity);
    }

    public boolean removeFromCart(Long id, String sessionId) {
        log.info("删除购物车项 | id={} | sessionId={}", id, sessionId);
        return cartRepository.remove(id, sessionId);
    }

    public CartItem updateQuantity(Long id, String sessionId, int quantity) {
        log.info("修改数量 | id={} | sessionId={} | quantity={}", id, sessionId, quantity);
        return cartRepository.updateQuantity(id, sessionId, quantity);
    }

    public List<CartItem> getCart(String sessionId) {
        log.debug("查询购物车 | sessionId={}", sessionId);
        return cartRepository.findBySessionId(sessionId);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd server && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/ragagent/service/CartService.java
git commit -m "feat: add CartService with business logic"
```

---

### Task 5: 创建 CartController

**Files:**
- Create: `server/src/main/java/com/ragagent/controller/CartController.java`

- [ ] **Step 1: 编写 CartController.java**

```java
package com.ragagent.controller;

import com.ragagent.model.CartItem;
import com.ragagent.service.CartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/add")
    public ResponseEntity<CartItem> add(
            @RequestParam String sessionId,
            @RequestParam String productId,
            @RequestParam(defaultValue = "1") int quantity) {
        log.info("==> POST /api/cart/add | sessionId={} | productId={} | quantity={}",
                sessionId, productId, quantity);
        CartItem item = cartService.addToCart(sessionId, productId, quantity);
        return ResponseEntity.ok(item);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @PathVariable Long id,
            @RequestParam String sessionId) {
        log.info("==> DELETE /api/cart/{} | sessionId={}", id, sessionId);
        boolean removed = cartService.removeFromCart(id, sessionId);
        return removed ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<CartItem> updateQuantity(
            @PathVariable Long id,
            @RequestParam String sessionId,
            @RequestParam int quantity) {
        log.info("==> PUT /api/cart/{} | sessionId={} | quantity={}", id, sessionId, quantity);
        CartItem item = cartService.updateQuantity(id, sessionId, quantity);
        return item != null ? ResponseEntity.ok(item) : ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<CartItem>> list(@RequestParam String sessionId) {
        log.info("==> GET /api/cart | sessionId={}", sessionId);
        return ResponseEntity.ok(cartService.getCart(sessionId));
    }
}
```

- [ ] **Step 2: 编译验证 + 启动测试**

```bash
cd server && mvn compile -q
# 重启后端后测试:
curl -X POST "http://localhost:8080/api/cart/add?sessionId=test&productId=p_beauty_001&quantity=1"
curl "http://localhost:8080/api/cart?sessionId=test"
curl -X DELETE "http://localhost:8080/api/cart/1?sessionId=test"
```

Expected: 加购返回 CartItem JSON，列表返回含商品详情，删除返回 200。

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/ragagent/controller/CartController.java
git commit -m "feat: add CartController REST endpoints"
```

---

### Task 6: 前端 ApiService — 新增购物车方法

**Files:**
- Modify: `client/app/src/main/java/com/ragagent/network/ApiService.kt`

- [ ] **Step 1: 在 ApiService.kt 末尾（最后一个 `}` 之前）新增 4 个方法**

```kotlin
// ========== 购物车 API ==========

data class CartItemDto(
    val id: Long,
    val sessionId: String,
    val productId: String,
    val quantity: Int,
    val productTitle: String?,
    val productBrand: String?,
    val productPrice: Double?,
    val productImagePath: String?
)

suspend fun addToCart(sessionId: String, productId: String, quantity: Int = 1): CartItemDto? =
    withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/cart/add?sessionId=$sessionId&productId=$productId&quantity=$quantity")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { gson.fromJson(it, CartItemDto::class.java) }
            } else null
        } catch (e: Exception) { null }
    }

suspend fun removeFromCart(id: Long, sessionId: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder()
            .url("$baseUrl/api/cart/$id?sessionId=$sessionId")
            .delete()
            .build()
        client.newCall(request).execute().isSuccessful
    } catch (e: Exception) { false }
}

suspend fun updateCartQuantity(id: Long, sessionId: String, quantity: Int): CartItemDto? =
    withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/cart/$id?sessionId=$sessionId&quantity=$quantity")
                .put(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { gson.fromJson(it, CartItemDto::class.java) }
            } else null
        } catch (e: Exception) { null }
    }

suspend fun getCart(sessionId: String): List<CartItemDto> = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder()
            .url("$baseUrl/api/cart?sessionId=$sessionId")
            .build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val json = response.body?.string() ?: "[]"
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java, CartItemDto::class.java).type
            gson.fromJson(json, type)
        } else emptyList()
    } catch (e: Exception) { emptyList() }
}
```

- [ ] **Step 2: Build**

```bash
cd client && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/app/src/main/java/com/ragagent/network/ApiService.kt
git commit -m "feat: add cart API methods to ApiService"
```

---

### Task 7: 前端 — 商品卡片加"加入购物车"按钮

**Files:**
- Modify: `client/app/src/main/res/layout/item_product_card.xml`
- Modify: `client/app/src/main/java/com/ragagent/ui/ChatAdapter.kt`

- [ ] **Step 1: 在 item_product_card.xml 价格行后加按钮**

在 `tvProductBrand` 的 `</TextView>` 之后、内层 `</LinearLayout>` 之前，插入：

```xml
            <TextView
                android:id="@+id/tvAddToCart"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:background="@drawable/bg_tag"
                android:paddingHorizontal="12dp"
                android:paddingVertical="4dp"
                android:text="加入购物车"
                android:textColor="@color/text_primary"
                android:textSize="11sp"
                android:clickable="true"
                android:focusable="true" />
```

- [ ] **Step 2: 在 ChatAdapter.kt 中新增 onAddToCart 回调和按钮绑定**

在 `ChatAdapter` 构造函数中添加参数：

```kotlin
class ChatAdapter(
    private val onProductClick: (Product) -> Unit,
    private val onAddToCart: (Product) -> Unit  // 新增
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {
```

在 `ProductViewHolder` 构造函数中添加参数：

```kotlin
class ProductViewHolder(
    private val binding: ItemProductCardBinding,
    private val onProductClick: (Product) -> Unit,
    private val onAddToCart: (Product) -> Unit  // 新增
) : RecyclerView.ViewHolder(binding.root) {
```

在 `onCreateViewHolder` 的 `VIEW_TYPE_PRODUCT` 分支中传入新参数：

```kotlin
VIEW_TYPE_PRODUCT -> {
    val binding = ItemProductCardBinding.inflate(inflater, parent, false)
    ProductViewHolder(binding, onProductClick, onAddToCart)
}
```

在 `ProductViewHolder.bind()` 末尾添加：

```kotlin
binding.tvAddToCart.setOnClickListener {
    onAddToCart(product)
}
```

- [ ] **Step 3: 在 MainActivity.kt 中传入 onAddToCart 回调**

```kotlin
adapter = ChatAdapter(
    onProductClick = { product ->
        val intent = Intent(this, ProductCardActivity::class.java).apply {
            putExtra(ProductCardActivity.EXTRA_PRODUCT_ID, product.productId)
        }
        startActivity(intent)
    },
    onAddToCart = { product ->
        viewModel.addToCart(product.productId)
        Toast.makeText(this, "✅ 已加入购物车", Toast.LENGTH_SHORT).show()
    }
)
```

- [ ] **Step 4: Build + Commit**

```bash
cd client && ./gradlew assembleDebug
git add client/app/src/main/res/layout/item_product_card.xml \
        client/app/src/main/java/com/ragagent/ui/ChatAdapter.kt \
        client/app/src/main/java/com/ragagent/ui/MainActivity.kt
git commit -m "feat: add 'add to cart' button on product card with callback"
```

---

### Task 8: 前端 — ChatViewModel 加购物车逻辑

**Files:**
- Modify: `client/app/src/main/java/com/ragagent/viewmodel/ChatViewModel.kt`

- [ ] **Step 1: 新增购物车状态和方法**

```kotlin
private val apiService = ApiService()

private val _cartCount = MutableStateFlow(0)
val cartCount: StateFlow<Int> = _cartCount.asStateFlow()

fun addToCart(productId: String) {
    viewModelScope.launch {
        val item = apiService.addToCart(sessionId, productId)
        if (item != null) {
            _cartCount.value += 1
        }
    }
}

fun refreshCartCount() {
    viewModelScope.launch {
        val items = apiService.getCart(sessionId)
        _cartCount.value = items.size
    }
}
```

- [ ] **Step 2: Build + Commit**

```bash
cd client && ./gradlew assembleDebug
git add client/app/src/main/java/com/ragagent/viewmodel/ChatViewModel.kt
git commit -m "feat: add cart state to ChatViewModel"
```

---

### Task 9: 前端 — 顶栏购物车图标

**Files:**
- Modify: `client/app/src/main/res/layout/activity_main.xml`
- Modify: `client/app/src/main/java/com/ragagent/ui/MainActivity.kt`

- [ ] **Step 1: 修改 activity_main.xml 的 headerLayout**

将 headerLayout 从 LinearLayout 改为 RelativeLayout（或 FrameLayout），右侧添加购物车图标：

```xml
<RelativeLayout
    android:id="@+id/headerLayout"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:paddingTop="12dp"
    android:paddingBottom="10dp"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:background="@color/background"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:layout_centerInParent="true">
        <TextView ... 标题 />
        <TextView ... 副标题 />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/btnCart"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_alignParentEnd="true"
        android:layout_centerVertical="true"
        android:gravity="center"
        android:clickable="true"
        android:focusable="true"
        android:background="?attr/selectableItemBackgroundBorderless">

        <RelativeLayout
            android:layout_width="28dp"
            android:layout_height="28dp">
            <!-- 购物车图标 SVG → 用 TextView emoji 占位，后续替换 -->
            <TextView
                android:layout_width="28dp"
                android:layout_height="28dp"
                android:text="🛒"
                android:textSize="18sp"
                android:gravity="center" />
            <TextView
                android:id="@+id/tvCartBadge"
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:layout_alignTop="true"
                android:layout_alignEnd="true"
                android:background="@drawable/bg_cart_badge"
                android:text="0"
                android:textColor="#FFFFFF"
                android:textSize="10sp"
                android:gravity="center"
                android:visibility="gone" />
        </RelativeLayout>
    </LinearLayout>
</RelativeLayout>
```

- [ ] **Step 2: 创建角标 drawable**

```bash
# 创建 bg_cart_badge.xml
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#C47A4A" />
</shape>
```

- [ ] **Step 3: 在 MainActivity.kt 中绑定购物车**

```kotlin
// 观察购物车数量
lifecycleScope.launch {
    viewModel.cartCount.collect { count ->
        binding.tvCartBadge.text = count.toString()
        binding.tvCartBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
    }
}

binding.btnCart.setOnClickListener {
    startActivity(Intent(this, CartActivity::class.java).apply {
        putExtra("sessionId", viewModel.sessionId)
    })
}
```

- [ ] **Step 4: Build + Commit**

```bash
cd client && ./gradlew assembleDebug
```

---

### Task 10: 前端 — 创建 CartActivity + CartAdapter + 布局

**Files:**
- Create: `client/app/src/main/java/com/ragagent/ui/CartActivity.kt`
- Create: `client/app/src/main/java/com/ragagent/ui/CartAdapter.kt`
- Create: `client/app/src/main/res/layout/activity_cart.xml`
- Create: `client/app/src/main/res/layout/item_cart_product.xml`

- [ ] **Step 1: 编写 activity_cart.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/background"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingVertical="8dp"
        android:paddingStart="8dp"
        android:paddingEnd="16dp">
        <ImageButton
            android:id="@+id/btnBack"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@drawable/ic_back" />
        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="购物车"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold" />
    </LinearLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerCart"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:padding="12dp"
        android:clipToPadding="false" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:background="@color/background">
        <TextView
            android:id="@+id/tvTotal"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="合计: ¥0"
            android:textColor="@color/text_primary"
            android:textSize="18sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/btnCheckout"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="@drawable/bg_send_button"
            android:paddingHorizontal="24dp"
            android:paddingVertical="12dp"
            android:text="去下单"
            android:textColor="#FFFFFF"
            android:textSize="15sp" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: 编写 item_cart_product.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:padding="12dp"
    android:layout_marginBottom="8dp"
    android:background="@color/surface"
    android:elevation="2dp">

    <View
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:background="@drawable/bg_product_placeholder" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:paddingStart="12dp">
        <TextView
            android:id="@+id/tvCartProductName"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/text_primary"
            android:textSize="13sp"
            android:maxLines="1"
            android:ellipsize="end" />
        <TextView
            android:id="@+id/tvCartProductPrice"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textColor="@color/accent"
            android:textSize="15sp"
            android:textStyle="bold" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">
        <TextView
            android:id="@+id/btnMinus"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:text="−"
            android:textSize="18sp"
            android:textColor="@color/text_primary"
            android:gravity="center"
            android:background="@drawable/bg_tag" />
        <TextView
            android:id="@+id/tvQuantity"
            android:layout_width="36dp"
            android:layout_height="wrap_content"
            android:text="1"
            android:textSize="15sp"
            android:textColor="@color/text_primary"
            android:gravity="center" />
        <TextView
            android:id="@+id/btnPlus"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:text="+"
            android:textSize="18sp"
            android:textColor="@color/text_primary"
            android:gravity="center"
            android:background="@drawable/bg_tag" />
    </LinearLayout>

    <TextView
        android:id="@+id/btnDelete"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:text="🗑"
        android:textSize="16sp"
        android:gravity="center"
        android:layout_marginStart="8dp" />
</LinearLayout>
```

- [ ] **Step 3: 编写 CartAdapter.kt**

```kotlin
package com.ragagent.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ragagent.databinding.ItemCartProductBinding
import com.ragagent.network.CartItemDto

class CartAdapter(
    private val onQuantityChange: (Long, Int) -> Unit,
    private val onDelete: (Long) -> Unit
) : RecyclerView.Adapter<CartAdapter.ViewHolder>() {

    private var items: List<CartItemDto> = emptyList()

    fun submitList(list: List<CartItemDto>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCartProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemCartProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItemDto) {
            binding.tvCartProductName.text = item.productTitle ?: item.productId
            binding.tvCartProductPrice.text = "¥${item.productPrice ?: 0.0}"
            binding.tvQuantity.text = item.quantity.toString()

            binding.btnPlus.setOnClickListener {
                onQuantityChange(item.id, item.quantity + 1)
            }
            binding.btnMinus.setOnClickListener {
                val newQty = item.quantity - 1
                if (newQty > 0) onQuantityChange(item.id, newQty)
                else onDelete(item.id)
            }
            binding.btnDelete.setOnClickListener { onDelete(item.id) }
        }
    }
}
```

- [ ] **Step 4: 编写 CartActivity.kt**

```kotlin
package com.ragagent.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ragagent.databinding.ActivityCartBinding
import com.ragagent.network.ApiService
import kotlinx.coroutines.launch

class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private val apiService = ApiService()
    private val adapter = CartAdapter(
        onQuantityChange = { id, qty -> updateQuantity(id, qty) },
        onDelete = { id -> deleteItem(id) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerCart.layoutManager = LinearLayoutManager(this)
        binding.recyclerCart.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCheckout.setOnClickListener {
            Toast.makeText(this, "下单功能开发中", Toast.LENGTH_SHORT).show()
        }

        loadCart()
    }

    private fun loadCart() {
        val sessionId = intent.getStringExtra("sessionId") ?: return
        lifecycleScope.launch {
            val items = apiService.getCart(sessionId)
            adapter.submitList(items)
            updateTotal(items)
        }
    }

    private fun updateQuantity(id: Long, quantity: Int) {
        val sessionId = intent.getStringExtra("sessionId") ?: return
        lifecycleScope.launch {
            apiService.updateCartQuantity(id, sessionId, quantity)
            loadCart()
        }
    }

    private fun deleteItem(id: Long) {
        val sessionId = intent.getStringExtra("sessionId") ?: return
        lifecycleScope.launch {
            apiService.removeFromCart(id, sessionId)
            loadCart()
        }
    }

    private fun updateTotal(items: List<com.ragagent.network.CartItemDto>) {
        val total = items.sumOf { (it.productPrice ?: 0.0) * it.quantity }
        binding.tvTotal.text = "合计: ¥%.2f".format(total)
    }
}
```

- [ ] **Step 5: 注册 Activity 到 AndroidManifest.xml**

在 `<application>` 内添加：

```xml
<activity
    android:name=".ui.CartActivity"
    android:exported="false" />
```

- [ ] **Step 6: Build + Commit**

```bash
cd client && ./gradlew assembleDebug
git add client/app/src/main/java/com/ragagent/ui/CartActivity.kt \
        client/app/src/main/java/com/ragagent/ui/CartAdapter.kt \
        client/app/src/main/res/layout/activity_cart.xml \
        client/app/src/main/res/layout/item_cart_product.xml \
        client/app/src/main/AndroidManifest.xml \
        client/app/src/main/res/drawable/bg_cart_badge.xml
git commit -m "feat: add CartActivity, CartAdapter and cart layouts"
```

---

### Task 11: 端到端构建验证

- [ ] **Step 1: 构建后端**

```bash
cd server && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 构建前端**

```bash
cd client && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 手动联调测试**

1. 启动后端
2. 安装 APK 到模拟器
3. 发消息推荐商品 → 商品卡片出现"加入购物车"按钮
4. 点击加购 → 按钮变色 + Toast
5. 顶栏购物车图标角标 +1
6. 点击购物车图标 → 进入购物车列表
7. 修改数量 ± → 列表刷新
8. 删除 → 列表刷新

---

## Task Dependency Graph

```
Task 1 (DB) → Task 2 (Model) → Task 3 (Repo) → Task 4 (Service) → Task 5 (Controller)
                                                                        ↓
Task 6 (ApiService) → Task 7 (card button) → Task 8 (ViewModel) → Task 9 (header icon)
                                                                        ↓
                              Task 10 (CartActivity + Adapter + layouts)
                                                                        ↓
                              Task 11 (Build verify)
```
