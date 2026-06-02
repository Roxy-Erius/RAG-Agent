# Day 04 — Android 客户端搭建

## 一、项目概览

Android 客户端是一个基于 **AGP 9.0.1 + Gradle 9.1.0 + Kotlin (内置)** 的 AI 导购助手聊天 App。

### 1.1 核心功能

- 聊天式商品检索对话
- SSE 流式接收 AI 回复（打字机效果）
- 商品卡片内嵌展示 + 点击查看详情

### 1.2 技术选型

| 技术 | 版本 | 用途 |
|---|---|---|
| Android Gradle Plugin | 9.0.1 | 构建系统 |
| Gradle | 9.1.0 | 构建工具 |
| Kotlin | 内置 (via AGP 9) | 开发语言 |
| compileSdk / targetSdk | 36 | 编译目标 |
| minSdk | 26 | 最低支持 Android 8.0 |
| ViewBinding | enabled | 视图绑定 |
| OkHttp + OkHttp-SSE | 4.12.0 | HTTP 请求 + SSE 流式解析 |
| Glide | 4.16.0 | 图片加载 |
| Gson | 2.10.1 | JSON 解析 |
| Coroutines | 1.7.3 | 异步处理 |

---

## 二、项目结构

```
client/
├── build.gradle.kts              ← 根构建脚本（AGP 9.0.1 声明）
├── settings.gradle.kts           ← 项目设置 + 阿里云镜像仓库
├── gradle.properties             ← Gradle/JVM 配置
├── gradlew / gradlew.bat         ← Gradle Wrapper 脚本
└── app/
    ├── build.gradle.kts          ← 模块构建脚本
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/ragagent/
        │   ├── RagAgentApp.kt            ← Application 入口
        │   ├── model/
        │   │   ├── ChatMessage.kt         ← 消息数据类（User/Ai/ProductCard/Loading）
        │   │   ├── Product.kt             ← 商品数据类
        │   │   └── SseEvent.kt            ← SSE 事件解析
        │   ├── network/
        │   │   ├── ApiService.kt          ← REST API 调用
        │   │   └── SseClient.kt           ← SSE 流式客户端
        │   ├── viewmodel/
        │   │   └── ChatViewModel.kt       ← 聊天状态管理
        │   └── ui/
        │       ├── MainActivity.kt        ← 聊天主页
        │       ├── ProductCardActivity.kt ← 商品详情页
        │       └── ChatAdapter.kt         ← RecyclerView 多类型适配器
        └── res/
            ├── layout/                    ← 5 个布局文件
            ├── drawable/                  ← 图标 + 形状资源
            ├── values/                    ← 颜色/主题/字符串
            └── mipmap-anydpi-v26/         ← 启动图标
```

---

## 三、AGP 9 环境搭建踩坑记录

### 3.1 compileSdk 版本不兼容

**问题：** 项目初始 `compileSdk = 34`，AGP 9.0.1 要求 `compileSdk >= 35`。

**错误信息：** Gradle sync 失败，IDE 无法解析 `android.app.Application`、`AppCompatActivity` 等框架类。

**修复：** `compileSdk 34 → 36`（匹配已安装的 SDK Platform），同步修改 `targetSdk`。

```kotlin
// app/build.gradle.kts
android {
    compileSdk = 36   // 原 34
    defaultConfig {
        targetSdk = 36  // 原 34
    }
}
```

### 3.2 Build Tools 缺失

**问题：** AGP 9.0.1 要求 Build Tools 36.0.0，但本地 SDK 只有 36.1.0 和 37.0.0。由于网络无法直连 Google，SDK Manager 无法自动下载。

**错误信息：** `Failed to find Build Tools revision 36.0.0`

**修复：** 复制 `build-tools/36.1.0` → `build-tools/36.0.0`。36.x 系列 API 兼容。

```bash
cp -r $ANDROID_SDK/build-tools/36.1.0 $ANDROID_SDK/build-tools/36.0.0
```

### 3.3 Kotlin 插件冲突

**问题：** AGP 9 默认 `android.builtInKotlin=true`，Kotlin 编译器和 Gradle 插件已内置于 AGP。再显式声明 `org.jetbrains.kotlin.android` 导致插件注册两次。

**错误信息：** `Cannot add extension with name 'kotlin', as there is an extension already registered with that name.`

**修复：** 从两个 `build.gradle.kts` 中移除外部 Kotlin 插件声明。

```kotlin
// build.gradle.kts (根) — 移除
plugins {
    id("com.android.application") version "9.0.1" apply false
    // 删除: id("org.jetbrains.kotlin.android") version "2.2.10" apply false
}

// app/build.gradle.kts — 移除
plugins {
    id("com.android.application")
    // 删除: id("org.jetbrains.kotlin.android")
}
```

**连锁问题：** 移除 Kotlin 插件后，`kotlinOptions` 块无法识别。AGP 9 内置 Kotlin 会自动跟随 `compileOptions` 的设置，直接删除即可。

