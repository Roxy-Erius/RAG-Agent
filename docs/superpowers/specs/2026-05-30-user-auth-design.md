# 用户认证系统 — 设计文档

## 目标

增加用户注册/登录功能，未登录可对话但加购需登录，登录后购物车绑定用户。

## 核心决策

| 决策 | 选择 |
|---|---|
| 游客模式 | 可对话、浏览，不可加购 |
| 认证方式 | 用户名 + 密码 + JWT Token |
| 密码加密 | BCrypt |
| Token 存储 | Android SharedPreferences |
| 购物车归属 | 登录后用 user_id，未登录降级 session_id |

---

## 一、后端设计

### 1.1 数据库

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE cart_items ADD COLUMN user_id BIGINT NULL;
```

### 1.2 API 接口

| 方法 | 路径 | 认证 | 请求 | 响应 |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | 无 | `{"username":"...","password":"..."}` | `{"message":"注册成功","userId":1}` |
| `POST` | `/api/auth/login` | 无 | `{"username":"...","password":"..."}` | `{"token":"eyJ...","username":"roxy"}` |
| `POST` | `/api/auth/link-session` | JWT | `?sessionId=xxx` | `{"message":"会话已绑定"}` |

### 1.3 购物车改造

| 接口 | 改动 |
|---|---|
| `POST /api/cart/add` | 优先从 JWT 取 userId，无 token 降级用 sessionId |
| `GET /api/cart` | 同上 |
| `DELETE /api/cart/{id}` | 同上 + 校验归属 |
| `PUT /api/cart/{id}` | 同上 + 校验归属 |

### 1.4 新建文件

```
server/src/main/java/com/ragagent/
├── model/User.java
├── model/LoginRequest.java
├── model/RegisterRequest.java
├── repository/UserRepository.java
├── service/UserService.java
├── security/JwtUtil.java
├── security/JwtAuthFilter.java
└── controller/AuthController.java
```

---

## 二、前端设计

### 2.1 新增文件

| 文件 | 作用 |
|---|---|
| `ui/LoginActivity.kt` | 登录页面 |
| `ui/RegisterActivity.kt` | 注册页面 |
| `layout/activity_login.xml` | 登录布局 |
| `layout/activity_register.xml` | 注册布局 |
| `auth/AuthManager.kt` | JWT 存储/读取/清除 |

### 2.2 改动文件

| 文件 | 改动 |
|---|---|
| `network/ApiService.kt` | 新增 `login()`、`register()`、`linkSession()`，OkHttp 加 Auth 拦截器 |
| `ui/ChatAdapter.kt` | 加购按钮 → 检查登录态 → 弹 Dialog 或跳登录 |
| `ui/MainActivity.kt` | `onResume` 刷新 cartCount |
| `AndroidManifest.xml` | 注册 LoginActivity、RegisterActivity |

### 2.3 交互流程

```
加购按钮 → AuthManager.isLoggedIn()?
  ├─ 是 → addToCart()
  └─ 否 → AlertDialog "请先登录"
           ├─ 去登录 → LoginActivity → 登录成功 → finish()
           └─ 取消 → 关闭 Dialog
```

---

## 三、不做的

- 第三方登录（微信/支付宝）
- 密码找回/修改
- 用户信息编辑
- Token 自动刷新
