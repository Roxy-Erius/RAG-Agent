package com.ragagent.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务指标埋点：Micrometer → /actuator/prometheus → Prometheus → Grafana。
 * 这些是"系统指标"之外、本项目特有的价值指标。
 */
@Service
public class MetricsService {

    private final Timer retrievalTimer;
    private final Timer llmTotalTimer;
    private final Timer llmFirstTokenTimer;
    private final Counter cartOk;
    private final Counter cartFail;

    /** 当前活跃 SSE 连接数（Gauge） */
    private final AtomicInteger activeSse = new AtomicInteger(0);

    public MetricsService(MeterRegistry registry) {
        this.retrievalTimer = Timer.builder("rag.retrieval.duration")
                .description("RAG 检索耗时").register(registry);
        this.llmTotalTimer = Timer.builder("llm.total.duration")
                .description("LLM 整轮生成耗时").register(registry);
        this.llmFirstTokenTimer = Timer.builder("llm.first_token.duration")
                .description("LLM 首 token 延迟").register(registry);
        this.cartOk = Counter.builder("cart.tool.calls").tag("result", "ok").register(registry);
        this.cartFail = Counter.builder("cart.tool.calls").tag("result", "fail").register(registry);
        registry.gauge("sse.active.connections", activeSse, AtomicInteger::get);
    }

    public void recordRetrieval(long millis) {
        retrievalTimer.record(Duration.ofMillis(millis));
    }

    public void recordLlmTotal(long millis) {
        llmTotalTimer.record(Duration.ofMillis(millis));
    }

    public void recordLlmFirstToken(long millis) {
        llmFirstTokenTimer.record(Duration.ofMillis(millis));
    }

    public void sseOpened() {
        activeSse.incrementAndGet();
    }

    public void sseClosed() {
        activeSse.decrementAndGet();
    }

    public void cartTool(boolean success) {
        (success ? cartOk : cartFail).increment();
    }
}
