package com.javaai.observability.cost;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 三级成本封顶：<b>单次请求 / 单用户日 / 全局日</b>。
 *
 * <p>没有预算封顶的 AI 系统，就是一颗定时炸弹。这里用「分」为单位存储，避免浮点误差。
 */
@Component
public class BudgetGuard {

    /** 按用户累计的当日成本（单位：分） */
    private final Map<String, AtomicLong> userDailyCost = new ConcurrentHashMap<>();
    /** 全局当日成本（单位：分） */
    private final AtomicLong globalDailyCost = new AtomicLong();

    // 示例单价（元 / 千 Token），真实项目应从配置中心读取
    private static final double PRICE_PER_1K_TOKEN = 0.002;

    private static final double BUDGET_PER_CALL = 0.5;      // 单次请求
    private static final double USER_DAILY_BUDGET = 50.0;   // 单用户日
    private static final double GLOBAL_DAILY_BUDGET = 5000.0; // 全局日

    public double estimate(int inputTokens, int outputTokens) {
        return (inputTokens + outputTokens) / 1000.0 * PRICE_PER_1K_TOKEN;
    }

    /** 调用前检查：任一级超限都直接拒绝 */
    public void check(String userId, double estimatedCost) {
        if (estimatedCost > BUDGET_PER_CALL) {
            throw new BudgetExceededException("单次请求预估成本超限：" + estimatedCost);
        }
        if (userDailyCost(userId) + estimatedCost > USER_DAILY_BUDGET) {
            throw new BudgetExceededException("用户[" + userId + "]今日额度已用完");
        }
        if (globalDailyCost() + estimatedCost > GLOBAL_DAILY_BUDGET) {
            throw new BudgetExceededException("全局额度已用完");
        }
    }

    /** 调用后记账 */
    public void commit(String userId, double cost) {
        long cents = (long) (cost * 1000);
        userDailyCost.computeIfAbsent(userId, k -> new AtomicLong()).addAndGet(cents);
        globalDailyCost.addAndGet(cents);
    }

    public double userDailyCost(String userId) {
        AtomicLong v = userDailyCost.get(userId);
        return v == null ? 0.0 : v.get() / 1000.0;
    }

    public double globalDailyCost() {
        return globalDailyCost.get() / 1000.0;
    }

    public static class BudgetExceededException extends RuntimeException {
        public BudgetExceededException(String message) {
            super(message);
        }
    }
}
