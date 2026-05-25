# RAG Agent UI 重设计 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android 聊天 App 从蓝色系基础 UI 升级为暖棕 + 奶油白的柔和精致风格。

**Architecture:** 纯资源层改造 — 颜色值 → drawable 形状 → layout XML。不改 Kotlin 业务逻辑，所有改动集中在 `res/` 目录。

**Tech Stack:** Android XML Layout, ViewBinding, AGP 9.0.1 (built-in Kotlin), CardView, Material Components

---

### Task 1: 更新色彩系统 (colors.xml + themes.xml)

**Files:**
- Modify: `client/app/src/main/res/values/colors.xml`
- Modify: `client/app/src/main/res/values/themes.xml`

- [ ] **Step 1: 替换 colors.xml 全部颜色值**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 主色系 — 暖棕 -->
    <color name="primary">#5C4A3A</color>
    <color name="primary_dark">#4A3A2E</color>
    <color name="primary_light">#8B7355</color>

    <!-- 强调色 — 暖金 -->
    <color name="accent">#C47A4A</color>
    <color name="accent_light">#D4A574</color>

    <!-- 背景 & 表面 -->
    <color name="background">#FAF7F2</color>
    <color name="surface">#FFFFFF</color>
    <color name="tag_background">#F5EFE5</color>

    <!-- 文字 -->
    <color name="text_primary">#5C4A3A</color>
    <color name="text_secondary">#A09080</color>
    <color name="text_hint">#C4B8A8</color>

    <!-- 标签 -->
    <color name="tag_text">#B8860B</color>

    <!-- 气泡 (保留旧 key 名用于兼容，值更新) -->
    <color name="bubble_user">#5C4A3A</color>
    <color name="bubble_ai">#FFFFFF</color>
</resources>
```

- [ ] **Step 2: 更新 themes.xml 适配新色系**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.RagAgent" parent="Theme.AppCompat.Light.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryDark">@color/primary_dark</item>
        <item name="colorAccent">@color/accent</item>
        <item name="android:windowBackground">@color/background</item>
    </style>
</resources>
```

- [ ] **Step 3: Commit**

```bash
git add client/app/src/main/res/values/colors.xml client/app/src/main/res/values/themes.xml
git commit -m "ui: update color palette to warm neutral scheme"
```

---

### Task 2: 创建/重写 drawable 资源

**Files:**
- Modify: `client/app/src/main/res/drawable/bubble_user.xml`
- Modify: `client/app/src/main/res/drawable/bubble_ai.xml`
- Create: `client/app/src/main/res/drawable/bg_ai_avatar.xml`
- Create: `client/app/src/main/res/drawable/bg_send_button.xml`
- Create: `client/app/src/main/res/drawable/bg_input_capsule.xml`
- Create: `client/app/src/main/res/drawable/bg_product_placeholder.xml`
- Create: `client/app/src/main/res/drawable/bg_tag.xml`
- Modify: `client/app/src/main/res/drawable/ic_send.xml`
- Modify: `client/app/src/main/res/drawable/ic_back.xml`

- [ ] **Step 1: 重写 bubble_user.xml — 暖棕渐变 + 不对称圆角**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:startColor="#5C4A3A"
        android:endColor="#8B7355"
        android:angle="135" />
    <corners
        android:topLeftRadius="22dp"
        android:topRightRadius="22dp"
        android:bottomRightRadius="22dp"
        android:bottomLeftRadius="6dp" />
</shape>
```

- [ ] **Step 2: 重写 bubble_ai.xml — 白色 + 不对称圆角 + 淡边框**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF" />
    <corners
        android:topLeftRadius="8dp"
        android:topRightRadius="20dp"
        android:bottomRightRadius="20dp"
        android:bottomLeftRadius="20dp" />
    <stroke
        android:width="0.5dp"
        android:color="#F0E8D8" />
</shape>
```

- [ ] **Step 3: 创建 bg_ai_avatar.xml — AI 头像渐变圆**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <gradient
        android:startColor="#D4A574"
        android:endColor="#C47A4A"
        android:angle="135" />
</shape>
```

- [ ] **Step 4: 创建 bg_send_button.xml — 发送按钮渐变圆**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <gradient
        android:startColor="#5C4A3A"
        android:endColor="#8B7355"
        android:angle="135" />
</shape>
```

