package com.ragagent;

import com.ragagent.judge.JudgeRubrics;
import com.ragagent.judge.LLmJudge;
import com.ragagent.judge.LLmJudge.JudgeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM-as-Judge 校准测试：用人工造的「好/坏」样本，验证裁判打分方向正确。该Test测试的是裁判能否按要求正确打分
 *
 * <p>关键经验（见 docs/LLM-as-Judge.md）：
 * <ul>
 *   <li>必须提供 reference answer（满分示范），否则裁判会把「不在 reference 里的」都当编造 → 全判低分</li>
 *   <li>忠实度维度要把「检索结果」作为唯一事实来源写进 instruction</li>
 * </ul>
 *
 * <p>前置：本地 Ollama 运行且已导入 m-prometheus。
 */
class LLmJudgeCalibrationTest {

    private final LLmJudge judge =
            new LLmJudge("http://localhost:11434/v1", "not-needed", "m-prometheus", 180);

    // ===== 忠实度样本 =====
    private static final String FACT_CONTEXT = """
            用户问题：推荐一款适合油性皮肤的保湿面霜。
            以下是检索到的商品（唯一事实来源，回复只能基于这些信息）：
            理肤泉特安舒缓修复霜（面霜，价格285元，卖点：清爽不油腻，适合油性皮肤保湿）""";
    private static final String FACT_REFERENCE =
            "推荐「理肤泉特安舒缓修复霜」，价格285元，质地清爽不油腻，适合油性皮肤保湿。";

    // ===== 相关性样本 =====
    private static final String RELEVANCE_Q = "用户想要一款适合油性皮肤的保湿面霜。";
    private static final String RELEVANCE_REFERENCE =
            "推荐「理肤泉特安舒缓修复霜」，质地清爽，适合油性皮肤保湿。";

    // ===== 语气样本 =====
    private static final String TONE_REFERENCE =
            "这款理肤泉修复霜质地很清爽，很适合油性皮肤，需要我帮你加入购物车吗？";

    @Test
    void calibration() {
        // ---- 忠实度：有据 vs 编造 ----
        JudgeResult fGood = judge.judge(FACT_CONTEXT,
                "推荐「理肤泉特安舒缓修复霜」，价格285元，质地清爽不油腻，很适合油性皮肤。",
                FACT_REFERENCE, JudgeRubrics.FAITHFULNESS);
        JudgeResult fBad = judge.judge(FACT_CONTEXT,
                "推荐「海蓝之谜精华面霜」，价格4500元，富含深海巨藻，油皮用完逆龄10岁。",
                FACT_REFERENCE, JudgeRubrics.FAITHFULNESS);
        report("忠实度-有据", fGood);
        report("忠实度-幻觉", fBad);
        assertTrue(fGood.score() > fBad.score(), "有据回复的忠实度应高于幻觉回复");

        // ---- 相关性：切题 vs 答非所问 ----
        JudgeResult rGood = judge.judge(RELEVANCE_Q,
                "推荐「理肤泉特安舒缓修复霜」，质地清爽，适合油性皮肤保湿。",
                RELEVANCE_REFERENCE, JudgeRubrics.RELEVANCE);
        JudgeResult rBad = judge.judge(RELEVANCE_Q,
                "推荐这款「华为蓝牙耳机」，音质很好，支持主动降噪。",
                RELEVANCE_REFERENCE, JudgeRubrics.RELEVANCE);
        report("相关性-切题", rGood);
        report("相关性-答非所问", rBad);
        assertTrue(rGood.score() > rBad.score(), "切题回复的相关性应高于答非所问");

        // ---- 语气：温和 vs 硬销 ----
        JudgeResult tGood = judge.judge(RELEVANCE_Q,
                "这款理肤泉修复霜质地很清爽，很适合油性皮肤，需要我帮你加入购物车吗？",
                TONE_REFERENCE, JudgeRubrics.TONE);
        JudgeResult tBad = judge.judge(RELEVANCE_Q,
                "赶紧买！不买就亏大了！史上最低价，错过再等一年，手慢无！！！",
                TONE_REFERENCE, JudgeRubrics.TONE);
        report("语气-温和", tGood);
        report("语气-硬销", tBad);
        assertTrue(tGood.score() > tBad.score(), "温和回复的语气应高于硬销");
    }

    private void report(String name, JudgeResult r) {
        System.out.printf("[%s] score=%d | %s%n", name, r.score(), r.feedback());
    }
}
