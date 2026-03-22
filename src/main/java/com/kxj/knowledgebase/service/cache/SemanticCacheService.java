package com.kxj.knowledgebase.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kxj.knowledgebase.config.CacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于向量相似度的智能缓存服务
 *
 * 核心思想：语义相同但表述不同的问题，其 embedding 向量在高维空间中距离很近
 * 通过计算余弦相似度，找到语义相似的历史问题，直接返回缓存答案
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final CacheProperties cacheProperties;
    private final SynonymNormalizer synonymNormalizer;
    private final HnswVectorIndexService hnswIndex;  // HNSW 向量索引（可选优化）

    // 本地缓存：问题 -> 答案（用于精确匹配加速）
    private Cache<String, CacheEntry> localSemanticCache;

    // HNSW 启用阈值（缓存条目超过此值时启用 HNSW）
    private static final int HNSW_ENABLE_THRESHOLD = 500;

    // 缓存 Key 前缀
    private static final String SEMANTIC_CACHE_PREFIX = "semantic:qa:";
    private static final String EXACT_CACHE_PREFIX = "qa:exact:";
    private static final String QUESTION_INDEX_KEY = "semantic:question:index";

    @PostConstruct
    public void init() {
        localSemanticCache = Caffeine.newBuilder()
                .maximumSize(cacheProperties.getLocalMaxSize())
                .expireAfterWrite(cacheProperties.getLocalExpireAfterWrite())
                .recordStats()
                .build();
    }

    /**
     * 智能缓存查找
     *
     * @param question  用户问题
     * @param embedding 问题的 embedding 向量
     * @return 缓存命中返回答案，未命中返回 null
     */
    public String get(String question, float[] embedding) {
        if (!cacheProperties.isEnabled()) {
            return null;
        }

        String normalizedQuestion = normalizeQuestion(question);
        String questionHash = sha256(normalizedQuestion);

        // L1: 本地精确匹配（最快，O(1)）
        CacheEntry localEntry = localSemanticCache.getIfPresent(questionHash);
        if (localEntry != null) {
            log.info("[语义缓存 L1命中-精确] 问题: {}", truncate(question, 30));
            return localEntry.getAnswer();
        }

        // L2: Redis 精确匹配
        String exactKey = EXACT_CACHE_PREFIX + questionHash;
        String exactAnswer = redisTemplate.opsForValue().get(exactKey);
        if (exactAnswer != null) {
            // 回填本地缓存
            localSemanticCache.put(questionHash, CacheEntry.builder()
                    .question(normalizedQuestion)
                    .answer(exactAnswer)
                    .build());
            log.info("[语义缓存 L2命中-精确] 问题: {}", truncate(question, 30));
            return exactAnswer;
        }

        // L3: 向量相似度匹配
        // 优先使用 HNSW 索引（如果可用且缓存条目足够多）
        String semanticAnswer = findBySimilarityWithHnsw(embedding, questionHash);
        if (semanticAnswer != null) {
            log.info("[语义缓存 L3命中-相似] 问题: {}", truncate(question, 30));
            return semanticAnswer;
        }

        log.debug("[语义缓存 未命中] 问题: {}", truncate(question, 30));
        return null;
    }

    /**
     * 存入缓存
     *
     * @param question  原始问题
     * @param embedding 问题 embedding
     * @param answer    答案
     */
    public void put(String question, float[] embedding, String answer) {
        if (!cacheProperties.isEnabled()) {
            return;
        }

        String normalizedQuestion = normalizeQuestion(question);
        String questionHash = sha256(normalizedQuestion);
        String embeddingStr = formatEmbedding(embedding);

        // 存储语义缓存（带 embedding）
        String semanticKey = SEMANTIC_CACHE_PREFIX + questionHash;
        Map<String, String> semanticData = Map.of(
                "question", normalizedQuestion,
                "embedding", embeddingStr,
                "answer", answer,
                "timestamp", String.valueOf(System.currentTimeMillis()),
                "hitCount", "0"
        );
        redisTemplate.opsForHash().putAll(semanticKey, semanticData);
        redisTemplate.expire(semanticKey, cacheProperties.getSemanticTtl());

        // 存储精确匹配缓存（仅答案，用于快速查找）
        String exactKey = EXACT_CACHE_PREFIX + questionHash;
        redisTemplate.opsForValue().set(exactKey, answer, cacheProperties.getExactTtl());

        // 添加到问题索引（用于后续相似度扫描）
        redisTemplate.opsForSet().add(QUESTION_INDEX_KEY, questionHash);

        // 添加到 HNSW 索引（如果启用）
        hnswIndex.add(questionHash, embedding, answer);

        // 回填本地缓存
        localSemanticCache.put(questionHash, CacheEntry.builder()
                .question(normalizedQuestion)
                .embedding(embedding)
                .answer(answer)
                .build());

        log.info("[语义缓存 已存储] 问题: {}", truncate(question, 30));
    }

    /**
     * 使用 HNSW 索引或暴力扫描查找相似向量
     *
     * 策略：
     * 1. 如果缓存条目 > HNSW_ENABLE_THRESHOLD 且 HNSW 可用，使用 HNSW 搜索
     * 2. 否则使用暴力扫描（兼容小数据量）
     */
    private String findBySimilarityWithHnsw(float[] queryEmbedding, String excludeHash) {
        // 判断是否启用 HNSW
        if (shouldUseHnsw()) {
            // 使用 HNSW 快速搜索
            String result = hnswIndex.search(queryEmbedding, 1, cacheProperties.getSimilarityThreshold());
            if (result != null) {
                log.debug("[HNSW 索引命中]");
                return result;
            }
        }

        // 回退：暴力扫描（HNSW 未启用或不可用时）
        return findBySimilarityBruteForce(queryEmbedding, excludeHash);
    }

    /**
     * 判断是否应使用 HNSW 索引
     */
    private boolean shouldUseHnsw() {
        if (!hnswIndex.isAvailable()) {
            return false;
        }
        // 当 HNSW 索引大小超过阈值时使用
        return hnswIndex.size() >= HNSW_ENABLE_THRESHOLD;
    }

    /**
     * 基于向量相似度暴力扫描查找缓存（O(N) 复杂度）
     *
     * 策略：
     * 1. 获取最近缓存的 N 个问题
     * 2. 计算当前问题与候选问题的 embedding 余弦相似度
     * 3. 超过阈值则视为命中
     */
    private String findBySimilarityBruteForce(float[] queryEmbedding, String excludeHash) {
        // 获取候选问题（从 Redis Set 中读取）
        Set<String> candidateHashes = redisTemplate.opsForSet().members(QUESTION_INDEX_KEY);
        if (candidateHashes == null || candidateHashes.isEmpty()) {
            return null;
        }

        // 限制扫描数量，避免性能问题
        List<String> candidates = candidateHashes.stream()
                .filter(h -> !h.equals(excludeHash)) // 排除自己
                .limit(cacheProperties.getMaxCandidateScan())
                .toList();

        double bestSimilarity = 0;
        String bestAnswer = null;
        String bestHash = null;

        for (String hash : candidates) {
            String semanticKey = SEMANTIC_CACHE_PREFIX + hash;
            Map<Object, Object> data = redisTemplate.opsForHash().entries(semanticKey);

            if (data.isEmpty()) {
                // 缓存已过期，从索引中移除
                redisTemplate.opsForSet().remove(QUESTION_INDEX_KEY, hash);
                continue;
            }

            String storedEmbedding = (String) data.get("embedding");
            if (storedEmbedding == null) continue;

            float[] candidateEmbedding = parseEmbedding(storedEmbedding);
            double similarity = cosineSimilarity(queryEmbedding, candidateEmbedding);

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestAnswer = (String) data.get("answer");
                bestHash = hash;
            }
        }

        // 判断是否超过阈值
        if (bestSimilarity >= cacheProperties.getSimilarityThreshold()) {
            // 更新命中次数统计
            if (bestHash != null) {
                String semanticKey = SEMANTIC_CACHE_PREFIX + bestHash;
                redisTemplate.opsForHash().increment(semanticKey, "hitCount", 1);
            }
            log.info("[语义相似度匹配] 相似度={}", String.format("%.4f", bestSimilarity));
            return bestAnswer;
        }

        return null;
    }

    /**
     * 清除所有语义缓存（文档更新时调用）
     */
    public void invalidateAll() {
        Set<String> keys = redisTemplate.keys(SEMANTIC_CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        Set<String> exactKeys = redisTemplate.keys(EXACT_CACHE_PREFIX + "*");
        if (exactKeys != null && !exactKeys.isEmpty()) {
            redisTemplate.delete(exactKeys);
        }

        redisTemplate.delete(QUESTION_INDEX_KEY);
        localSemanticCache.invalidateAll();

        // 同时清空 HNSW 索引
        hnswIndex.clear();

        log.info("[语义缓存 已清空]");
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        Set<String> index = redisTemplate.opsForSet().members(QUESTION_INDEX_KEY);

        return CacheStats.builder()
                .localCacheSize(localSemanticCache.estimatedSize())
                .localHitCount(localSemanticCache.stats().hitCount())
                .redisIndexSize(index != null ? index.size() : 0)
                .hnswEnabled(hnswIndex.isAvailable())
                .hnswIndexSize(hnswIndex.size())
                .hnswActive(hnswIndex.isAvailable() && hnswIndex.size() >= HNSW_ENABLE_THRESHOLD)
                .build();
    }

    /**
     * 计算余弦相似度
     * 范围 [-1, 1]，embedding 通常为非负值，实际范围 [0, 1]
     * 值越接近 1 表示越相似
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }

        double dot = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 问题标准化（使用同义词归一化提高缓存命中率）
     */
    private String normalizeQuestion(String question) {
        return synonymNormalizer.normalizeForCache(question);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    private String formatEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format("%.6f", embedding[i]));
        }
        return sb.toString();
    }

    private float[] parseEmbedding(String str) {
        String[] parts = str.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }

    private String truncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    @Data
    @Builder
    public static class CacheEntry {
        private String question;
        private float[] embedding;
        private String answer;
    }

    @Data
    @Builder
    public static class CacheStats {
        private long localCacheSize;
        private long localHitCount;
        private long redisIndexSize;
        private boolean hnswEnabled;
        private int hnswIndexSize;
        private boolean hnswActive;
    }
}