- [ ] **Step 5: 创建 bg_input_capsule.xml — 输入框白色胶囊**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF" />
    <corners android:radius="26dp" />
</shape>
```

- [ ] **Step 6: 创建 bg_product_placeholder.xml — 商品图渐变占位**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:startColor="#E8DDD0"
        android:endColor="#DDD5C8"
        android:angle="135" />
    <corners android:radius="14dp" />
</shape>
```

- [ ] **Step 7: 创建 bg_tag.xml — 标签背景**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/tag_background" />
    <corners android:radius="10dp" />
</shape>
```

- [ ] **Step 8: 更新 ic_send.xml — 白色填充发送图标**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z" />
</vector>
```

- [ ] **Step 9: 更新 ic_back.xml — 深棕色返回箭头**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#5C4A3A"
        android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z" />
</vector>
```

- [ ] **Step 10: Commit**

```bash
git add client/app/src/main/res/drawable/
git commit -m "ui: redesign drawables — bubbles, avatars, buttons, placeholders"
```

---

### Task 3: 更新聊天气泡布局

**Files:**
- Modify: `client/app/src/main/res/layout/item_message_user.xml`
- Modify: `client/app/src/main/res/layout/item_message_ai.xml`

- [ ] **Step 1: 重写 item_message_user.xml — 新用户气泡 + elevation 阴影**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingBottom="10dp">

    <TextView
        android:id="@+id/tvMessage"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end"
        android:layout_marginStart="56dp"
        android:layout_marginEnd="12dp"
        android:background="@drawable/bubble_user"
        android:elevation="4dp"
        android:paddingHorizontal="14dp"
        android:paddingVertical="10dp"
        android:textColor="#FFFFFF"
        android:textSize="13sp"
        android:lineSpacingExtra="3dp"
        android:maxWidth="280dp" />
</LinearLayout>
```

- [ ] **Step 2: 重写 item_message_ai.xml — 添加 AI 头像 + 新气泡 + elevation**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:paddingBottom="10dp"
    android:paddingStart="8dp"
    android:paddingEnd="12dp"
    android:gravity="top">

    <!-- AI 头像 -->
    <View
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:layout_marginTop="2dp"
        android:layout_marginEnd="8dp"
        android:background="@drawable/bg_ai_avatar" />

    <TextView
        android:id="@+id/tvMessage"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:background="@drawable/bubble_ai"
        android:elevation="2dp"
        android:paddingHorizontal="14dp"
        android:paddingVertical="10dp"
        android:textColor="@color/text_primary"
        android:textSize="13sp"
        android:lineSpacingExtra="3dp"
        android:maxWidth="260dp" />
</LinearLayout>
```

- [ ] **Step 3: Commit**

```bash
git add client/app/src/main/res/layout/item_message_user.xml client/app/src/main/res/layout/item_message_ai.xml
git commit -m "ui: redesign chat bubbles with warm gradient, avatar, and elevation"
```

---

### Task 4: 更新商品卡片布局

**Files:**
- Modify: `client/app/src/main/res/layout/item_product_card.xml`

- [ ] **Step 1: 重写 item_product_card.xml — 大圆角 + 渐变换图 + 推荐标签**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginStart="8dp"
    android:layout_marginEnd="56dp"
    android:layout_marginBottom="10dp"
    app:cardCornerRadius="18dp"
    app:cardElevation="4dp"
    app:cardBackgroundColor="@color/surface"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="14dp">

        <ImageView
            android:id="@+id/ivProductImage"
            android:layout_width="60dp"
            android:layout_height="60dp"
            android:scaleType="centerCrop"
            android:background="@drawable/bg_product_placeholder"
            android:contentDescription="商品图片" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical"
            android:paddingStart="12dp"
            android:gravity="center_vertical">

            <TextView
                android:id="@+id/tvProductName"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="14sp"
                android:textColor="@color/text_primary"
                android:maxLines="2"
                android:ellipsize="end"
                android:textStyle="bold" />

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="4dp"
                android:gravity="center_vertical">

                <TextView
                    android:id="@+id/tvProductPrice"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/accent"
                    android:textSize="17sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tvProductTag"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="8dp"
                    android:background="@drawable/bg_tag"
                    android:paddingHorizontal="8dp"
                    android:paddingVertical="2dp"
                    android:text="店长推荐"
                    android:textColor="@color/tag_text"
                    android:textSize="10sp"
                    android:visibility="gone" />
            </LinearLayout>

            <TextView
                android:id="@+id/tvProductBrand"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="3dp"
                android:textColor="@color/text_secondary"
                android:textSize="12sp" />
        </LinearLayout>
    </LinearLayout>

</androidx.cardview.widget.CardView>
```

