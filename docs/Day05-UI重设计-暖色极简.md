# Day 05 — UI 重设计：暖色极简风格

## 一、设计背景

Day 04 搭建的 Android 客户端功能完整，但 UI 偏向"学生作业"风格：
- 单一蓝色调 `#4A90D9`，缺乏品牌感
- 聊天气泡为标准圆角矩形，无视觉层次
- 商品卡片为默认 CardView，无精致感
- 冷灰底色 `#F5F5F5`，缺少温度

**目标：** 在不改一行 Kotlin 逻辑的前提下，纯资源层改造，将整体视觉升级为**暖色极简 + 柔和精致**风格。

---

## 二、设计决策

经过三轮讨论确认了三个核心方向：

| 决策 | 选择 | 风格参考 |
|---|---|---|
| 配色方向 | 暖色极简 | 奶油白 + 深棕 + 暖金，类似 MUJI/Aesop |
| APP 气质 | 温和亲切 | AI 头像、推荐标签、友好微文案 |
| 设计语言 | 柔和精致 | 渐变气泡、大圆角、柔阴影 |

**不做的：**
- 不改 Kotlin 业务逻辑（ChatAdapter 仅1行改动）
- 不改网络层、数据模型
- 不增加动画（本次只做静态视觉）
- 不引入自定义字体

---

## 三、配色方案

| 角色 | 色值 | 用途 |
|---|---|---|
| 主色·深棕 | `#5C4A3A` | 标题、用户气泡渐变起点、发送按钮、强调元素 |
| 中棕 | `#8B7355` | 用户气泡渐变终点 |
| 暖金 | `#C47A4A` | 价格、AI 头像渐变终点、价格标签 |
| 浅金 | `#D4A574` | AI 头像渐变起点 |
| 页面背景 | `#FAF7F2` | 聊天页/详情页底色（偏暖奶油白） |
| 卡片白 | `#FFFFFF` | AI 气泡、商品卡片 |
| 标签暖 | `#F5EFE5` | 推荐标签、规格标签背景 |
| 辅助文字 | `#A09080` | 次要信息、时间戳、品牌名 |
| 淡文字 | `#C4B8A8` | placeholder、最弱信息 |
| 深色背景 | `#4A3A2E` | primary_dark |

---

## 四、字体层级

| 层级 | 规格 | 用途 |
|---|---|---|
| 页面标题 | 16sp / Bold | Header 标题、详情页标题 |
| 商品名称 | 14sp / Bold | 卡片标题 |
| 正文 | 13sp / Regular / 行高 1.5 | 聊天消息 |
| 辅助信息 | 12sp / Regular | 品牌、卖点 |
| 价格 | 17-28sp / Bold | 卡片/详情页价格（暖金色） |
| 标签/副标题 | 10-11sp | 推荐标签、header 副标题 |

---

## 五、组件设计详解

### 5.1 用户聊天气泡

**设计：** 暖棕渐变背景 + 不对称圆角（模拟"尾巴"在左下角）

```xml
<!-- drawable/bubble_user.xml -->
<shape>
    <gradient startColor="#5C4A3A" endColor="#8B7355" angle="135" />
    <corners topLeftRadius="22dp" topRightRadius="22dp"
             bottomRightRadius="22dp" bottomLeftRadius="6dp" />
</shape>
```

- 文字白色 13sp，右对齐，marginStart 56dp
- elevation 4dp 做轻微浮起

### 5.2 AI 聊天气泡

**设计：** 白底 + 不对称圆角（"尾巴"在左上角）+ 金色渐变圆形头像

```xml
<!-- drawable/bubble_ai.xml -->
<shape>
    <solid color="#FFFFFF" />
    <corners topLeftRadius="8dp" topRightRadius="20dp"
             bottomRightRadius="20dp" bottomLeftRadius="20dp" />
    <stroke width="0.5dp" color="#F0E8D8" />
</shape>
```

