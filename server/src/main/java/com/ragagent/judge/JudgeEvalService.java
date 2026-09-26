package com.ragagent.judge;

import com.ragagent.model.ProductSearchResult;
import com.ragagent.repository.JudgeRunRepository;
import com.ragagent.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 真实 Agent 评测：跑真 agent → 三维度 LLM-as-Judge 打分 → 落库（judge_runs / judge_cases）。
 *
 * <p>从 {@code LLMJudgeAgentEvalTest} 抽出，便于：
 * ① 测试与业务逻辑解耦；② 结果持久化，后台可看"每次迭代的进步/退步"。
 */
@Service
public class JudgeEvalService {

    private static final Logger log = LoggerFactory.getLogger(JudgeEvalService.class);

    /** 一条评测用例：query + 该 query 的"满分示范"回答（gold） */
    public record EvalCase(String query, String goldReply) {}

    /** 单条用例结果 */
    public record CaseResult(String query, String context, String reply,
                             int relevance, int tone, int faithfulness, long elapsedMs) {}

    /** 一次运行的汇总 */
    public record RunSummary(long runId, int caseCount,
                             double avgRelevance, double avgTone, double avgFaithfulness,
                             List<CaseResult> cases) {}

    /** 忠实度维度用的标准说明式 reference（不绑定具体商品，避免"没推荐 gold 就算编造"） */
    private static final String FAITH_REFERENCE =
            "回答仅使用检索结果中出现的商品、价格与卖点，不添加任何未在检索结果中提及的信息。";
    /** 语气维度用统一的满分示范（风格与具体 query 无关） */
    private static final String TONE_REFERENCE =
            "这款商品质地很清爽，很适合你的需求，需要我帮你加入购物车吗？";

    private final ChatService chatService;
    private final LLmJudge judge;
    private final JudgeRunRepository repo;

    @Value("${volcengine.chat.model-id:unknown}")
    private String generatorModel;

    @Value("${judge.model-id:unknown}")
    private String judgeModel;

    public JudgeEvalService(ChatService chatService, LLmJudge judge, JudgeRunRepository repo) {
        this.chatService = chatService;
        this.judge = judge;
        this.repo = repo;
    }

    /** 跑一轮评测 → 落库 → 返回汇总 */
    public RunSummary run(List<EvalCase> cases, String note) {
        int n = cases.size();
        List<CaseResult> results = new ArrayList<>(n);
        double sumRel = 0, sumTone = 0, sumFaith = 0;

        for (int i = 0; i < n; i++) {
            EvalCase c = cases.get(i);
            String session = "eval-" + System.currentTimeMillis() + "-" + i; // 每条独立 session
            long t0 = System.currentTimeMillis();

            // 1) 真 agent 生成回复 + 本次实际检索到的商品（同一批，避免上下文不一致）
            ChatService.ChatResult r = chatService.chatWithContext(session, null, null, c.query());
            String reply = r.reply();
            String context = buildContext(r.products());

            // 2) 三维度裁判（一次一个 rubric）
            LLmJudge.JudgeResult rel = judge.judge(c.query(), reply, c.goldReply(), JudgeRubrics.RELEVANCE);
            LLmJudge.JudgeResult tone = judge.judge(c.query(), reply, TONE_REFERENCE, JudgeRubrics.TONE);
            LLmJudge.JudgeResult faith = judge.judge(context + "\n" + c.query(), reply, FAITH_REFERENCE, JudgeRubrics.FAITHFULNESS);

            long elapsed = System.currentTimeMillis() - t0;
            results.add(new CaseResult(c.query(), context, reply,
                    rel.score(), tone.score(), faith.score(), elapsed));
            sumRel += rel.score();
            sumTone += tone.score();
            sumFaith += faith.score();
        }

        double avgRel = sumRel / n, avgTone = sumTone / n, avgFaith = sumFaith / n;
        long runId = repo.insertRun(LocalDateTime.now(), generatorModel, judgeModel, n, avgFaith, avgRel, avgTone, note);
        for (CaseResult cr : results) {
            repo.insertCase(runId, cr.query(), cr.reply(), cr.faithfulness(), cr.relevance(), cr.tone(), cr.elapsedMs());
        }
        log.info("评测完成并落库 | runId={} | 用例={} | 相关性={} 语气={} 忠实度={}",
                runId, n, avgRel, avgTone, avgFaith);
        return new RunSummary(runId, n, avgRel, avgTone, avgFaith, results);
    }

    /** 把召回的商品拼成"唯一事实来源"文本，供忠实度维度判断有无编造 */
    private String buildContext(List<ProductSearchResult> products) {
        StringBuilder sb = new StringBuilder("【检索到的商品（唯一事实来源，回复只能基于以下信息）】\n");
        if (products == null || products.isEmpty()) {
            sb.append("（无）\n");
            return sb.toString();
        }
        for (ProductSearchResult p : products) {
            sb.append(String.format("- %s（%s，¥%.0f）：%s%n",
                    p.getTitle(), p.getCategory(), p.getBasePrice(),
                    p.getMarketingDescription() == null ? "" : p.getMarketingDescription()));
        }
        return sb.toString();
    }
}
