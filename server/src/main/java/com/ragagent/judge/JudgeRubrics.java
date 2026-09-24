package com.ragagent.judge;

/**
 * 导购场景的三个评判维度 rubric（评分细则）。
 *
 * <p>rubric = 评分标准：既告诉裁判「评什么」（维度），也规定「每一档分数对应什么质量」。
 * 给 rubric 是为了让打分有锚点、可复现、可解释。
 */
public final class JudgeRubrics {

    private JudgeRubrics() {}

    /** 忠实度：回复是否只依据检索到的商品信息，无编造（幻觉检测） */
    public static final String FAITHFULNESS = """
            [忠实度]
            Score 1: 回复中大量商品、价格或功效信息并非来自给定检索结果，属明显编造（幻觉）。
            Score 2: 回复有多处内容无法在检索结果中找到依据。
            Score 3: 回复大体基于检索结果，但存在个别无依据的细节或夸大。
            Score 4: 回复基本基于检索结果，仅个别措辞略微超出。
            Score 5: 回复完全基于给定检索结果，所有商品与信息均有据可查，无任何编造。""";

    /** 相关性：推荐的商品与用户需求是否匹配 */
    public static final String RELEVANCE = """
            [相关性]
            Score 1: 推荐的商品与用户需求完全无关。
            Score 2: 推荐的商品与用户需求关联很弱。
            Score 3: 推荐的商品大致符合需求，但不是最优选择。
            Score 4: 推荐的商品比较符合需求，基本合适。
            Score 5: 推荐的商品与用户需求高度匹配，是最优选择。""";

    /** 语气：是否符合「贴心买手」人设 */
    public static final String TONE = """
            [语气]
            Score 1: 语气生硬、夸张或强推销，令人反感。
            Score 2: 语气偏硬或过度营销，亲和力不足。
            Score 3: 语气中性、基本得体，但缺乏导购的亲切感。
            Score 4: 语气温和有礼，比较像专业买手。
            Score 5: 语气温和、专业、简洁有礼，完全符合“贴心买手”人设。""";
}
