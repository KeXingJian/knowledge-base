package com.kxj.knowledgebase.service.embedding;

import com.kxj.knowledgebase.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CachedEmbeddingService - 多级Embedding缓存")
class CachedEmbeddingServiceTest {

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private CacheProperties cacheProperties;
    private CachedEmbeddingService service;

    private static final float[] FAKE_EMBEDDING = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};

    @BeforeEach
    void setUp() {
        cacheProperties = new CacheProperties();
        cacheProperties.setLocalMaxSize(1000);
        cacheProperties.setLocalExpireAfterWrite(Duration.ofMinutes(10));
        cacheProperties.setEmbeddingTtl(Duration.ofDays(7));

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service = new CachedEmbeddingService(embeddingService, redisTemplate, cacheProperties);
        service.init();
    }

    @Nested
    @DisplayName("embed() - 缓存层次")
    class EmbedTests {

        @Test
        @DisplayName("第一次调用：走到Ollama，并回填L1+L2缓存")
        void embed_firstCall_callsOllamaAndCaches() {
            when(valueOps.get(startsWith("emb:"))).thenReturn(null);
            when(embeddingService.embed("Java是什么")).thenReturn(FAKE_EMBEDDING);

            float[] result = service.embed("Java是什么");

            assertThat(result).isEqualTo(FAKE_EMBEDDING);
            verify(embeddingService, times(1)).embed("Java是什么");
            // 回填L2 Redis
            verify(valueOps).set(startsWith("emb:"), anyString(), eq(Duration.ofDays(7)));
        }

        @Test
        @DisplayName("L1本地缓存命中：第二次调用不查Redis，不调用Ollama")
        void embed_l1LocalHit_noRedisNoOllama() {
            when(valueOps.get(startsWith("emb:"))).thenReturn(null);
            when(embeddingService.embed(anyString())).thenReturn(FAKE_EMBEDDING);

            // 第一次：填充本地缓存
            service.embed("Spring Boot是什么");
            // 重置所有 mock 调用记录
            clearInvocations(embeddingService, redisTemplate, valueOps);

            // 第二次：应命中 L1
            float[] result = service.embed("Spring Boot是什么");

            assertThat(result).isEqualTo(FAKE_EMBEDDING);
            verifyNoInteractions(embeddingService);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("L2 Redis命中：从Redis读取并回填L1缓存，不调用Ollama")
        void embed_l2RedisHit_noOllamaCallAndBackfillsL1() {
            String redisCachedValue = "0.100000,0.200000,0.300000,0.400000,0.500000";
            when(valueOps.get(startsWith("emb:"))).thenReturn(redisCachedValue);

            float[] result = service.embed("Redis缓存测试");

            assertThat(result).isEqualTo(FAKE_EMBEDDING);
            verifyNoInteractions(embeddingService); // 不调用 Ollama

            // 第二次调用应命中 L1
            clearInvocations(redisTemplate, valueOps);
            float[] result2 = service.embed("Redis缓存测试");
            assertThat(result2).isEqualTo(FAKE_EMBEDDING);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("内容相同但有多余空格：trim后命中同一缓存Key")
        void embed_trimmedText_sameKey() {
            when(valueOps.get(startsWith("emb:"))).thenReturn(null);
            when(embeddingService.embed("Java是什么")).thenReturn(FAKE_EMBEDDING);

            // 先存入
            service.embed("Java是什么");
            clearInvocations(embeddingService, redisTemplate, valueOps);

            // 带空格的版本，trim后应命中同一L1缓存
            float[] result = service.embed("  Java是什么  ");

            assertThat(result).isEqualTo(FAKE_EMBEDDING);
            verifyNoInteractions(embeddingService);
        }

        @Test
        @DisplayName("空字符串：立即返回空数组，不调用任何缓存或Ollama")
        void embed_emptyText_returnsEmptyArray() {
            float[] result = service.embed("");

            assertThat(result).isEmpty();
            verifyNoInteractions(embeddingService);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("null输入：立即返回空数组")
        void embed_nullText_returnsEmptyArray() {
            float[] result = service.embed(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("不同文本使用不同的缓存Key，互不干扰")
        void embed_differentTexts_differentKeys() {
            when(valueOps.get(startsWith("emb:"))).thenReturn(null);
            float[] embA = {1.0f, 0.0f};
            float[] embB = {0.0f, 1.0f};
            when(embeddingService.embed("问题A")).thenReturn(embA);
            when(embeddingService.embed("问题B")).thenReturn(embB);

            float[] resultA = service.embed("问题A");
            float[] resultB = service.embed("问题B");

            assertThat(resultA).isEqualTo(embA);
            assertThat(resultB).isEqualTo(embB);
            verify(embeddingService).embed("问题A");
            verify(embeddingService).embed("问题B");
        }
    }

    @Nested
    @DisplayName("embedBatch() - 批量Embedding")
    class EmbedBatchTests {

        @Test
        @DisplayName("批量处理多个文本，每个都独立缓存")
        void embedBatch_processesEachText() {
            when(valueOps.get(startsWith("emb:"))).thenReturn(null);
            when(embeddingService.embed(anyString())).thenReturn(FAKE_EMBEDDING);

            String[] texts = {"文本A", "文本B", "文本C"};
            float[][] results = service.embedBatch(texts);

            assertThat(results.length).isEqualTo(3);
            verify(embeddingService, times(3)).embed(anyString());
        }

        @Test
        @DisplayName("批量中有重复文本时，L1缓存避免重复调用Ollama")
        void embedBatch_deduplicatesViaCache() {
            when(valueOps.get(startsWith("emb:"))).thenReturn(null);
            when(embeddingService.embed("重复文本")).thenReturn(FAKE_EMBEDDING);

            // 先单独调用一次填充缓存
            service.embed("重复文本");
            clearInvocations(embeddingService, redisTemplate, valueOps);

            // 批量中包含已缓存的文本
            String[] texts = {"重复文本", "重复文本"};
            float[][] results = service.embedBatch(texts);

            assertThat(results.length).isEqualTo(2);
            // 两次都命中本地缓存，Ollama不被调用
            verifyNoInteractions(embeddingService);
        }
    }

    @Nested
    @DisplayName("getStats() - 缓存统计")
    class StatsTests {

        @Test
        @DisplayName("返回Caffeine缓存统计字符串")
        void getStats_returnsCaffeineStats() {
            String stats = service.getStats();

            assertThat(stats).isNotNull();
            // Caffeine统计中包含这些关键字段
            assertThat(stats).contains("hitCount", "missCount");
        }
    }
}
