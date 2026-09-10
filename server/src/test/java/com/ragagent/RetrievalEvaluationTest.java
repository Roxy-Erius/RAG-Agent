package com.ragagent;

import com.ragagent.model.ProductSearchResult;
import com.ragagent.service.RetrieverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class RetrievalEvaluationTest {

    @Autowired
    private RetrieverService retrieverService;

    // ==================== 测试用例定义 ====================

    static class TestQuery {
        final String mode;          // text / image / hybrid
        final String queryText;
        final String imagePath;
        final List<String> expectedIds;  // 至少应出现在 Top-K 的商品 ID
        final int topK;

        TestQuery(String mode, String queryText, String imagePath, List<String> expectedIds, int topK) {
            this.mode = mode;
            this.queryText = queryText;
            this.imagePath = imagePath;
            this.expectedIds = expectedIds;
            this.topK = topK;
        }

        String label() {
            return queryText != null ? queryText : imagePath;
        }
    }

    /**
     * 评测集：期望 ID 全部基于真实商品的 sub_category 字段（权威 ground truth）。
     * 覆盖 4 个类目：美妆护肤 / 数码电子 / 服饰运动 / 食品饮料。
     * 注意：不要使用不存在的商品 ID（旧版曾写 p_home_*，库里没有 home 类目）。
     */
    private List<TestQuery> buildTestQueries() {
        List<TestQuery> queries = new ArrayList<>();

        // ---------- 美妆护肤 ----------
        queries.add(new TestQuery("text", "保湿面霜", null,
                List.of("p_beauty_007", "p_beauty_008", "p_beauty_012"), 10));   // 面霜
        queries.add(new TestQuery("text", "适合敏感肌的面霜", null,
                List.of("p_beauty_007", "p_beauty_012"), 10));                     // 面霜
        queries.add(new TestQuery("text", "洗面奶", null,
                List.of("p_beauty_011"), 10));                                     // 洁面
        queries.add(new TestQuery("text", "防晒霜", null,
                List.of("p_beauty_006", "p_beauty_010", "p_beauty_023"), 10));     // 防晒
        queries.add(new TestQuery("text", "卸妆油", null,
                List.of("p_beauty_017"), 10));                                     // 卸妆
        queries.add(new TestQuery("text", "抗老精华", null,
                List.of("p_beauty_001", "p_beauty_002", "p_beauty_004", "p_beauty_024"), 10)); // 精华
        queries.add(new TestQuery("text", "眼部护理眼霜", null,
                List.of("p_beauty_016", "p_beauty_021"), 10));                     // 眼霜
        queries.add(new TestQuery("text", "补水面膜", null,
                List.of("p_beauty_022"), 10));                                     // 面膜

        // ---------- 数码电子 ----------
        queries.add(new TestQuery("text", "蓝牙耳机", null,
                List.of("p_digital_007", "p_digital_018"), 10));                   // 真无线耳机
        queries.add(new TestQuery("text", "笔记本电脑", null,
                List.of("p_digital_004", "p_digital_006", "p_digital_012",
                        "p_digital_020", "p_digital_022", "p_digital_023"), 10));  // 笔记本电脑
        queries.add(new TestQuery("text", "手机", null,
                List.of("p_digital_001", "p_digital_002", "p_digital_003", "p_digital_008",
                        "p_digital_009", "p_digital_010", "p_digital_014", "p_digital_015",
                        "p_digital_016", "p_digital_017"), 10));                   // 智能手机
        queries.add(new TestQuery("text", "平板电脑", null,
                List.of("p_digital_005", "p_digital_011", "p_digital_013", "p_digital_019",
                        "p_digital_021", "p_digital_024", "p_digital_025"), 10));  // 平板电脑
        queries.add(new TestQuery("text", "主动降噪耳机", null,
                List.of("p_digital_007", "p_digital_018"), 10));                   // 真无线耳机

        // ---------- 服饰运动 ----------
        queries.add(new TestQuery("text", "男士运动T恤", null,
                List.of("p_clothes_001", "p_clothes_002", "p_clothes_003"), 10));  // 短袖T恤
        queries.add(new TestQuery("text", "跑步鞋", null,
                List.of("p_clothes_007", "p_clothes_008", "p_clothes_009", "p_clothes_010"), 10)); // 跑步鞋
        queries.add(new TestQuery("text", "篮球鞋", null,
                List.of("p_clothes_011", "p_clothes_012", "p_clothes_013"), 10));  // 篮球鞋
        queries.add(new TestQuery("text", "户外徒步鞋", null,
                List.of("p_clothes_014", "p_clothes_015"), 10));                   // 徒步鞋
        queries.add(new TestQuery("text", "双肩背包", null,
                List.of("p_clothes_018", "p_clothes_025"), 10));                   // 背包
        queries.add(new TestQuery("text", "连帽卫衣", null,
                List.of("p_clothes_005", "p_clothes_022"), 10));                   // 卫衣
        queries.add(new TestQuery("text", "瑜伽裤", null,
                List.of("p_clothes_016"), 10));                                    // 瑜伽裤

        // ---------- 食品饮料 ----------
        queries.add(new TestQuery("text", "咖啡", null,
                List.of("p_food_001", "p_food_002", "p_food_022", "p_food_023"), 10)); // 咖啡
        queries.add(new TestQuery("text", "牛奶", null,
                List.of("p_food_007", "p_food_016"), 10));                         // 牛奶
        queries.add(new TestQuery("text", "酸奶", null,
                List.of("p_food_008", "p_food_017"), 10));                         // 酸奶
        queries.add(new TestQuery("text", "方便面", null,
                List.of("p_food_011", "p_food_012", "p_food_020", "p_food_021"), 10)); // 方便食品
        queries.add(new TestQuery("text", "气泡水", null,
                List.of("p_food_004", "p_food_015", "p_food_024"), 10));           // 碳酸饮料
        queries.add(new TestQuery("text", "无糖茶饮料", null,
                List.of("p_food_003", "p_food_014"), 10));                         // 茶饮
        queries.add(new TestQuery("text", "功能饮料", null,
                List.of("p_food_005", "p_food_006", "p_food_025"), 10));           // 功能饮料
        queries.add(new TestQuery("text", "混合坚果零食", null,
                List.of("p_food_009", "p_food_019"), 10));                         // 坚果/零食

        return queries;
    }

    // ==================== 评测主流程 ====================

    static class Metrics {
        String label;
        int total, hit1, hit3, hit5, hit10;
        double mrr;
        double hit1P() { return 100.0 * hit1 / total; }
        double hit3P() { return 100.0 * hit3 / total; }
        double hit5P() { return 100.0 * hit5 / total; }
        double hit10P() { return 100.0 * hit10 / total; }
        double mrrV() { return mrr / total; }
    }

    private Metrics runEval(String label, Function<TestQuery, List<ProductSearchResult>> retriever, boolean verbose) {
        List<TestQuery> queries = buildTestQueries();
        Metrics m = new Metrics();
        m.label = label;
        m.total = queries.size();

        if (verbose) {
            System.out.println();
            System.out.println("========== 召回质量评测 [" + label + "] ==========");
            System.out.println();
        }

        for (TestQuery q : queries) {
            List<ProductSearchResult> results = retriever.apply(q);
            List<String> resultIds = results.stream().map(ProductSearchResult::getProductId).toList();

            boolean h1 = false, h3 = false, h5 = false, h10 = false;
            double reciprocalRank = 0.0;
            for (int i = 0; i < resultIds.size(); i++) {
                if (q.expectedIds.contains(resultIds.get(i))) {
                    if (i < 1) h1 = true;
                    if (i < 3) h3 = true;
                    if (i < 5) h5 = true;
                    if (i < 10) h10 = true;
                    if (reciprocalRank == 0.0) reciprocalRank = 1.0 / (i + 1);
                }
            }
            if (h1) m.hit1++;
            if (h3) m.hit3++;
            if (h5) m.hit5++;
            if (h10) m.hit10++;
            m.mrr += reciprocalRank;

            if (verbose) {
                String top5Str = String.join(", ", resultIds.subList(0, Math.min(5, resultIds.size())));
                String hitMark = h1 ? "HIT@1" : (h3 ? "HIT@3" : (h5 ? "HIT@5" : (h10 ? "HIT@10" : "MISS")));
                System.out.printf("[%s] %-20s | top5: [%s]%n", hitMark, q.label(), top5Str);
            }
        }
        System.out.printf(">> %-24s Hit@1=%.2f%% Hit@3=%.2f%% Hit@5=%.2f%% Hit@10=%.2f%% MRR=%.4f%n",
                m.label, m.hit1P(), m.hit3P(), m.hit5P(), m.hit10P(), m.mrrV());
        return m;
    }

    @Test
    void evaluateRetrievalQuality() {
        // 基线：纯文本 text→text
        Metrics textOnly = runEval("TEXT-ONLY",
                q -> retrieverService.retrieveProductsByText(q.queryText, q.topK, null), true);

        // 参数扫描：文本权重 / 图像权重 / 候选池倍数
        double[][] cfgs = {
                {0.4, 0.6, 4}, {0.35, 0.65, 4}, {0.3, 0.7, 4},
                {0.2, 0.8, 4}, {0.3, 0.7, 6}, {0.4, 0.6, 6}, {0.5, 0.5, 6},
        };

        System.out.println();
        System.out.println("===== 多模态参数扫描 =====");
        System.out.printf("%-26s %8s %8s %8s %8s %8s%n", "配置", "Hit@1", "Hit@3", "Hit@5", "Hit@10", "MRR");
        Metrics best = null;
        for (double[] c : cfgs) {
            String label = String.format("t=%.1f i=%.1f pool=%dx", c[0], c[1], (int) c[2]);
            Metrics m = runEval("MM " + label,
                    q -> retrieverService.retrieveProductsMultiModal(q.queryText, q.topK, null, c[0], c[1], (int) c[2]), false);
            System.out.printf("%-26s %7.2f%% %7.2f%% %7.2f%% %7.2f%% %8.4f%n", label,
                    m.hit1P(), m.hit3P(), m.hit5P(), m.hit10P(), m.mrrV());
            if (best == null || m.mrrV() > best.mrrV()) best = m;
        }

        System.out.println();
        System.out.println("===== 对比汇总 =====");
        System.out.printf("%-26s %8s %8s %8s %8s %8s%n", "模式", "Hit@1", "Hit@3", "Hit@5", "Hit@10", "MRR");
        System.out.printf("%-26s %7.2f%% %7.2f%% %7.2f%% %7.2f%% %8.4f%n", "TEXT-ONLY",
                textOnly.hit1P(), textOnly.hit3P(), textOnly.hit5P(), textOnly.hit10P(), textOnly.mrrV());
        System.out.printf("%-26s %7.2f%% %7.2f%% %7.2f%% %7.2f%% %8.4f%n", "MULTIMODAL(best: " + best.label + ")",
                best.hit1P(), best.hit3P(), best.hit5P(), best.hit10P(), best.mrrV());
        System.out.printf("提升(最优多模态-纯文本): Hit@1 %+.2fpp | Hit@3 %+.2fpp | Hit@5 %+.2fpp | Hit@10 %+.2fpp | MRR %+.4f%n",
                best.hit1P() - textOnly.hit1P(), best.hit3P() - textOnly.hit3P(),
                best.hit5P() - textOnly.hit5P(), best.hit10P() - textOnly.hit10P(),
                best.mrrV() - textOnly.mrrV());
        System.out.println();

        assertFalse(best.hit5 == 0, "Hit@5 为 0，召回完全失效");
    }
}
