# ai-observability-in-action · AI 可观测与 FinOps

> 配套文章：篇8《我的 AI Agent 半夜偷偷花了 3000 块，直到我装了这个「行车记录仪」》

## ✨ 这个仓库演示什么

1. **可观测三大件**：Trace（链路）/ Cost（成本）/ Eval（评估）
2. **Token 指标上报**：把每次调用的 Token 用量上报成 Prometheus 指标，打上 `user` / `model` / `api` 标签
3. **语义缓存**：Redis 缓存相似问题，实测命中率约 38%
4. **三级成本封顶**：单次请求 / 单用户日 / 全局日，硬上限防失控
5. **开箱即用的看板与告警**：Grafana Dashboard JSON + Prometheus 告警规则

## 📁 结构

```
src/main/java/com/javaai/observability/
├── ObservabilityApplication.java       # 主类
├── metrics/TokenMetrics.java           # Token 指标上报（关键：tag 维度）
├── cost/BudgetGuard.java               # 三级成本封顶
├── cache/SemanticCacheConfig.java      # Redis 语义缓存
└── api/ChatController.java             # 演示入口（自动记录指标）
src/main/resources/application.yml
docs/
├── grafana-dashboard.json              # Grafana 看板（可直接导入）
├── prometheus-alerts.yml               # 告警规则
└── finops.mmd                          # 成本治理示意图
```

## 🚀 快速开始

```bash
export DASHSCOPE_API_KEY=sk-xxx
docker run -d -p 6379:6379 redis:7        # 语义缓存
mvn spring-boot:run
```

访问 `http://localhost:8080/actuator/prometheus` 就能看到 `ai_tokens_input_total` 等指标。

## 🧠 可观测三大件

| 能力 | 解决什么 | 技术选型 |
|---|---|---|
| **Trace（链路）** | 一次请求调了哪些模型、每步耗时与 Token | Spring AI + Micrometer Tracing + OTel |
| **Cost（成本）** | 每个用户 / 租户 / 接口花了多少 | 自建指标 + Prometheus + Grafana |
| **Eval（评估）** | 效果有没有悄悄退化 | Langfuse + RAGAS（见参考文献） |

## 💰 FinOps 三板斧

| 手段 | 效果 |
|---|---|
| **语义缓存** | 相似问题直接命中，实测命中率约 38% |
| **前缀缓存** | 固定 Prompt 只发一次，输入成本降 50%+ |
| **成本封顶** | 单次 / 单用户日 / 全局日三级硬上限 |

```java
// 成本封顶（BudgetGuard 核心逻辑）
if (cost > budgetPerCall()) {
    model = "qwen-turbo";                       // 超单次预算 → 降级
}
if (userDailyCost(userId) > userDailyBudget()) {
    throw new BudgetExceededException("今日额度已用完");   // 超用户日预算 → 拒绝
}
if (globalDailyCost() > globalDailyBudget()) {
    throw new BudgetExceededException("全局额度已用完");   // 超全局预算 → 熔断
}
```

## 📊 看板与告警

导入 `docs/grafana-dashboard.json`，四块核心面板：

| 面板 | 指标 | 作用 |
|---|---|---|
| 实时 Token 流速 | `rate(ai_tokens_input_total[5m])` | 一眼看异常尖峰 |
| TopN 烧钱用户 | `topk(10, sum by (user)(...))` | 找到异常账号 |
| 成本趋势 | 按天 / 按模型 | 与预算对比 |
| 异常告警 | 单用户分钟级突增 | 第一时间通知 |

告警规则见 `docs/prometheus-alerts.yml`。

## ⚠️ 踩坑清单

| # | 坑 | 后果 | 解法 |
|---|---|---|---|
| 1 | 只监控系统，不监控 Token | 账单月底炸 | Token 上报成指标 |
| 2 | Trace 全量落库 | 存储成本爆炸 | 按比例采样 + 冷热分离 |
| 3 | Prompt 明文落库 | 泄露隐私 | 脱敏后再存 |
| 4 | 没有 tag 维度 | 找不到烧钱元凶 | user / model / api 打标 |
| 5 | 语义缓存阈值太低 | 答非所问 | 阈值 ≥ 0.9 + 回归 |
| 6 | 没有预算封顶 | 定时炸弹 | 三级封顶 |
| 7 | 告警阈值迟钝 | 发现时已烧光 | 分钟级突增告警 |
| 8 | 只看总成本 | 分不清业务涨还是效率降 | 盯单位成本（每会话） |

## 📚 参考

- [Spring AI Observability](https://docs.spring.io/spring-ai/reference/observability/index.html)
- [Langfuse](https://langfuse.com/docs)
- [RAGAS](https://docs.ragas.io/)

## License

MIT

> 版本说明：本仓库基于 Spring AI 1.0 GA、Langfuse 2.x、Grafana 11 编写，具体 API 与版本号以官方仓库为准。
