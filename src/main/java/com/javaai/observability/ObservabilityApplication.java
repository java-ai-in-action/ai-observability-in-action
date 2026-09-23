package com.javaai.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI 可观测与 FinOps 示例入口。
 *
 * <p>配套文章：篇8《我的 AI Agent 半夜偷偷花了 3000 块，直到我装了这个「行车记录仪」》
 */
@SpringBootApplication
public class ObservabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApplication.class, args);
    }
}
