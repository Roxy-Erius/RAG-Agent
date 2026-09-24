package com.ragagent;

import com.ragagent.judge.JudgeRubrics;
import com.ragagent.judge.LLmJudge;
import com.ragagent.judge.LLmJudge.*;
import com.ragagent.model.ProductSearchResult;
import com.ragagent.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;


/**
 * 真实 Agent 评测：跑真 agent 生成回复 → 用 LlmJudge 三维度打分 → 汇总报告。
 *
 * 前置：Ollama 在跑（m-prometheus）；MySQL/ChromaDB 可达；embedding 会自动拉起。
 * 运行：mvn -f server/pom.xml test -Dtest=LlmJudgeAgentEvalTest
 *
 * 注意：reference（满分示范）必须填；startsWith "满分示范"是为了给裁判锚点，
 * 空 reference 会让裁判把"不在 reference 里的"都当编造 → 好回复也判低分。
 */

@SpringBootTest
public class LLMJudgeAgentEvalTest {
    @Autowired
    ChatService chatService;
    @Autowired
    LLmJudge judge;
    /** 一条评测用例：query + 该 query 的"满分示范"回答（gold） */
    record EvalCase(String query, String goldReply) {}

    /** 忠实度维度用的标准说明式 reference（不绑定具体商品，避免"没推荐 gold 就算编造"） */
    private static final String FAITH_REFERENCE =
            "回答仅使用检索结果中出现的商品、价格与卖点，不添加任何未在检索结果中提及的信息。";
    /** 语气维度用统一的满分示范（风格与具体 query 无关） */
    private static final String TONE_REFERENCE =
            "这款商品质地很清爽，很适合你的需求，需要我帮你加入购物车吗？";

    /** 15 条评测集（都带足信息，避免 agent 追问；最后一条测"库里没有时会不会老实承认"） */
    private static final List<EvalCase> CASES = List.of(
            // ---- 美妆护肤 ----
            new EvalCase("推荐一款适合油性皮肤的保湿面霜，预算300元以内",
                    "推荐「理肤泉特安舒缓修复霜」，质地清爽不油腻，适合油性皮肤保湿，价格约285元。"),
            new EvalCase("我是敏感肌，想买支日常通勤用的防晒霜",
                    "推荐「理肤泉特护清盈防晒乳」，高倍防晒、清爽控油，适合敏感肌日常通勤。"),
            new EvalCase("想买一瓶抗初老的精华，预算1000元以内",
                    "推荐「雅诗兰黛特润修护肌活精华露」，主打淡纹紧致、抗初老。"),
            new EvalCase("想要温和一点的卸妆油，价格200元以内",
                    "推荐「芳珂纳米温和净化卸妆油」，温和无添加、深层清洁。"),
            // ---- 数码电子 ----
            new EvalCase("帮我找一款主动降噪的蓝牙耳机，预算2000元以内",
                    "推荐「华为 FreeBuds Pro 5」，支持主动降噪，音质出色。"),
            new EvalCase("想要一台轻薄的笔记本电脑，预算8000元以内，主要办公用",
                    "推荐「联想 ThinkBook 14+」或「Apple MacBook Air M5」，轻薄便携，适合办公。"),
            new EvalCase("学生党买平板，预算3000元以内，主要上网课做笔记",
                    "推荐「小米平板 8 Pro」，大屏高刷，适合上网课和做笔记。"),
            new EvalCase("想买一款拍照好的手机，预算5000元以内",
                    "推荐「华为 Pura 90 Pro」，超感光影像，拍照出色。"),
            // ---- 服饰运动 ----
            new EvalCase("想买一双日常慢跑的跑鞋，预算800元以内",
                    "推荐「Nike Air Zoom Pegasus 41」，缓震舒适，适合日常慢跑。"),
            new EvalCase("想买一双防水的户外徒步鞋，预算1000元以内",
                    "推荐「SALOMON X ULTRA 4 GORE-TEX」，防水抓地，适合户外徒步。"),
            new EvalCase("需要一个通勤用的双肩背包，能放下15寸笔记本",
                    "推荐「The North Face Borealis 28L」，多功能通勤，容量充足。"),
            new EvalCase("想买一条女生练瑜伽穿的紧身裤，预算500元以内",
                    "推荐「Lululemon Align 高腰紧身裤」，柔软裸感，适合瑜伽。"),
            // ---- 食品饮料 ----
            new EvalCase("推荐几款速溶咖啡，预算100元以内",
                    "推荐「三顿半数字星球超即溶咖啡」，精品速溶，风味好。"),
            new EvalCase("想买无糖的茶饮料，要一整箱的",
                    "推荐「农夫山泉东方树叶无糖乌龙茶」，0糖0卡，清爽解腻。"),
            // ---- 库里没有，测"会不会老实承认"（不追问、不编造）----
            new EvalCase("推荐一款防脱发的洗发水，预算200元以内",
                    "很抱歉，商品库中暂时没有防脱发洗发水，可以看看其他护肤商品。")
    );

    @Test
    void evalRealAgent() {
        int n = CASES.size();
        double sumRel = 0, sumTone = 0, sumFaith = 0;

        System.out.println("\n===== 真实 Agent 评测（LLM-as-Judge）=====");
        for (int i = 0; i < n; i++) {
            EvalCase c = CASES.get(i);
            String session = "eval-" + i;   // 每条独立 session，避免对话历史互相影响

            // 1) 真 agent 生成回复 + 拿到它本次实际使用的检索商品（同一批，避免上下文不一致）
            ChatService.ChatResult result = chatService.chatWithContext(session, null, null, c.query());
            String reply = result.reply();

            // 2) 召回上下文（忠实度维度的判断依据）= agent 真实用到的那批商品
            String context = buildContext(result.products());

            // 3) 三维度裁判（一次一个 rubric，共 3 次调用）
            JudgeResult rel   = judge.judge(c.query(), reply, c.goldReply(), JudgeRubrics.RELEVANCE);
            JudgeResult tone  = judge.judge(c.query(), reply, TONE_REFERENCE, JudgeRubrics.TONE);
            JudgeResult faith = judge.judge(context + "\n" + c.query(), reply, FAITH_REFERENCE, JudgeRubrics.FAITHFULNESS);

            sumRel += rel.score();
            sumTone += tone.score();
            sumFaith += faith.score();

            System.out.printf("%n[Q] %s%n[召回] %s%n[回复] %s%n[分] 相关性=%d 语气=%d 忠实度=%d%n",
                    c.query(), context.replace("\n", " "), reply, rel.score(), tone.score(), faith.score());
        }

        System.out.printf("%n===== 汇总（%d 条）=====%n相关性=%.2f  语气=%.2f  忠实度=%.2f%n",
                n, sumRel / n, sumTone / n, sumFaith / n);
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
