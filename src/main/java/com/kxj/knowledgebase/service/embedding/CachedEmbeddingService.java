package com.kxj.knowledgebase.service.embedding;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kxj.knowledgebase.config.CacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

/**
 * 带多级缓存的 Embedding 服务
 * L1: Caffeine 本地缓存（进程内，亚毫秒级）
 * L2: Redis 分布式缓存（跨实例共享）
 * L3: 实际调用 Ollama
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CachedEmbeddingService {

    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, String> redisTemplate;
    private final CacheProperties cacheProperties;

    private Cache<String, float[]> localCache;

    @PostConstruct
    public void init() {
        localCache = Caffeine.newBuilder()
                .maximumSize(cacheProperties.getLocalMaxSize())
                .expireAfterWrite(cacheProperties.getLocalExpireAfterWrite())
                .recordStats()
                .build();
    }

    /**
     * 获取文本的 embedding，带多级缓存
     */
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[0];
        }

        String contentHash = sha256(text.trim());
        String redisKey = "emb:" + contentHash;

        // L1: 本地缓存
        float[] cached = localCache.getIfPresent(contentHash);
        if (cached != null) {
            log.debug("[Embedding L1命中] contentHash={}", contentHash.substring(0, 8));
            return cached;
        }

        // L2: Redis缓存
        String redisValue = redisTemplate.opsForValue().get(redisKey);
        if (redisValue != null) {
            float[] embedding = parseEmbedding(redisValue);
            localCache.put(contentHash, embedding); // 回填L1
            log.debug("[Embedding L2命中] contentHash={}", contentHash.substring(0, 8));
            return embedding;
        }

        // L3: 调用实际服务
        log.debug("[Embedding 未命中] 调用Ollama, contentHash={}", contentHash.substring(0, 8));
        long start = System.currentTimeMillis();
        float[] embedding = embeddingService.embed(text);
        long cost = System.currentTimeMillis() - start;

        // 回填缓存
        localCache.put(contentHash, embedding);
        redisTemplate.opsForValue().set(redisKey, formatEmbedding(embedding),
                cacheProperties.getEmbeddingTtl());

        log.info("[Embedding 计算完成] 耗时{}ms, contentHash={}", cost, contentHash.substring(0, 8));
        return embedding;
    }

    /**
     * 批量获取 embedding，利用并行提高效率
     */
    public float[][] embedBatch(String[] texts) {
        return Arrays.stream(texts)
                .parallel()
                .map(this::embed)
                .toArray(float[][]::new);
    }

    /**
     * 获取缓存统计
     */
    public String getStats() {
        return localCache.stats().toString();
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // fallback to simple hash
            return String.valueOf(text.hashCode());
        }
    }

    private String formatEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
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
}
