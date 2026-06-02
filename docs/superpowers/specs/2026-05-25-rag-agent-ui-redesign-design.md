# RAG Agent UI 重设计

## 目标

将 AI 导购助手聊天 App 的 UI 从基础功能风格升级为**暖色极简 + 柔和精致**的高级感设计。

## 设计方向

- **配色**: 暖色极简 — 奶油白底 + 深棕文字 + 暖金点缀
- **气质**: 温和亲切 — AI 头像、推荐标签、友好微文案
- **语言**: 柔和精致 — 渐变气泡、大圆角、柔阴影、流畅过渡

---

## 1. 配色方案

| 角色 | 色值 | 用途 |
|---|---|---|
| 主色 (深棕) | `#5C4A3A` | 标题、用户气泡、发送按钮、强调元素 |
| 中棕 | `#8B7355` | 用户气泡渐变终点 |
| 暖金 | `#C47A4A` | 价格、AI 头像渐变、价格标签 |
| 浅金 | `#D4A574` | AI 头像渐变起点 |
| 页面背景 | `#FAF7F2` | 聊天页/详情页底色 |
| 卡片白 | `#FFFFFF` | AI 气泡、商品卡片 |
| 标签暖 | `#F5EFE5` | 推荐标签、规格标签背景 |
| 辅助文字 | `#A09080` | 次要信息、时间戳、品牌名 |
| 淡文字 | `#C4B8A8` | placeholder、最弱信息 |
| 推荐标签字 | `#B8860B` | "店长推荐"标签文字 |

## 2. 字体层级

| 层级 | 规格 | 用途 |
|---|---|---|
| 大标题 | 22sp · Light · letterSpacing 2 | (可选) 留白风格标题 |
| 页面标题 | 16sp · SemiBold | Header 标题 |
| 商品名称 | 14sp · Medium | 卡片/详情标题 |
| 正文 | 13sp · Regular · lineHeight 1.5 | 聊天消息 |
| 辅助信息 | 12sp · Regular | 品牌、卖点 |
| 标签 | 10sp · letterSpacing 1 | 分类、推荐标签 |

## 3. 组件设计

### 3.1 聊天气泡

**用户气泡**:
- 渐变背景 (linearGradient: startColor `#5C4A3A`, endColor `#8B7355`, angle 135)
- 圆角: `22dp` (top-left/top-right/bottom-right), `6dp` (bottom-left)
- 阴影: elevation 4dp, 暖棕色 12% alpha
- 文字: 白色 13sp

**AI 气泡**:
- 背景: 白色 `#FFFFFF`
- 圆角: `20dp` (top-right/bottom-right/bottom-left), `8dp` (top-left)
- 阴影: elevation 2dp, 黑色 4% alpha
- 文字: 深棕 `#5C4A3A` 13sp
- 头像: 渐变圆形 28dp (startColor `#D4A574`, endColor `#C47A4A`)

### 3.2 商品卡片

- CardView `cardCornerRadius="18dp"`, `cardElevation="4dp"`
- 内容 padding: 14dp
- 内部结构: 横向 LinearLayout (商品图 60dp + 文字区)
- 商品图: 渐变 placeholder (startColor `#E8DDD0`, endColor `#DDD5C8`), 圆角 14dp
- 价格: `#C47A4A` 17sp Bold
- 推荐标签: `#F5EFE5` 背景, `#B8860B` 文字, 圆角 10dp
- 卖点行: 12sp `#A09080`

### 3.3 输入区域

- 外层容器: 页面背景色, padding 8+12dp
- 输入框: 白色胶囊形 (圆角 26dp), 柔阴影
- placeholder: "说说你的需求...", `#C4B8A8` 12sp
- 发送按钮: 渐变圆形 40dp, 同用户气泡渐变, 柔阴影

### 3.4 顶部标题栏

- 背景: 页面背景色 `#FAF7F2`, 去蓝底
- 底部: 1px 极淡分割线 (rgba(92,74,58,0.06))
- 主标题: "AI 导购助手" 16sp SemiBold `#5C4A3A`
- 副标题: "发现属于你的好物" 11sp `#B8A898`

### 3.5 商品详情页

- 顶部: 返回箭头 + "商品详情", 同聊天页 header 风格
- 商品大图: 240dp 高, 渐变 placeholder 背景
- 商品名: 20sp SemiBold
- 品牌副标题: 13sp `#A09080`
- 价格: 28sp Bold `#C47A4A` + 原价删除线
- 分割线: 渐变淡出 (solid to transparent)
- 描述标题: 小而淡的标签文字
- 描述正文: 14sp `#5C4A3A`, 行高 1.8
- 规格标签: 胶囊形 chips (`#F5EFE5` 背景, 圆角 16dp)

---

## 4. 实现清单

### 4.1 colors.xml — 替换全部颜色值
### 4.2 themes.xml — 更新 colorPrimary/colorAccent 适配新色系
### 4.3 新建 drawable:
- `bubble_user.xml` — 重写为渐变 + 不对称圆角 + 阴影
- `bubble_ai.xml` — 重写为不对称圆角 + 淡阴影
- `bg_input_area.xml` — 白色胶囊 + 阴影
- `ic_send.xml` — 更新颜色
- `ic_back.xml` — 更新颜色为深棕
- `edittext_bg.xml` — 不再需要（输入框改为白色胶囊）
- `bg_product_placeholder.xml` — 商品图渐变占位
- `bg_tag.xml` — 标签背景
### 4.4 修改 layout:
- `activity_main.xml` — header 改色、输入区重构、progressBar 调色
- `item_message_user.xml` — 新气泡样式
- `item_message_ai.xml` — 新气泡样式 + AI 头像
- `item_product_card.xml` — 新卡片样式
- `activity_product_detail.xml` — 全面重构
### 4.5 修改 ChatAdapter.kt — LoadingViewHolder 从纯文字改为"... "动画文字或无变更

---

## 5. 不做什么

- 不改 Kotlin 业务逻辑
- 不改网络层、数据模型
- 不增加动画（本次只做静态视觉）
- 不引入自定义字体（用系统默认保证简约）
