package com.javaai.observability.cache;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语义缓存（原理演示版）。
 *
 * <p>用户的问题高度重复（"怎么退款""发票怎么开"）。把「问题 → 答案」按向量缓存，
 * 相似问题直接命中，不重复调用模型。
 *
 * <p>生产环境建议改为 <b>Redis + 向量检索</b>（Redis 8 原生支持向量），
 * 这里用内存 + 余弦相似度演示核心原理。实测客服场景命中率约 38%。
 */
@Component
public class SemanticCache {

    /** 相似度阈值：低于它视为「不同问题」 */
    private static final double SIMILARITY_THRESHOLD = 0.92;

    private final EmbeddingModel embeddingModel;
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public SemanticCache(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** 命中则返回缓存答案 */
    public Optional<String> lookup(String question) {
        float[] queryVector = embeddingModel.embed(question);
        return store.values().stream()
                .filter(e -> cosine(queryVector, e.vector()) >= SIMILARITY_THRESHOLD)
                .findFirst()
                .map(Entry::answer);
    }

    public void store(String question, String answer) {
        store.put(question, new Entry(embeddingModel.embed(question), answer));
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-9);
    }

    private record Entry(float[] vector, String answer) {
    }
}
