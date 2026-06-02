# Day 08 — 日志体系与多轮对话优化

## 一、后端日志体系搭建

### 1.1 覆盖范围

| 层 | 文件 | 日志内容 |
|---|---|---|
| Controller | `ChatController.java` | `==>` 接口调用、`<==` SSE 完成/超时/异常、非流式对话、清除会话 |
| Controller | `ProductController.java` | 商品查询 debug、语义搜索 info、商品不存在 warn |
| Service | `ChatService.java` | 树形 RAG 流水线（预处理→检索→LLM→完成）、SSE product 事件、耗时统计 |
| Service | `RetrieverService.java` | 向量检索 debug、原始/过滤结果数、每个商品得分 |

### 1.2 日志格式示例

```
==> SSE 流式对话 | sessionId=abc | message="推荐蓝牙耳机"
┌─ RAG 流式对话开始 | sessionId=abc
│ 预处理: "推荐蓝牙耳机" → "蓝牙耳机"
│ 检索结果: 3 条 | ids=[p_digital_007, p_digital_008, p_digital_015]
│ 调用 LLM...
└─ 对话完成 | sessionId=abc | 回复长度=156 | 耗时=2847ms
<== SSE 完成 | sessionId=abc
```

### 1.3 配置

```yaml
logging:
  level:
    "[com.ragagent]": DEBUG
    root: INFO
```

---

## 二、模糊查询追问优化

### 2.1 问题

测试用例 3 "我想买护肤品" → LLM 直接推荐了商品，没有追问细节。

### 2.2 根因

System Prompt 原有规则 5："当用户需求模糊时，主动提问引导用户细化需求"——表述太弱，LLM 倾向于忽略。

### 2.3 修复

重构 System Prompt，新增三层约束：

**决策流程（先判断再行动）：**
```
1. 用户有品类+价位/肤质等具体信息 → 直接推荐
2. 用户只说"护肤品"、"数码产品"等模糊大品类 → 必须先追问
3. 用户说"想买东西"等完全无目标 → 必须先追问
4. 用户说"油皮洗面奶"、"200元蓝牙耳机"等具体需求 → 推荐
```

**规则 5 升级：** "必须先追问再推荐。追问时给出2-3个具体方向引导用户"

**正确/错误示例对照：**
```
用户："推荐护肤品"
✅ "请问您想要什么类型的护肤品呢？我们有精华、面霜、洁面等品类~"
❌ "推荐雅诗兰黛精华 [PRODUCT:p_beauty_001]..."
```

---

## 三、多轮对话检索增强

### 3.1 问题

```
轮1: "推荐跑鞋" → 检索到跑鞋 ✅
轮2: "要轻量的" → query增强为"推荐跑鞋 要轻量的" ✅
轮3: "2000以内" → query增强为"要轻量的 2000以内" ✗ (丢了"跑鞋")
```

### 3.2 根因

`augmentQuery` 只取最近 1 条历史拼到当前 query。轮 3 只有上一轮的"要轻量的"，丢了最原始的意图"跑鞋"。

### 3.3 修复

| | 改前 | 改后 |
|---|---|---|
| 取历史数量 | `getRecentUserMessages(sessionId, 1)` | `getRecentUserMessages(sessionId, 3)` |
| 拼接方式 | 只用最后一条 | 全部历史关键词拼入，去重去当前 |
| 轮3 效果 | `要轻量的 2000以内` | `推荐跑鞋 要轻量的 2000以内` |

---

## 四、端到端测试结果

| # | 用例 | 结果 |
|---|---|---|
| 1 | "推荐适合油皮的洗面奶" → 商品卡片 | ✅ |
| 2 | "200元以下的蓝牙耳机" → 推荐或告知无匹配 | ✅ |
| 3 | "我想买护肤品" → AI 追问肤质/功效 | ✅ 修复后 |
| 4 | "推荐跑鞋" → "要轻量的" → "2000以内" | ✅ 修复后 |
| 5 | 推荐库中不存在的品类 | ✅ |
| 6 | 空输入 / 快速连续发送 | ✅ 不崩溃 |

---

## 五、改动文件清单

```
server/src/main/java/com/ragagent/
├── controller/
│   ├── ChatController.java       (修改：添加接口调用/SSE生命周期日志)
│   └── ProductController.java    (修改：添加查询/搜索日志)
└── service/
    ├── ChatService.java           (修改：Prompt 追问强化、augmentQuery 增强)
    └── RetrieverService.java      (修改：检索过程 debug 日志)
```
