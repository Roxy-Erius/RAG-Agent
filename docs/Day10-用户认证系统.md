# Day 10 — 用户认证系统

## 一、设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 认证方式 | JWT（jjwt 0.12.5） | 无状态，适合移动端，不依赖 Session |
| 密码存储 | BCrypt 哈希 | 不可逆，salt 内建 |
| 购物车绑定 | 登录后迁移 | 匿名购物车 → 登录后自动关联 userId |
| 前端存储 | SharedPreferences | 轻量，重启保留 |
| 加购控制 | 登录门控 | 未登录弹窗 → 跳转登录页 |

## 二、后端实现

### 2.1 数据库

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2.2 API 接口

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 注册 | 无 |
| POST | `/api/auth/login` | 登录，返回 JWT | 无 |
| POST | `/api/auth/link-session` | 匿名会话绑定到用户 | JWT |

### 2.3 核心文件

| 文件 | 说明 |
|------|------|
| `model/User.java` | 用户模型（id, username, passwordHash, createdAt） |
| `model/LoginRequest.java` | 登录 DTO |
| `model/RegisterRequest.java` | 注册 DTO |
| `repository/UserRepository.java` | JdbcTemplate CRUD |
| `service/UserService.java` | BCrypt 密码哈希，注册/登录 |
| `security/JwtUtil.java` | JJWT 令牌生成/验证 |
| `security/JwtAuthFilter.java` | 从 Bearer 头提取 userId |
| `controller/AuthController.java` | 3 个端点 |

### 2.4 购物车改造

`CartController` 中每个端点注入 `HttpServletRequest`，通过 `jwtAuthFilter.getUserId(request)` 判断：

- `userId != null` → 走用户购物车（`cart_items.user_id`）
- `userId == null` → 走匿名购物车（`cart_items.session_id`）

`CartRepository` 新增：
- `findByUserId()` — 用户购物车查询
- `migrateSessionToUser()` — 登录后迁移匿名购物车数据

## 三、前端实现

### 核心文件

| 文件 | 说明 |
|------|------|
| `auth/AuthManager.kt` | SharedPreferences 封装：saveToken / getToken / isLoggedIn / logout / getUsername |
| `network/ApiService.kt` | OkHttp 拦截器自动附加 `Authorization: Bearer <token>`，新增加 login/register/linkSession 方法 |
| `ui/LoginActivity.kt` | 登录页：用户名 + 密码 + 错误提示 |
| `ui/RegisterActivity.kt` | 注册页：用户名 + 密码 + 确认密码 + 校验 |
| `ui/ChatAdapter.kt` | ProductViewHolder 加购按钮点击 → 未登录弹登录对话框 |

### 认证流程

```
匿名用户点击"加入购物车"
  → AlertDialog: "请先登录" → 去登录 / 取消
  → LoginActivity → API /api/auth/login
  → 返回 JWT → AuthManager.saveToken()
  → 调用 /api/auth/link-session 迁移购物车
  → 回到聊天页，可正常加购
```

### 登录/注册按钮

- 使用 `bg_auth_button.xml`：12dp 圆角矩形，暖棕色渐变（`#5C4A3A` → `#8B7355`）
- 区别于发送按钮的椭圆胶囊形状

## 四、依赖新增

```xml
<!-- pom.xml -->
<jjwt-api.version>0.12.5</jjwt-api.version>
<!-- jjwt-api, jjwt-impl, jjwt-jackson -->
<spring-security-crypto.version>6.2.4</spring-security-crypto.version>
```

## 五、提交记录

```
0a924f0 feat: JWT user authentication with login gate for cart
```