```kotlin
// app/build.gradle.kts — 删除 kotlinOptions 块
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
// 删除: kotlinOptions { jvmTarget = "17" }
```

### 3.4 gradle.properties 弃用属性

AGP 9 翻新了大量默认值，旧 `gradle.properties` 中 7 个属性已弃用并产生警告。

**清理：** 移除以下属性（AGP 9 默认值即为正确值）：

```
删除的属性：
- android.defaults.buildfeatures.resvalues=true
- android.sdk.defaultTargetSdkToCompileSdkIfUnset=false
- android.enableAppCompileTimeRClass=false
- android.usesSdkInManifest.disallowed=false
- android.r8.optimizedResourceShrinking=false
- android.builtInKotlin=false
- android.newDsl=false
```

### 3.5 Gradle Wrapper 生成

**问题：** 项目只有 `gradle-wrapper.properties`，缺少 `gradlew` / `gradlew.bat` / `gradle-wrapper.jar`（被 `.gitignore` 的 `*.jar` 规则忽略）。

**修复：** 用已缓存的 Gradle 9.1.0 生成 wrapper 脚本：

```bash
gradle wrapper --gradle-version 9.1.0
```

然后修改 `gradle-wrapper.properties` 的 `distributionUrl` 使用腾讯镜像（GFW 原因）：

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.1.0-bin.zip
```

### 3.6 缺失启动图标

**问题：** `AndroidManifest.xml` 引用 `@mipmap/ic_launcher` 但无对应资源，导致资源链接失败。

**修复：** 创建 `mipmap-anydpi-v26/ic_launcher.xml`（自适应图标）和 `drawable/ic_launcher_foreground.xml`（矢量前景）。

---

## 四、Gradle 配置要点

### 4.1 settings.gradle.kts — 阿里云镜像

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}
```

### 4.2 gradle.properties — 最终版本

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
android.uniquePackageNames=false
android.dependency.useConstraints=true
android.r8.strictFullModeForKeepRules=false
```

### 4.3 网络代理注意事项

- Maven 依赖通过 `settings.gradle.kts` 的阿里云镜像下载
- Gradle 本体通过 `gradle-wrapper.properties` 的腾讯云镜像下载
- `BASE_URL`（后端地址）在 `buildConfigField` 中配置，真机调试需改为局域网 IP

---

## 五、核心代码解读

### 5.1 SseClient — SSE 流式解析

```kotlin
// network/SseClient.kt
// 通过 OkHttp-SSE 连接后端的 GET /api/chat/stream 接口
// 使用 EventSourceListener 的 onEvent() 回调接收每个 token
// onOpen → onEvent(data=token) → onEvent(data=[DONE]) → onClosed
```

### 5.2 ChatViewModel — 状态管理

```kotlin
// viewmodel/ChatViewModel.kt
// StateFlow<List<ChatMessage>> messages  — 消息列表
// StateFlow<Boolean> isStreaming           — 是否正在流式接收
// fun sendMessage(text)                    — 发送消息并启动 SSE 接收
```

### 5.3 ChatAdapter — 多类型 RecyclerView

支持 4 种 ViewType：
- `VIEW_TYPE_USER` — 用户消息气泡
- `VIEW_TYPE_AI` — AI 回复气泡
- `VIEW_TYPE_PRODUCT` — 商品卡片（点击跳转详情页）
- `VIEW_TYPE_LOADING` — "正在思考..." 加载态

---

## 六、已验证项

| 验证项 | 结果 | 说明 |
|--------|------|------|
| Gradle Sync | ✅ 通过 | IDE 无 unresolved reference 错误 |
| AGP 9 + built-in Kotlin | ✅ 通过 | 无插件冲突 |
| compileSdk 36 + Build Tools 36.0.0 | ✅ 通过 | 资源链接正常 |
| `assembleDebug` 构建 | ✅ 通过 | 38 个 task，11 执行 / 27 up-to-date |
| 启动图标 | ✅ 通过 | 自适应图标正确渲染 |

---

## 七、文件清单

```
client/
├── build.gradle.kts              (修改)
├── settings.gradle.kts           (已有，阿里云镜像)
├── gradle.properties             (修改：清理 7 个弃用属性)
├── gradlew                       ← 新增
├── gradlew.bat                   ← 新增
└── app/
    ├── build.gradle.kts          (修改：compileSdk 36, 移除 Kotlin 插件)
    └── src/main/
        ├── AndroidManifest.xml   (已有)
        ├── res/
        │   ├── mipmap-anydpi-v26/ic_launcher.xml  ← 新增
        │   └── drawable/ic_launcher_foreground.xml ← 新增
        └── java/com/ragagent/
            ├── RagAgentApp.kt            (已有)
            ├── model/                     (已有)
            ├── network/                   (已有)
            ├── viewmodel/                 (已有)
            └── ui/                        (已有)
```
