# LLM-as-Judge 学习笔记

> 记录：为什么用、怎么搭、prompt 怎么拼、rubric 怎么设计、踩了哪些坑。
> 配套代码：`server/src/main/java/com/ragagent/judge/LLmJudge.java`、`JudgeRubrics.java`、
> 测试 `server/src/test/java/com/ragagent/LLmJudgeCalibrationTest.java`、
> `server/src/test/java/com/ragagent/LLMJudgeAgentEvalTest.java`

---

## 0. 为什么需要它（我们项目的缺口）

| 已有评测 | 类型 | 能否用现成指标 |
|---------|------|--------------|
| `RetrievalEvaluationTest`（Hit@K/MRR） | **检索侧** | ✅ 能（确定性指标） |
| **生成侧**（回复切不切题、有没有幻觉、像不像导购） | ❌ 没有 | ❌ 不能 → **LLM-as-Judge** |

当输出的"好坏"**没法用精确匹配算**时（对话质量、幻觉、语气），就**再叫一个 LLM 当裁判**打分。

---

## 1. rubric 是什么

**rubric = 评分细则**。类比作文评分标准：
- 告诉裁判 **评什么**（维度，如"相关性"）
- 规定 **每一档分数对应什么质量**（Score 1 是什么样 … Score 5 是什么样）

**作用**：让打分有锚点 → 稳定、可复现、可解释；还能按需定制维度。
Prometheus 的核心卖点就是**原生吃 rubric**（区别于"随便打个 1-10 分"）。

---

## 2. 三种范式

| 范式 | 做法 | 适用 |
|------|------|------|
| **Pointwise**（绝对打分） | 单条输出按 rubric 打 1~5 | 质量打分、幻觉检测 |
| **Pairwise**（相对打分） | 两个输出选更好 | A/B 比模型/prompt |
| **Reference-based** | 有满分示范（reference）对照 | 有黄金答案时 |

我们第一版做 **Pointwise + Reference**（Prometheus 的 absolute_grade，可选 reference）。

---

## 3. 裁判模型选型：同源偏见 + 免费方案

**核心原则**：裁判最好**比被测强、且不同源**。

- **同源偏见（self-preference bias）**：裁判倾向给自己家族的输出高分
  - agent=DeepSeek，judge=DeepSeek → ⚠️ 有（换 reasoner 也一样，同族）
  - agent=DeepSeek，judge=别的家族 → ✅ 基本消除
- **忠实度**这类"有证据可依"的判断同源影响小；**语气/主观**和**跨模型对比**影响大

**免费/低成本方案**：
| 方案 | 说明 |
|------|------|
| **本地 Ollama**（本次采用） | 真免费、无限流、不同家族 |
| Groq / 硅基流动 / OpenRouter free / Gemini 免费档 | 有额度限制 |
| **专用裁判模型**：Prometheus / M-Prometheus / PandaLM / JudgeLM | 天生为评判训练 |

**结论**：选 **M-Prometheus**（多语言，非 DeepSeek 家族）本地跑。

---

## 4. 本地搭建 M-Prometheus（Ollama，全部装 D 盘）

> 背景：C 盘只剩 5.2GB，D 盘 141GB。硬件 RTX 4060 Laptop 8GB + CUDA 12.3。

**① 装 Ollama 到 D 盘**（Inno Setup，支持 `/DIR` 静默安装）
```powershell
# 下载 OllamaSetup.exe（约 1.46GB）到 D 盘
curl.exe -L -o "D:\Downloads\OllamaSetup.exe" `
  "https://github.com/ollama/ollama/releases/download/v0.34.3/OllamaSetup.exe"
# 静默安装到 D 盘（临时目录也指到 D，避免占用 C）
$env:TEMP='D:\temp'; $env:TMP='D:\temp'
Start-Process "D:\Downloads\OllamaSetup.exe" `
  -ArgumentList '/VERYSILENT','/DIR=D:\Programs\Ollama','/SUPPRESSMSGBOXES','/NORESTART' -Wait
```

**② 模型目录也指到 D**（默认在 C 盘的 `~/.ollama`）
```powershell
[Environment]::SetEnvironmentVariable('OLLAMA_MODELS','D:\ollama\models','User')
```

**③ 下载 GGUF（走 hf-mirror，因为 HF 直连不通）**
```powershell
curl.exe -L -C - -o "D:\ollama\gguf\M-Prometheus-3B.Q5_K_M.gguf" `
  "https://hf-mirror.com/mradermacher/M-Prometheus-3B-GGUF/resolve/main/M-Prometheus-3B.Q5_K_M.gguf"
