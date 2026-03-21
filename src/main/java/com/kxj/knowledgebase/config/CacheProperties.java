package com.kxj.knowledgebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "cache.semantic")
public class CacheProperties {

    /**
     * 向量相似度阈值，超过此值视为同一问题
     */
    private double similarityThreshold = 0.92;

    /**
     * 语义缓存TTL
     */
    private Duration semanticTtl = Duration.ofHours(2);

    /**
     * 精确匹配缓存TTL
     */
    private Duration exactTtl = Duration.ofHours(1);

    /**
     * 本地缓存最大大小
     */
    private int localMaxSize = 10000;

    /**
     * 本地缓存过期时间
     */
    private Duration localExpireAfterWrite = Duration.ofMinutes(10);

    /**
     * Embedding缓存TTL（较长，因为embedding不会变）
     */
    private Duration embeddingTtl = Duration.ofDays(7);

    /**
     * 是否启用语义缓存
     */
    private boolean enabled = true;

    /**
     * 每次扫描的候选缓存数量上限
     */
    private int maxCandidateScan = 100;
}
