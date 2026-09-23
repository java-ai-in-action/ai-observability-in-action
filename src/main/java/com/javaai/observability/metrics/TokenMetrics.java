package com.javaai.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

/**
 * Token 指标上报 —— 可观测的核心：把「钱」变成可查询的指标。
 *
 * <p>关键不在记录本身，而在 <b>tag 维度</b>：{@code user} / {@code model} / {@code api}。
 * 有了维度，才能回答「哪个用户最烧钱」「哪个模型性价比最差」。
 */
@Component
public class TokenMetrics {

    private final MeterRegistry registry;

    public TokenMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 记录一次模型调用的 Token 用量 */
    public void record(String userId, String model, String api, Usage usage) {
        if (usage == null) {
            return;
        }
        int input = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int output = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();

        registry.counter("ai.tokens.input",
                        "user", safe(userId), "model", safe(model), "api", safe(api))
                .increment(input);
        registry.counter("ai.tokens.output",
                        "user", safe(userId), "model", safe(model), "api", safe(api))
                .increment(output);
    }

    private String safe(String v) {
        return v == null || v.isBlank() ? "unknown" : v;
    }
}
