# Day 0-1 环境搭建与项目初始化指南

> 三个人都需要完成以下所有步骤。每人本地都要有完整环境。

---

## 第一步：安装 Docker Desktop（三人必装）

Docker 用于运行 ChromaDB 向量数据库。

1. 下载 Docker Desktop：https://www.docker.com/products/docker-desktop/
2. 双击安装，全部默认选项
3. 安装完会提示重启，重启后任务栏出现 Docker 鲸鱼图标
4. 如果提示安装 WSL2，按提示操作

**验证：** 打开终端，执行 `docker --version`，显示版本号即成功。

---

## 第二步：启动 ChromaDB（三人必做）

```bash
docker pull chromadb/chroma
docker run -d --name chromadb -p 8000:8000 chromadb/chroma
```

**验证：** 浏览器访问 http://localhost:8000/api/v1/heartbeat
看到 `{"nanosecond heartbeat": ...}` 即成功。

**常用命令：**
```bash
docker stop chromadb      # 停止
docker start chromadb     # 启动
docker rm -f chromadb     # 删除重建
```

---

## 第三步：MySQL 建库建表（三人必做）

### 方式一：你已有本地 MySQL

1. 打开 MySQL 客户端（Navicat / DBeaver / IDEA Database）
2. 连接 localhost:3306，用户名 root，密码 root123456（或你自己的密码）
3. 执行 `data/init.sql` 脚本（项目根目录下）

### 方式二：用 Docker 运行 MySQL

```bash
docker pull mysql:8.0
docker run -d --name mysql8 -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root123456 mysql:8.0
```

然后进入 MySQL 执行 init.sql：
```bash
docker exec -it mysql8 mysql -uroot -proot123456
# 进入后执行：
source /path/to/init.sql
```

**验证：**
```sql
USE rag_agent;
SHOW TABLES;  -- 应显示 5 张表
```

---

## 第四步：启动 Spring Boot 后端（三人必做）

1. 用 IntelliJ IDEA 打开 `server/` 目录（作为 Maven 项目）
2. 等待依赖下载完成（首次可能需要 5-10 分钟）
3. 运行 `RagServerApplication.java`

**验证：** 浏览器访问 http://localhost:8080 ，应该看到 Spring Boot 默认页面或 404（正常）。

**常见问题：**
- 依赖下载慢 → 确认 Maven 阿里云镜像已配置
- 数据库连接失败 → 确认 MySQL 已启动，密码正确
- ChromaDB 连接失败 → 确认 Docker 中 ChromaDB 容器在运行

---

## 第五步：创建 Android 项目（三人必做）

1. 打开 Android Studio
2. New Project → Empty Views Activity
3. 配置：
   - Name: `RAGAgent`
   - Package name: `com.ragagent`
   - Save location: `d:\JavaCode\RagAgent\client\`
   - Language: Kotlin
   - Minimum SDK: API 26 (Android 8.0)
   - Build configuration language: Kotlin DSL
4. 点 Finish，等待 Gradle sync（首次 10-20 分钟）

**配置阿里云镜像**（加速依赖下载）：

编辑 `client/settings.gradle.kts`：
```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}
```

**添加依赖**（编辑 `client/app/build.gradle.kts` 的 dependencies 块）：
```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

**验证：** 点击 Run，模拟器上显示空白 Activity 即成功。

---

## 第六步：Git 初始化（三人必做）

在项目根目录 `d:\JavaCode\RagAgent\` 下：

```bash
git init
git add .
git commit -m "Day 0: 项目初始化"
```

---

## 完成检查清单

- [ ] `docker --version` 有输出
- [ ] ChromaDB 心跳接口正常（localhost:8000）
- [ ] MySQL 连接正常，`rag_agent` 库有 5 张表
- [ ] Spring Boot 后端可启动（localhost:8080）
- [ ] Android 项目可编译运行
- [ ] Git 仓库已初始化

全部完成后，进入 Day 2：Embedding + 向量库 + 知识库构建。