```

**④ Modelfile + 导入**
```
# D:\ollama\Modelfile
FROM D:/ollama/gguf/M-Prometheus-3B.Q5_K_M.gguf
PARAMETER temperature 0
PARAMETER num_ctx 4096
```
```powershell
ollama create m-prometheus -f D:\ollama\Modelfile
ollama list      # 应看到 m-prometheus:latest
ollama ps        # 应显示 100% GPU
```

**⑤ 端点**：Ollama 暴露 OpenAI 兼容接口 `http://localhost:11434/v1`。
（注意：Ollama 是本地服务，重启电脑后需 `ollama serve` 或启动 Ollama app。）

**模型信息**：M-Prometheus-3B，基座 `Qwen2.5-3B-Instruct`（非 DeepSeek 家族），48 万条多语言评判数据训练，多语言友好；Q5 量化仅 2GB，8GB 显存轻松。

---

## 5. 官方 prompt 格式（"拼 prompt"到底拼什么）

**格式很敏感**——裁判是按固定模板训练的，格式不对分数就不准。来源：模型官方卡（M-Prometheus README / Prometheus-2）。

**① 系统 prompt**
```
You are a fair judge assistant tasked with providing clear, objective feedback
based on specific criteria, ensuring each assessment reflects the absolute standards set for performance.
```

**② 任务模板**（占位符 `{instruction}`/`{response}`/`{reference}`/`{rubric}`）
```
###Task Description:
...（4 条规则：先写反馈→再给 1~5 分→格式 "Feedback: ... [RESULT] n"）...
###The instruction to evaluate:
{instruction}
###Response to evaluate:
{response}
###Reference Answer (Score 5):
{reference}
###Score Rubrics:
{rubric}
###Feedback:
```

**③ 官方拼法**：把两段**拼成同一条 user 消息**（不用单独 system 角色）
```python
user_content = SYSTEM_PROMPT + "\n\n" + TASK_PROMPT.format(...)
messages = [{"role": "user", "content": user_content}]
```
> 我们一开始把系统 prompt 放在 Ollama Modelfile 的 `SYSTEM` 字段，后**改为官方拼法**（拼进 user）。

**④ 输出解析**：从返回里正则取 `[RESULT] n`，`[RESULT]` 之前的就是反馈文字。
```java
Pattern RESULT_PATTERN = Pattern.compile("\\[RESULT]\\s*(\\d)");
```

---

## 6. Rubric 设计（我们定的三维度）

- **忠实度**：回复是否只依据检索结果，无编造（幻觉检测）
- **相关性**：推荐的商品与用户需求是否匹配
- **语气**：是否符合"贴心买手"人设（温和、专业、不硬销）

完整 Score 1~5 描述见 `JudgeRubrics.java`。

> **注意**：Prometheus 绝对打分**一次只接一个 rubric** → 一条回复要判 **3 次**（三维度各一次）。

---

## 7. 代码实现

| 文件 | 作用 |
|------|------|
| `LLmJudge.java` | 拼官方模板 → POST `localhost:11434/v1/chat/completions` → 解析 `[RESULT]`。含测试用构造器；内含嵌套 `JudgeResult` |
| `JudgeRubrics.java` | 三个维度的 rubric 常量 |
| `LLmJudgeCalibrationTest.java` | 校准测试（不依赖 Spring/DB，只打裁判；用人工造的假回复） |
| `LLMJudgeAgentEvalTest.java` | 真实 Agent 评测（`@SpringBootTest`；跑真 agent → 裁判三维度打分 → 报告） |

**配置**（`application.yml`，已被 gitignore）：
```yaml
judge:
  base-url: http://localhost:11434/v1
  api-key: not-needed
  model-id: m-prometheus
  timeout-seconds: 180
```

---

## 8. 踩坑与经验（重点！）

1. **必须给 `reference answer`（满分示范）** ⭐
   空 reference 时，裁判会把"不在 reference 里的信息"全当编造 → **好回复也判 1 分**。
   我们第一版校准就是这样翻车的，补上 reference 后 有据=5 / 幻觉=1，正常了。

2. **忠实度要把"检索结果"写成"唯一事实来源"**放进 instruction，裁判才有依据判断有没有编造。

3. **中文输出不稳定**：即使系统 prompt 是中文，3B 裁判的反馈**一会儿中文一会儿英文**。
   小模型通病；**分数不受影响**。想强制中文可把 `###Feedback:` 改成 `###Feedback（请用简体中文撰写）:`（实测有效），但会偏离官方模板。

