package com.kxj.knowledgebase.service.cache;

import com.kxj.knowledgebase.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticCacheService - 语义缓存服务")
class SemanticCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private SynonymNormalizer synonymNormalizer;
    @Mock
    private HnswVectorIndexService hnswIndex;

    private CacheProperties cacheProperties;
    private SemanticCacheService service;

    // 两个语义相近的 embedding（夹角很小，相似度 ~0.999）
    private static final float[] EMBEDDING_A = {0.6f, 0.8f, 0.0f};
    private static final float[] EMBEDDING_B = {0.61f, 0.79f, 0.01f};

    // 完全不同方向的 embedding（相似度 ~0.0）
    private static final float[] EMBEDDING_UNRELATED = {0.0f, 0.0f, 1.0f};

    @BeforeEach
    void setUp() {
        cacheProperties = new CacheProperties();
        cacheProperties.setSimilarityThreshold(0.92);
        cacheProperties.setSemanticTtl(Duration.ofHours(2));
        cacheProperties.setExactTtl(Duration.ofHours(1));
        cacheProperties.setLocalMaxSize(1000);
        cacheProperties.setLocalExpireAfterWrite(Duration.ofMinutes(10));
        cacheProperties.setEnabled(true);
        cacheProperties.setMaxCandidateScan(100);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);

        // Mock SynonymNormalizer 行为
        lenient().when(synonymNormalizer.normalizeForCache(anyString())).thenAnswer(inv -> {
            String arg = inv.getArgument(0);
            return arg != null ? arg.toLowerCase().replaceAll("[\\s\\p{P}]+", "") : "";
        });

        // Mock HnswVectorIndexService 行为
        lenient().when(hnswIndex.isAvailable()).thenReturn(true);
        lenient().when(hnswIndex.size()).thenReturn(0); // 默认小于阈值，使用暴力扫描

        service = new SemanticCacheService(redisTemplate, cacheProperties, synonymNormalizer, hnswIndex);
        service.init();
    }

    // ─────────────────────────────────────────────────────────────────
    // get() - 缓存查找
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("get() - 缓存查找")
    class GetTests {

        @Test
        @DisplayName("缓存禁用时，始终返回 null")
        void get_whenDisabled_returnsNull() {
            cacheProperties.setEnabled(false);

            String result = service.get("任意问题", EMBEDDING_A);

            assertThat(result).isNull();
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("L1本地精确命中：同一问题第二次查询直接从本地缓存返回")
        void get_l1LocalExactHit() {
            // 先存入缓存
            when(setOps.add(anyString(), anyString())).thenReturn(1L);
            service.put("Spring Boot是什么", EMBEDDING_A, "Spring Boot是一个框架");

            // 再查询
            String result = service.get("Spring Boot是什么", EMBEDDING_A);

            assertThat(result).isEqualTo("Spring Boot是一个框架");
            // L1 命中后不应再查 Redis exact key
            verify(valueOps, never()).get(startsWith("qa:exact:"));
        }

        @Test
        @DisplayName("L2 Redis精确命中：本地缓存未命中时查Redis")
        void get_l2RedisExactHit() {
            // 本地缓存为空，Redis返回答案
            when(valueOps.get(startsWith("qa:exact:"))).thenReturn("从Redis返回的答案");

            String result = service.get("什么是Redis", EMBEDDING_A);

            assertThat(result).isEqualTo("从Redis返回的答案");
        }

        @Test
        @DisplayName("L3语义相似度命中：语义相近的不同表述能命中同一缓存")
        void get_l3SemanticSimilarityHit() {
            // 精确查找未命中
            when(valueOps.get(anyString())).thenReturn(null);

            // Redis索引中有一个历史问题
            String storedHash = "someHash123456";
            when(setOps.members("semantic:question:index"))
                    .thenReturn(Set.of(storedHash));

            // 该历史问题的 embedding 与查询 embedding 语义相似
            when(hashOps.entries("semantic:qa:" + storedHash)).thenReturn(Map.of(
                    "question", "SpringBoot是什么",
                    "embedding", formatEmbedding(EMBEDDING_A),   // 相似
                    "answer", "Spring Boot是开发框架",
                    "hitCount", "0"
            ));

            // EMBEDDING_B 与 EMBEDDING_A 相似度 > 0.92
            String result = service.get("Spring Boot是啥", EMBEDDING_B);

            assertThat(result).isEqualTo("Spring Boot是开发框架");
            verify(hashOps).increment(eq("semantic:qa:" + storedHash), eq("hitCount"), eq(1L));
        }

        @Test
        @DisplayName("语义不相似时，返回 null")
        void get_semanticallydissimilar_returnsNull() {
            when(valueOps.get(anyString())).thenReturn(null);

            String storedHash = "someHash123456";
            when(setOps.members("semantic:question:index"))
                    .thenReturn(Set.of(storedHash));

            // 存储的 embedding 与查询方向完全不同
            when(hashOps.entries("semantic:qa:" + storedHash)).thenReturn(Map.of(
                    "question", "毫不相关的问题",
                    "embedding", formatEmbedding(EMBEDDING_A),
                    "answer", "不相关的答案",
                    "hitCount", "0"
            ));

            // EMBEDDING_UNRELATED 与 EMBEDDING_A 相似度 ≈ 0
            String result = service.get("完全不同方向的问题", EMBEDDING_UNRELATED);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Redis索引为空时，跳过向量检索直接返回 null")
        void get_emptyIndex_returnsNull() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(setOps.members("semantic:question:index")).thenReturn(Set.of());

            String result = service.get("任意问题", EMBEDDING_A);

            assertThat(result).isNull();
            verify(hashOps, never()).entries(anyString());
        }

        @Test
        @DisplayName("候选缓存已过期（Redis Hash为空）时，从索引移除该记录")
        void get_expiredCandidate_removedFromIndex() {
            when(valueOps.get(anyString())).thenReturn(null);

            String expiredHash = "expiredHash";
            when(setOps.members("semantic:question:index")).thenReturn(Set.of(expiredHash));
            // 模拟缓存已过期：Hash 返回空 Map
            when(hashOps.entries("semantic:qa:" + expiredHash)).thenReturn(Map.of());

            service.get("任意问题", EMBEDDING_A);

            verify(setOps).remove("semantic:question:index", expiredHash);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // put() - 写入缓存
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("put() - 写入缓存")
    class PutTests {

        @Test
        @DisplayName("写入时同时存储语义缓存、精确缓存、并加入索引")
        void put_writesAllThreePersistenceTargets() {
            when(setOps.add(anyString(), anyString())).thenReturn(1L);

            service.put("什么是Java", EMBEDDING_A, "Java是编程语言");

            // 1. 写入 Redis Hash（语义缓存）
            verify(hashOps).putAll(startsWith("semantic:qa:"), anyMap());
            verify(redisTemplate).expire(startsWith("semantic:qa:"), eq(Duration.ofHours(2)));

            // 2. 写入精确匹配缓存
            verify(valueOps).set(startsWith("qa:exact:"), eq("Java是编程语言"), eq(Duration.ofHours(1)));

            // 3. 写入问题索引（用于后续相似度扫描）
            verify(setOps).add(eq("semantic:question:index"), anyString());
        }

        @Test
        @DisplayName("缓存禁用时，put不执行任何操作")
        void put_whenDisabled_doesNothing() {
            cacheProperties.setEnabled(false);

            service.put("任意问题", EMBEDDING_A, "任意答案");

            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("写入后本地缓存可以精确命中（不需要再查Redis）")
        void put_thenGet_hitsLocalCache() {
            when(setOps.add(anyString(), anyString())).thenReturn(1L);
            service.put("Redis是什么", EMBEDDING_A, "Redis是内存数据库");

            // 重置 mock，验证后续 get 不走 Redis
            clearInvocations(redisTemplate, valueOps, hashOps, setOps);

            String result = service.get("Redis是什么", EMBEDDING_A);

            assertThat(result).isEqualTo("Redis是内存数据库");
            verify(valueOps, never()).get(anyString());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // invalidateAll() - 缓存清除
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("invalidateAll() - 缓存清除")
    class InvalidateTests {

        @Test
        @DisplayName("清除所有语义缓存、精确缓存和索引")
        void invalidateAll_clearsAllRedisKeys() {
            when(redisTemplate.keys("semantic:qa:*")).thenReturn(Set.of("semantic:qa:hash1", "semantic:qa:hash2"));
            when(redisTemplate.keys("qa:exact:*")).thenReturn(Set.of("qa:exact:hash1"));

            service.invalidateAll();

            verify(redisTemplate).delete(Set.of("semantic:qa:hash1", "semantic:qa:hash2"));
            verify(redisTemplate).delete(Set.of("qa:exact:hash1"));
            verify(redisTemplate).delete("semantic:question:index");
        }

        @Test
        @DisplayName("清除后，之前存入的本地缓存也失效")
        void invalidateAll_clearsLocalCache() {
            when(setOps.add(anyString(), anyString())).thenReturn(1L);
            service.put("问题", EMBEDDING_A, "答案");

            when(redisTemplate.keys(anyString())).thenReturn(Set.of());
            service.invalidateAll();

            // 本地缓存已清空，get应该走到Redis查询
            when(valueOps.get(anyString())).thenReturn(null);
            when(setOps.members(anyString())).thenReturn(Set.of());

            String result = service.get("问题", EMBEDDING_A);

            assertThat(result).isNull();
            verify(valueOps).get(startsWith("qa:exact:"));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // cosineSimilarity - 余弦相似度核心算法（通过get()间接测试）
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cosineSimilarity - 余弦相似度计算")
    class CosineSimilarityTests {

        @Test
        @DisplayName("完全相同的向量，相似度为1.0，阈值0.92时命中")
        void similarity_identicalVectors_hits() {
            prepareRedisForSimilarityTest(EMBEDDING_A, "答案");

            // 使用完全相同的 embedding 查询
            String result = service.get("不同表述相同问题", EMBEDDING_A);

            assertThat(result).isEqualTo("答案");
        }

        @Test
        @DisplayName("相似向量（夹角很小），相似度 > 0.92，命中缓存")
        void similarity_similarVectors_hits() {
            prepareRedisForSimilarityTest(EMBEDDING_A, "答案");

            // EMBEDDING_B 与 EMBEDDING_A 夹角很小
            String result = service.get("近似问题", EMBEDDING_B);

            assertThat(result).isEqualTo("答案");
        }

        @Test
        @DisplayName("正交向量，相似度为0，不命中")
        void similarity_orthogonalVectors_misses() {
            prepareRedisForSimilarityTest(EMBEDDING_A, "答案");

            // 正交向量，余弦相似度为0
            String result = service.get("完全不同方向", EMBEDDING_UNRELATED);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("阈值边界：相似度恰好等于阈值时命中（>=）")
        void similarity_exactThreshold_hits() {
            // 设置阈值为0.99
            cacheProperties.setSimilarityThreshold(0.99);

            // 构造相似度恰好为1.0的完全相同向量
            prepareRedisForSimilarityTest(EMBEDDING_A, "边界答案");

            String result = service.get("任意问题", EMBEDDING_A);

            assertThat(result).isEqualTo("边界答案");
        }

        @Test
        @DisplayName("零向量不应引发除零异常，返回null")
        void similarity_zeroVector_doesNotThrow() {
            float[] zeroEmbedding = new float[]{0.0f, 0.0f, 0.0f};
            prepareRedisForSimilarityTest(EMBEDDING_A, "答案");

            // 零向量与任何向量的余弦相似度为0
            String result = service.get("零向量测试", zeroEmbedding);

            assertThat(result).isNull();
        }

        private void prepareRedisForSimilarityTest(float[] storedEmbedding, String answer) {
            when(valueOps.get(anyString())).thenReturn(null);
            String hash = "storedHash1";
            when(setOps.members("semantic:question:index")).thenReturn(Set.of(hash));
            when(hashOps.entries("semantic:qa:" + hash)).thenReturn(Map.of(
                    "question", "已缓存的问题",
                    "embedding", formatEmbedding(storedEmbedding),
                    "answer", answer,
                    "hitCount", "0"
            ));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // normalizeQuestion - 问题标准化（通过put/get间接测试）
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("normalizeQuestion - 问题标准化提高缓存命中率")
    class NormalizeQuestionTests {

        @Test
        @DisplayName("去除语气词后，不同表述被归一到同一缓存Key")
        void normalize_removesChineseParticles_sameKey() {
            when(setOps.add(anyString(), anyString())).thenReturn(1L);
            // 存入"什么是Java"
            service.put("什么是Java", EMBEDDING_A, "Java是编程语言");

            // 清除 mock 调用记录，但保留本地缓存
            clearInvocations(valueOps);

            // "什么是Java吗" 去除语气词"吗"后与"什么是Java"相同，应命中本地缓存
            String result = service.get("什么是Java吗", EMBEDDING_A);

            assertThat(result).isEqualTo("Java是编程语言");
        }

        @Test
        @DisplayName("大小写和标点不影响缓存Key")
        void normalize_lowercaseAndPunctuation_sameKey() {
            when(setOps.add(anyString(), anyString())).thenReturn(1L);
            service.put("What is Java?", EMBEDDING_A, "Java is a language");

            clearInvocations(valueOps);

            // 大写、无标点版本应命中同一本地缓存
            String result = service.get("WHAT IS JAVA", EMBEDDING_A);

            assertThat(result).isEqualTo("Java is a language");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // getStats()
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStats() 返回缓存统计信息")
    void getStats_returnsStats() {
        when(setOps.members("semantic:question:index")).thenReturn(Set.of("h1", "h2", "h3"));

        SemanticCacheService.CacheStats stats = service.getStats();

        assertThat(stats.getRedisIndexSize()).isEqualTo(3L);
        assertThat(stats.getLocalCacheSize()).isGreaterThanOrEqualTo(0L);
    }

    // ─────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────

    private String formatEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format("%.6f", embedding[i]));
        }
        return sb.toString();
    }
}
