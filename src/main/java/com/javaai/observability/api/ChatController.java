package com.javaai.observability.api;

import com.javaai.observability.cost.BudgetGuard;
import com.javaai.observability.metrics.TokenMetrics;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示入口：POST /api/chat
 *
 * <p>一次请求的完整生命周期：<b>成本预检 → 调用 → 指标上报 → 记账</b>。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatModel chatModel;
    private final TokenMetrics metrics;
    private final BudgetGuard budgetGuard;

    public ChatController(ChatModel chatModel, TokenMetrics metrics, BudgetGuard budgetGuard) {
        this.chatModel = chatModel;
        this.metrics = metrics;
        this.budgetGuard = budgetGuard;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest req) {
        // 1. 调用前：成本预检（三级封顶）
        double estimated = budgetGuard.estimate(req.message().length() / 2, 500);
        budgetGuard.check(req.userId(), estimated);

        // 2. 调用模型
        ChatResponse resp = chatModel.call(new Prompt(req.message()));

        // 3. 指标上报：user / model / api 三个维度
        Usage usage = resp.getMetadata().getUsage();
        metrics.record(req.userId(), modelOf(resp), "/api/chat", usage);

        // 4. 记账
        double actual = budgetGuard.estimate(
                usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
        budgetGuard.commit(req.userId(), actual);

        return Map.of(
                "answer", resp.getResult().getOutput().getText(),
                "costYuan", actual,
                "userDailyCostYuan", budgetGuard.userDailyCost(req.userId()));
    }

    private String modelOf(ChatResponse resp) {
        return resp.getMetadata() != null && resp.getMetadata().getModel() != null
                ? resp.getMetadata().getModel()
                : "unknown";
    }

    public record ChatRequest(String userId, String message) {
    }
}
