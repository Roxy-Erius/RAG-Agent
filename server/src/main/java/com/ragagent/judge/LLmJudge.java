package com.ragagent.judge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-Judge：调用本地 M-Prometheus（Ollama，OpenAI 兼容端点）给回复打分。
 *
 * <p>严格按 Prometheus-2 / M-Prometheus 官方模板拼 prompt：
 * 系统 prompt + 任务模板拼成「同一条 user 消息」（官方做法）。
 *
 * <p>配置见 application.yml 的 {@code judge.*}（默认本地 Ollama）。
 */
@Service
public class LLmJudge {

    private static final Logger log = LoggerFactory.getLogger(LLmJudge.class);

    @Value("${judge.base-url:http://localhost:11434/v1}")
    private String baseUrl;

    @Value("${judge.api-key:not-needed}")
    private String apiKey;

    @Value("${judge.model-id:m-prometheus}")
    private String modelId;

    @Value("${judge.timeout-seconds:180}")
    private int timeoutSeconds;

    /** 官方系统 prompt。此处用中文版以强制中文反馈（英文系统 prompt 会导致反馈为英文）。 */
    private static final String SYSTEM_PROMPT =
            "你是一名公正的评审助手，需要依据给定标准给出清晰、客观的反馈，"
            + "确保评价符合标准规定的要求。请始终使用简体中文撰写反馈。";

    /** 官方任务模板（Prometheus-2 / M-Prometheus 一致），占位符顺序：instruction, response, reference, rubric */
    private static final String TASK_TEMPLATE = """
            ###Task Description:
            An instruction (might include an Input inside it), a response to evaluate, a reference answer that gets a score of 5, and a score rubric representing a evaluation criteria are given.
            1. Write a detailed feedback that assess the quality of the response strictly based on the given score rubric, not evaluating in general.
            2. After writing a feedback, write a score that is an integer between 1 and 5. You should refer to the score rubric.
            3. The output format should look as follows: "Feedback: (write a feedback for criteria) [RESULT] (an integer number between 1 and 5)"
            4. Please do not generate any other opening, closing, and explanations.

            ###The instruction to evaluate:
            %s

            ###Response to evaluate:
            %s

            ###Reference Answer (Score 5):
            %s

            ###Score Rubrics:
            %s

            ###Feedback:
            """;

    private static final Pattern RESULT_PATTERN = Pattern.compile("\\[RESULT]\\s*(\\d)");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final Gson gson = new Gson();

    public LLmJudge() { }   // Spring 字段注入用

    /** 供测试/独立调用：手动指定配置（不走 Spring） */
    public LLmJudge(String baseUrl, String apiKey, String modelId, int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 一次评判的结果：分数(1~5，解析失败为 -1) + 反馈 + 原始输出 */
    public record JudgeResult(int score, String feedback, String raw) {
        public boolean isValid() { return score >= 1 && score <= 5; }
    }

    /**
     * 评测一条回复。
     *
     * @param instruction 待评估的指令（可能是用户问题；忠实度场景可把「检索到的商品上下文」也放进来）
     * @param response    待评估的回复（agent 的输出）
     * @param reference   参考答案（满分 5 的示范；可为 null）
     * @param rubric      评分细则（Score 1~5 的描述）
     */
    public JudgeResult judge(String instruction, String response, String reference, String rubric) {
        String task = String.format(TASK_TEMPLATE, instruction, response,
                reference == null ? "" : reference, rubric);
        String userContent = SYSTEM_PROMPT + "\n\n" + task;   // 官方拼法：拼进同一条 user 消息
        return call(userContent);
    }

    private JudgeResult call(String userContent) {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelId);
        body.addProperty("temperature", 0);
        body.addProperty("stream", false);

        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", userContent);
        JsonArray messages = new JsonArray();
        messages.add(msg);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Judge 调用失败, status=" + resp.statusCode() + " body=" + resp.body());
            }
            JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
            String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            JudgeResult result = parse(content);
            log.debug("Judge | score={} | feedback={}", result.score(), result.feedback());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Judge 调用异常: " + e.getMessage(), e);
        }
    }

    /** 从模型输出里解析 [RESULT] n，反馈 = [RESULT] 之前的部分 */
    private JudgeResult parse(String content) {
        Matcher m = RESULT_PATTERN.matcher(content);
        int score = -1;
        int resultStart = -1;
        if (m.find()) {
            score = Integer.parseInt(m.group(1));
            resultStart = m.start();
        }
        String feedback = (resultStart >= 0 ? content.substring(0, resultStart) : content).trim();
        feedback = feedback.replaceFirst("(?i)^Feedback:\\s*", "").trim();
        return new JudgeResult(score, feedback, content);
    }
}