4. **一次一个 rubric**：三维度 = 三次调用（可并行，但注意本地模型串行）。

5. **同源偏见**：见 §3。用不同家族裁判。

6. **免费路线**：本地 Ollama 最省心（无额度、无 Key、不同家族）。

7. **Windows 中文户名 + PowerShell**：路径含中文时控制台易乱码，`chcp 65001` + `[Console]::OutputEncoding=UTF8` 可缓解；写文件用 `Out-File -Encoding UTF8` 再读。

---

## 9. 校准结果（`LLmJudgeCalibrationTest`）

| 维度 | 好样本 | 坏样本 | 是否区分 |
|------|:-----:|:-----:|:-------:|
| 忠实度 | 5 | 1 | ✅ |
| 相关性 | 5 | 1 | ✅ |
| 语气 | 2 | 1 | ✅（方向对，裁判偏严） |

判定标准：**好样本分数 > 坏样本分数** 即可（校准的核心是"方向对不对"，不是绝对分值）。

---

## 10. 如何运行

```powershell
# 1. 确保 Ollama 在跑（本地服务）
ollama list        # 看到 m-prometheus 即可
ollama ps          # 可选：确认 GPU

# 2. 跑校准测试（测裁判，不依赖 Spring/DB）
mvn -f server/pom.xml test -Dtest=LLmJudgeCalibrationTest

# 3. 跑真实 Agent 评测（@SpringBootTest，需 MySQL/ChromaDB 可达；embedding 会自动拉起）
mvn -f server/pom.xml test -Dtest=LLMJudgeAgentEvalTest
```

---

## 11. 真实 Agent 评测（结果与经验）

`LLMJudgeAgentEvalTest`（`@SpringBootTest`）：15 条带约束的 query → 生成回复 → 三维度裁判。

### ⭐ 关键坑：评测的"上下文"必须和 agent 真正用的那批一致

第一版评测里，测试用**原始 query 单独检索**一次当上下文。但 agent 内部会
`preprocessQuery`（去"推荐/一款"等噪声词）+ `augmentQuery` 后再检索 —— **两边检索结果可能不同**，
于是 agent 推荐的商品在裁判的上下文里"不存在" → 被判**编造** → 忠实度虚低（2.73）。

**解决**：给 `ChatService` 加了 `chatWithContext()`，返回 `{reply, products}`（**同一批**商品）；
`chat()` 改为复用它，**生产逻辑不变**（只是把本就已经检索到的 `products` 一并暴露）。

### 效果对比

| 指标 | 上下文不一致(旧) | 用 agent 真实上下文(新) |
|------|:---:|:---:|
| 相关性 | 4.33 | 4.47 |
| 语气 | 3.47 | 3.33 |
| **忠实度** | 2.73 | **4.27** |

→ 忠实度大幅回升，坐实"上下文不一致 → 误判编造"。

### 另一个经验：语气维度裁判偏严
低分案例多是"询问是否提高预算/规格"这类**正常礼貌话术**，却被判 1~2 分。
3B 裁判 + score-5 定义偏高 → 语气系统性偏低。
**对策**：放宽 tone rubric / 加锚点，或接受"只看相对趋势"。

### 还顺带发现了 agent 的真实行为问题
它有时**推荐超预算商品**、再问"是否提高预算"（平板/跑鞋/瑜伽裤案例），体验欠佳。
—— 这正是 LLM-as-Judge 的价值：把难量化的"体验问题"量化出来。

---

## 12. 局限与下一步

- **裁判质量**：3B 偏弱、偏严、语言不稳；要求高可换 M-Prometheus-7B（需 4-bit）或更强的独立模型。
- **偏差缓解**：Pairwise 时**交换 A/B 顺序各跑一次**取平均；用不同的 rubric 交叉验证。
- **校准**：先人工标 10~20 条，看裁判与人的一致率（agreement）。
- **成本/确定性**：`temperature=0`；本地推理无 API 费。

---

## 附：常用命令

```powershell
# 启动 Ollama 服务（若没自启）
& 'D:\Programs\Ollama\ollama.exe' serve

# 看模型
& 'D:\Programs\Ollama\ollama.exe' list
& 'D:\Programs\Ollama\ollama.exe' ps

# 直接命令行问裁判（快速验证）
& 'D:\Programs\Ollama\ollama.exe' run m-prometheus
```