- [ ] **Step 2: Commit**

```bash
git add client/app/src/main/res/layout/item_product_card.xml
git commit -m "ui: redesign product card with large radius, gradient placeholder, and badge"
```

---

### Task 5: 更新主聊天页布局

**Files:**
- Modify: `client/app/src/main/res/layout/activity_main.xml`

- [ ] **Step 1: 重写 activity_main.xml — 暖色 header + 胶囊输入框 + 重构整体**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background">

    <!-- 标题栏 -->
    <LinearLayout
        android:id="@+id/headerLayout"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center"
        android:paddingTop="12dp"
        android:paddingBottom="10dp"
        android:background="@color/background"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="AI 导购助手"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:text="发现属于你的好物"
            android:textColor="#B8A898"
            android:textSize="11sp" />
    </LinearLayout>

    <!-- 分割线 -->
    <View
        android:id="@+id/headerDivider"
        android:layout_width="0dp"
        android:layout_height="0.5dp"
        android:background="#1A5C4A3A"
        app:layout_constraintTop_toBottomOf="@id/headerLayout"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- 消息列表 -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:paddingTop="12dp"
        android:paddingBottom="8dp"
        android:clipToPadding="false"
        app:layout_constraintTop_toBottomOf="@id/headerDivider"
        app:layout_constraintBottom_toTopOf="@id/inputLayout"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- 进度条 -->
    <ProgressBar
        android:id="@+id/progressBar"
        style="?android:attr/progressBarStyleSmall"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:visibility="gone"
        android:indeterminateTint="@color/accent"
        app:layout_constraintBottom_toTopOf="@id/inputLayout"
        app:layout_constraintStart_toStartOf="parent"
        android:layout_marginStart="16dp"
        android:layout_marginBottom="6dp" />

    <!-- 底部输入区 -->
    <LinearLayout
        android:id="@+id/inputLayout"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:paddingTop="8dp"
        android:paddingBottom="12dp"
        android:background="@color/background"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <!-- 白色胶囊容器 -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:background="@drawable/bg_input_capsule"
            android:elevation="3dp"
            android:paddingStart="6dp"
            android:paddingEnd="4dp">

            <EditText
                android:id="@+id/etInput"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:background="@null"
                android:hint="说说你的需求..."
                android:textColorHint="@color/text_hint"
                android:textColor="@color/text_primary"
                android:maxLines="3"
                android:paddingHorizontal="10dp"
                android:paddingVertical="8dp"
                android:textSize="13sp" />

            <ImageButton
                android:id="@+id/btnSend"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="@drawable/bg_send_button"
                android:elevation="3dp"
                android:src="@drawable/ic_send"
                android:scaleType="centerInside"
                android:contentDescription="发送" />
        </LinearLayout>
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 2: Commit**

```bash
git add client/app/src/main/res/layout/activity_main.xml
git commit -m "ui: redesign main chat screen — warm header, capsule input, refined layout"
```

---

### Task 6: 更新商品详情页布局

**Files:**
- Modify: `client/app/src/main/res/layout/activity_product_detail.xml`

