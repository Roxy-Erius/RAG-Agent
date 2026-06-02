package com.ragagent;

import com.ragagent.model.ProductSearchResult;
import com.ragagent.service.RetrieverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

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

    private List<TestQuery> buildTestQueries() {
        List<TestQuery> queries = new ArrayList<>();

        // ---------- 美妆护肤 ----------
        queries.add(new TestQuery("text", "保湿面霜", null,
                List.of("p_beauty_007", "p_beauty_008", "p_beauty_012"), 10));
        queries.add(new TestQuery("text", "适合敏感肌的面霜", null,
                List.of("p_beauty_007", "p_beauty_012"), 10));
        queries.add(new TestQuery("text", "洗面奶", null,
                List.of("p_beauty_011", "p_beauty_006"), 10));
        queries.add(new TestQuery("text", "抗老精华", null,
                List.of("p_beauty_001", "p_beauty_002"), 10));
        queries.add(new TestQuery("text", "防晒霜", null,
                List.of("p_beauty_013", "p_beauty_014"), 10));
        queries.add(new TestQuery("text", "卸妆油", null,
                List.of("p_beauty_017"), 10));

        // ---------- 数码电子 ----------
        queries.add(new TestQuery("text", "蓝牙耳机", null,
                List.of("p_digital_007", "p_digital_018"), 10));
        queries.add(new TestQuery("text", "笔记本电脑", null,
                List.of("p_digital_020", "p_digital_021"), 10));
        queries.add(new TestQuery("text", "手机", null,
                List.of("p_digital_001", "p_digital_002", "p_digital_003"), 10));
        queries.add(new TestQuery("text", "平板电脑", null,
                List.of("p_digital_019"), 10));

        // ---------- 家居生活 ----------
        queries.add(new TestQuery("text", "枕头", null,
                List.of("p_home_009", "p_home_010"), 10));
        queries.add(new TestQuery("text", "床上用品四件套", null,
                List.of("p_home_011", "p_home_012"), 10));
        queries.add(new TestQuery("text", "毛巾浴巾", null,
                List.of("p_home_007", "p_home_008"), 10));

        // ---------- 食品保健 ----------
        queries.add(new TestQuery("text", "蛋白粉", null,
                List.of("p_food_001", "p_food_002"), 10));
        queries.add(new TestQuery("text", "维生素", null,
                List.of("p_food_003", "p_food_004"), 10));
        queries.add(new TestQuery("text", "益生菌", null,
                List.of("p_food_005", "p_food_006"), 10));

        // ---------- 跨类目干扰测试 ----------
        queries.add(new TestQuery("text", "降噪耳机推荐", null,
                List.of("p_digital_007", "p_digital_018"), 10));

        return queries;
    }

    // ==================== 评测主流程 ====================

    @Test
    void evaluateRetrievalQuality() {
        List<TestQuery> queries = buildTestQueries();

        int total = queries.size();
        int hitAt1 = 0;
        int hitAt3 = 0;
        int hitAt5 = 0;
        int hitAt10 = 0;
        double mrr = 0.0;

        System.out.println();
        System.out.println("========== 召回质量评测 ==========");
        System.out.println();

        for (TestQuery q : queries) {
            List<ProductSearchResult> results;
            if ("text".equals(q.mode)) {
                results = retrieverService.retrieveProductsByText(q.queryText, q.topK, null);
            } else if ("image".equals(q.mode)) {
                results = List.of();  // 图片检索暂不评测
            } else {
                // hybrid 暂不测（需要真实图片文件）
                System.out.printf("[SKIP] %s | mode=%s 需要图片文件%n", q.label(), q.mode);
                continue;
            }

            List<String> resultIds = results.stream()
                    .map(ProductSearchResult::getProductId)
                    .toList();

            // 判断命中
            boolean h1 = false, h3 = false, h5 = false, h10 = false;
            double reciprocalRank = 0.0;
            for (int i = 0; i < resultIds.size(); i++) {
                if (q.expectedIds.contains(resultIds.get(i))) {
                    if (i < 1) h1 = true;
                    if (i < 3) h3 = true;
                    if (i < 5) h5 = true;
                    if (i < 10) h10 = true;
                    if (reciprocalRank == 0.0) {
                        reciprocalRank = 1.0 / (i + 1);
                    }
                }
            }
            if (h1) hitAt1++;
            if (h3) hitAt3++;
            if (h5) hitAt5++;
            if (h10) hitAt10++;
            mrr += reciprocalRank;

            // 打印单条用例详情
            String top5Str = String.join(", ", resultIds.subList(0, Math.min(5, resultIds.size())));
            String hitMark = h1 ? "HIT@1" : (h3 ? "HIT@3" : (h5 ? "HIT@5" : (h10 ? "HIT@10" : "MISS")));
            System.out.printf("[%s] %-20s | expected: %-30s | top5: [%s]%n",
                    hitMark, q.label(), q.expectedIds, top5Str);
        }

        System.out.println();
        System.out.println("===== 汇总 =====");
        System.out.printf("总数:    %d%n", total);
        System.out.printf("Hit@1:   %d/%d = %.2f%%%n", hitAt1, total, 100.0 * hitAt1 / total);
        System.out.printf("Hit@3:   %d/%d = %.2f%%%n", hitAt3, total, 100.0 * hitAt3 / total);
        System.out.printf("Hit@5:   %d/%d = %.2f%%%n", hitAt5, total, 100.0 * hitAt5 / total);
        System.out.printf("Hit@10:  %d/%d = %.2f%%%n", hitAt10, total, 100.0 * hitAt10 / total);
        System.out.printf("MRR:     %.4f%n", mrr / total);
        System.out.println();

        // 至少应该不是全 MISS
        assertFalse(hitAt5 == 0, "Hit@5 为 0，召回完全失效");
    }
}