- 左侧 28dp 渐变头像（`bg_ai_avatar.xml`：`#D4A574` → `#C47A4A`）
- 文字深棕 13sp，elevation 2dp

### 5.3 商品卡片

从原有的 `cardCornerRadius="10dp"` + 灰色 placeholder 全面升级：

| 属性 | 旧值 | 新值 |
|---|---|---|
| cardCornerRadius | 10dp | **18dp** |
| cardElevation | 2dp | **4dp** |
| 商品图 | 72dp, `@android:color/darker_gray` | 60dp, 米色渐变 `bg_product_placeholder` |
| 价格颜色 | `#E53935` (红) | `@color/accent` (暖金) |
| 价格字号 | 16sp | **17sp Bold** |
| 新增标签 | 无 | "店长推荐" 标签（`bg_tag` 背景，默认 gone） |
| 品牌颜色 | `@android:color/darker_gray` | `@color/text_secondary` (#A09080) |

### 5.4 输入区域

**设计：** 白色胶囊形容器内嵌 EditText + 右侧渐变圆形发送按钮

```
┌─────────────────────────────────────────┐
│  ┌──────────────────────────────────┐   │
│  │  说说你的需求...              [▶] │   │
│  └─ bg_input_capsule (圆角26dp) ───┘   │
│                 ↑ send button:          │
│                 bg_send_button (40dp)   │
│                 渐变 #5C4A3A→#8B7355    │
└─────────────────────────────────────────┘
```

- 发送按钮图标从蓝底改为白色箭头 + 暖棕渐变圆底
- EditText 的 `background="@null"` 去边框
- 整体 elevation 3dp 悬浮感

### 5.5 顶部标题栏

**旧设计：** 48dp 蓝色实底 `@color/primary` + 单行白色文字

**新设计：** 与页面同色背景 `@color/background` + 双行（主标题 + 副标题）+ 极淡分割线

```xml
<!-- 主标题 16sp Bold #5C4A3A -->
<!-- 副标题 "发现属于你的好物" 11sp #B8A898 -->
<!-- 0.5dp 分割线 #1A5C4A3A (6% alpha) -->
```

### 5.6 商品详情页

从蓝底标题栏 + 生硬线性排版全面重构：

| 区域 | 改动 |
|---|---|
| 顶部栏 | 蓝底 → 暖背景 + 深棕返回箭头 + 文字 |
| 商品大图 | 灰色 → 米色渐变 placeholder（240dp） |
| 价格 | 红色 → 暖金 `#C47A4A` 28sp Bold |
| 添加原价位 | `tvOriginalPrice`，默认 gone |
| 添加推荐标签 | `tvRecommendTag`，默认 gone |
| 分割线 | 灰实线 → 暖色 `#E8DDD0` 细线 |
| 描述标题 | 小号 11sp + letterSpacing |
| 描述正文 | 14sp + lineSpacingExtra 6dp |
| 添加规格标签 | `specTagsLayout` 容器，默认 gone |

---

## 六、实现清单

**15 个文件，8 个 commit，纯资源层改动。**

### 6.1 colors.xml + themes.xml

```xml
<!-- 旧 -->
<color name="primary">#4A90D9</color>
<color name="accent">#E53935</color>
<color name="background">#F5F5F5</color>

<!-- 新 -->
<color name="primary">#5C4A3A</color>
<color name="accent">#C47A4A</color>
<color name="background">#FAF7F2</color>
```

新增 8 个颜色常量：`primary_light`、`accent_light`、`surface`、`tag_background`、`text_primary`、`text_secondary`、`text_hint`、`tag_text`

### 6.2 Drawable 资源（9 个文件）

| 文件 | 作用 | 类型 |
|---|---|---|
| `bubble_user.xml` | 用户气泡：暖棕渐变 + 不对称圆角 | 修改 |
| `bubble_ai.xml` | AI 气泡：白底 + 不对称圆角 + 淡边框 | 修改 |
| `bg_ai_avatar.xml` | AI 头像：金色渐变圆形 (oval) | 新建 |
| `bg_send_button.xml` | 发送按钮：棕色渐变圆 | 新建 |
| `bg_input_capsule.xml` | 输入框容器：白色胶囊 (radius 26dp) | 新建 |
| `bg_product_placeholder.xml` | 商品图占位：米色渐变 (radius 14dp) | 新建 |
| `bg_tag.xml` | 标签背景：暖色圆角 (radius 10dp) | 新建 |
| `ic_send.xml` | 发送图标：白色箭头 | 修改 |
| `ic_back.xml` | 返回图标：深棕箭头 | 修改 |

### 6.3 Layout 布局（5 个文件）

| 文件 | 改动类型 | 要点 |
|---|---|---|
| `activity_main.xml` | 全面重写 | 暖 header、胶囊输入区、progressBar 调色 |
| `item_message_user.xml` | 重写 | elevation 阴影、margin 调整、13sp 白字 |
| `item_message_ai.xml` | 重写 | 横向布局、28dp avatar、气泡 weight=1 |
| `item_product_card.xml` | 重写 | 18dp 圆角、60dp 图片、新增 tag 位 |
| `activity_product_detail.xml` | 重写 | 全面暖色重构、5 个新 ID、gradient divider |

### 6.4 ChatAdapter.kt（1 行改动）

```kotlin
// 旧：系统灰色 placeholder
.placeholder(android.R.color.darker_gray)

// 新：米色渐变 placeholder
.placeholder(R.drawable.bg_product_placeholder)
```

---

## 七、构建问题与修复

### 7.1 gravity="baseline" 不兼容

**错误：** `activity_product_detail.xml:110: 'baseline' is incompatible with attribute gravity`

价格行 LinearLayout 使用了 `android:gravity="baseline"`，该值在某些 AGP 9 配置下不被识别。

**修复：** `baseline` → `center_vertical`

---

## 八、构建验证

```bash
$ ./gradlew assembleDebug
BUILD SUCCESSFUL in 9s
38 actionable tasks: 11 executed, 27 up-to-date
```

所有 XML 语法正确，资源引用无错误，APK 正常生成。

---

## 九、Commit 历史

```
322da21 fix: replace baseline gravity with center_vertical, add gradlew wrapper
75f9e35 ui: use new gradient placeholder for product image loading
98b1faf ui: redesign product detail page with warm palette and refined layout
2f77365 ui: redesign main chat screen — warm header, capsule input, refined layout
aab8a85 ui: redesign product card with large radius, gradient placeholder, and badge
dc0d24d ui: redesign chat bubbles with warm gradient, avatar, and elevation
c707953 ui: redesign drawables — bubbles, avatars, buttons, placeholders
012a5dd ui: update color palette to warm neutral scheme
```

---

## 十、文件清单

```
client/app/src/main/res/
├── values/
│   ├── colors.xml                 (修改：11 色值全部替换 + 8 新增)
│   └── themes.xml                 (修改：新增 windowBackground)
├── drawable/
│   ├── bubble_user.xml            (修改：渐变 + 不对称圆角)
│   ├── bubble_ai.xml              (修改：白底 + 不对称圆角 + 淡边框)
│   ├── bg_ai_avatar.xml           ← 新建
│   ├── bg_send_button.xml         ← 新建
│   ├── bg_input_capsule.xml       ← 新建
│   ├── bg_product_placeholder.xml ← 新建
│   ├── bg_tag.xml                 ← 新建
│   ├── ic_send.xml                (修改：白色填充)
│   └── ic_back.xml                (修改：深棕色填充)
└── layout/
    ├── activity_main.xml          (修改：全面重写)
    ├── item_message_user.xml      (修改：重写)
    ├── item_message_ai.xml        (修改：重写 + AI 头像)
    ├── item_product_card.xml      (修改：重写)
    └── activity_product_detail.xml (修改：全面重写)

client/app/src/main/java/com/ragagent/ui/
└── ChatAdapter.kt                 (修改：1行 placeholder 替换)
```