- [ ] **Step 1: 重写 activity_product_detail.xml — 全面暖色重构**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background">

    <!-- 顶部栏 -->
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
            android:contentDescription="返回"
            android:src="@drawable/ic_back"
            android:scaleType="centerInside" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="商品详情"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold" />
    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <!-- 商品大图 -->
            <ImageView
                android:id="@+id/ivProduct"
                android:layout_width="match_parent"
                android:layout_height="240dp"
                android:scaleType="centerCrop"
                android:background="@drawable/bg_product_placeholder" />

            <!-- 信息区 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="20dp">

                <!-- 名称 + 标签 -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="top">

                    <TextView
                        android:id="@+id/tvTitle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:textColor="@color/text_primary"
                        android:textSize="20sp"
                        android:textStyle="bold"
                        android:lineSpacingExtra="2dp" />

                    <TextView
                        android:id="@+id/tvRecommendTag"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="12dp"
                        android:background="@drawable/bg_tag"
                        android:paddingHorizontal="10dp"
                        android:paddingVertical="4dp"
                        android:text="店长推荐"
                        android:textColor="@color/tag_text"
                        android:textSize="11sp"
                        android:visibility="gone" />
                </LinearLayout>

                <!-- 品牌 -->
                <TextView
                    android:id="@+id/tvBrand"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp" />

                <!-- 价格 -->
                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:orientation="horizontal"
                    android:gravity="baseline">

                    <TextView
                        android:id="@+id/tvPrice"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textColor="@color/accent"
                        android:textSize="28sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/tvOriginalPrice"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:textColor="@color/text_hint"
                        android:textSize="13sp"
                        android:visibility="gone" />
                </LinearLayout>

                <!-- 渐变分割线 -->
                <View
                    android:layout_width="match_parent"
                    android:layout_height="0.5dp"
                    android:layout_marginTop="20dp"
                    android:layout_marginBottom="20dp"
                    android:background="#E8DDD0" />

                <!-- 描述标题 -->
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="商品描述"
                    android:textColor="@color/text_secondary"
                    android:textSize="11sp"
                    android:letterSpacing="0.08" />

                <!-- 描述正文 -->
                <TextView
                    android:id="@+id/tvDescription"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:textColor="@color/text_primary"
                    android:textSize="14sp"
                    android:lineSpacingExtra="6dp" />

                <!-- 规格标签群 -->
                <LinearLayout
                    android:id="@+id/specTagsLayout"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="20dp"
                    android:orientation="horizontal"
                    android:visibility="gone">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:background="@drawable/bg_tag"
                        android:paddingHorizontal="12dp"
                        android:paddingVertical="6dp"
                        android:textColor="@color/text_primary"
                        android:textSize="11sp" />
                </LinearLayout>

            </LinearLayout>
        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

- [ ] **Step 2: Commit**

```bash
git add client/app/src/main/res/layout/activity_product_detail.xml
git commit -m "ui: redesign product detail page with warm palette and refined layout"
```

---

### Task 7: 更新 ChatAdapter Glide placeholder 引用

**Files:**
- Modify: `client/app/src/main/java/com/ragagent/ui/ChatAdapter.kt:116-117`

- [ ] **Step 1: 更新 placeholder 为新的 drawable**

将 `ChatAdapter.kt` 第 117 行的 `android.R.color.darker_gray` 替换为 `R.drawable.bg_product_placeholder`：

```kotlin
// 旧:
.placeholder(android.R.color.darker_gray)

// 新:
.placeholder(R.drawable.bg_product_placeholder)
```

- [ ] **Step 2: Commit**

```bash
git add client/app/src/main/java/com/ragagent/ui/ChatAdapter.kt
git commit -m "ui: use new gradient placeholder for product image loading"
```

---

### Task 8: 构建验证

- [ ] **Step 1: 执行 assembleDebug 构建**

```bash
cd client && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL, 无 XML parsing 错误, 无 resource linking 错误。

- [ ] **Step 2: 检查 APK 生成**

```bash
ls client/app/build/outputs/apk/debug/*.apk
```

Expected: 存在 `app-debug.apk`。

- [ ] **Step 3: 如有 lint 警告，确认均非本次改动引入**

```bash
cd client && ./gradlew lintDebug
```

Expected: 无新的 Error 级别问题。

---

## Task Dependency Graph

```
Task 1 (colors)
  └─> Task 2 (drawables, depends on color names)
        ├─> Task 3 (message bubbles, depends on drawables)
        ├─> Task 4 (product cards, depends on drawables)
        ├─> Task 5 (main activity, depends on drawables)
        └─> Task 6 (product detail, depends on drawables)
              └─> Task 7 (ChatAdapter, depends on drawable refs)
                    └─> Task 8 (build verify)
```
