package com.kxj.knowledgebase.service.cache;

import com.kxj.knowledgebase.config.SynonymProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SynonymNormalizer - 同义词标准化")
class SynonymNormalizerTest {

    private SynonymNormalizer normalizer;

    @BeforeEach
    void setUp() {
        SynonymProperties properties = new SynonymProperties();
        normalizer = new SynonymNormalizer(properties);
        normalizer.init();
    }

    @Nested
    @DisplayName("normalize() - 基础标准化")
    class BasicNormalizationTests {

        @Test
        @DisplayName("转小写")
        void normalize_lowercase() {
            String result = normalizer.normalize("Spring Boot");
            assertThat(result).isEqualTo("springboot");
        }

        @Test
        @DisplayName("去除标点")
        void normalize_removePunctuation() {
            String result = normalizer.normalize("什么是Java？");
            assertThat(result).isEqualTo("什么是java");
        }

        @Test
        @DisplayName("去除空格")
        void normalize_removeWhitespace() {
            String result = normalizer.normalize("Spring   Boot");
            assertThat(result).isEqualTo("springboot");
        }

        @Test
        @DisplayName("去除语气词")
        void normalize_removeParticles() {
            String result = normalizer.normalize("什么是Java呢？");
            assertThat(result).isEqualTo("什么是java");
        }
    }

    @Nested
    @DisplayName("同义词替换 - 疑问词")
    class QuestionWordTests {

        @Test
        @DisplayName("咋 → 怎么")
        void normalize_za_to_zenme() {
            String result = normalizer.normalize("Spring Boot咋用");
            assertThat(result).isEqualTo("springboot怎么用");
        }

        @Test
        @DisplayName("啥 → 什么")
        void normalize_sha_to_shenme() {
            String result = normalizer.normalize("Redis是啥");
            assertThat(result).isEqualTo("redis什么是");
        }

        @Test
        @DisplayName("为啥 → 为什么")
        void normalize_weisha_to_weishenme() {
            String result = normalizer.normalize("为啥用Kafka");
            assertThat(result).isEqualTo("为什么用kafka");
        }

        @Test
        @DisplayName("咋样 → 怎么样")
        void normalize_zayang_to_zenmeyang() {
            String result = normalizer.normalize("性能咋样");
            assertThat(result).isEqualTo("性能怎么样");
        }
    }

    @Nested
    @DisplayName("同义词替换 - 动词短语")
    class VerbPhraseTests {

        @Test
        @DisplayName("咋用 → 怎么用")
        void normalize_zayong_to_zenmeyong() {
            String result = normalizer.normalize("Redis咋用");
            assertThat(result).isEqualTo("redis怎么用");
        }

        @Test
        @DisplayName("咋配置 → 怎么配置")
        void normalize_zapeizhi_to_zenmepeizhi() {
            String result = normalizer.normalize("咋配置缓存");
            assertThat(result).isEqualTo("怎么配置缓存");
        }

        @Test
        @DisplayName("咋连 → 怎么连接")
        void normalize_zalian_to_zenmelianjie() {
            String result = normalizer.normalize("咋连数据库");
            assertThat(result).isEqualTo("怎么连接数据库");
        }
    }

    @Nested
    @DisplayName("同义词替换 - 查询类动词")
    class QueryVerbTests {

        @Test
        @DisplayName("介绍一下 → 什么是")
        void normalize_jieshao_to_shishenme() {
            String result = normalizer.normalize("介绍一下Spring Boot");
            assertThat(result).isEqualTo("什么是springboot");
        }

        @Test
        @DisplayName("讲讲 → 什么是")
        void normalize_jiangjiang_to_shishenme() {
            String result = normalizer.normalize("讲讲Redis");
            assertThat(result).isEqualTo("什么是redis");
        }

        @Test
        @DisplayName("说说 → 什么是")
        void normalize_shuoshuo_to_shishenme() {
            String result = normalizer.normalize("说说Kafka原理");
            assertThat(result).isEqualTo("什么是kafka原理");
        }
    }

    @Nested
    @DisplayName("同义词替换 - 倒装归一化")
    class InversionTests {

        @Test
        @DisplayName("是什么 → 什么是")
        void normalize_shishenme_to_shenmeshi() {
            String result = normalizer.normalize("Spring Boot是什么");
            assertThat(result).isEqualTo("什么是springboot");
        }

        @Test
        @DisplayName("是啥 → 什么是")
        void normalize_shisha_to_shenmeshi() {
            String result = normalizer.normalize("Redis是啥");
            assertThat(result).isEqualTo("redis什么是");
        }
    }

    @Nested
    @DisplayName("同义词替换 - 技术缩写")
    class TechAbbreviationTests {

        @Test
        @DisplayName("sb → springboot")
        void normalize_sb_to_springboot() {
            String result = normalizer.normalize("sb是什么");
            assertThat(result).isEqualTo("什么是springboot");
        }

        @Test
        @DisplayName("spring boot → springboot")
        void normalize_spring_boot_to_springboot() {
            String result = normalizer.normalize("Spring Boot是什么");
            assertThat(result).isEqualTo("什么是springboot");
        }
    }

    @Nested
    @DisplayName("同义词替换 - 口语化")
    class ColloquialTests {

        @Test
        @DisplayName("给我 → (空)")
        void normalize_geiwo_removed() {
            String result = normalizer.normalize("给我讲讲Redis");
            assertThat(result).isEqualTo("什么是redis");
        }

        @Test
        @DisplayName("帮我 → (空)")
        void normalize_bangwo_removed() {
            String result = normalizer.normalize("帮我介绍一下Kafka");
            assertThat(result).isEqualTo("什么是kafka");
        }

        @Test
        @DisplayName("请 → (空)")
        void normalize_qing_removed() {
            String result = normalizer.normalize("请介绍一下Spring Boot");
            assertThat(result).isEqualTo("什么是springboot");
        }
    }

    @Nested
    @DisplayName("复杂组合场景")
    class ComplexCombinationTests {

        @Test
        @DisplayName("口语化完整问句")
        void normalize_fullSentence() {
            String result = normalizer.normalize("给我介绍一下Spring Boot咋用啊？");
            assertThat(result).isEqualTo("什么是springboot怎么用");
        }

        @Test
        @DisplayName("多种同义词混合")
        void normalize_multipleSynonyms() {
            String result = normalizer.normalize("sb是啥，咋配置呢？");
            assertThat(result).isEqualTo("springboot什么是怎么配置");
        }

        @Test
        @DisplayName("极端口语化")
        void normalize_extremeColloquial() {
            String result = normalizer.normalize("请告诉我Redis是个啥呀？咋连数据库啊？");
            assertThat(result).isEqualTo("什么是redis什么是怎么连接数据库");
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCaseTests {

        @Test
        @DisplayName("空字符串返回空")
        void normalize_emptyString() {
            String result = normalizer.normalize("");
            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("null返回空")
        void normalize_null() {
            String result = normalizer.normalize(null);
            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("只有语气词")
        void normalize_onlyParticles() {
            String result = normalizer.normalize("的呢吗吧啊");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("无匹配同义词时正常返回")
        void normalize_noSynonymMatch() {
            String result = normalizer.normalize("Java核心技术");
            assertThat(result).isEqualTo("java核心技术");
        }
    }

    @Nested
    @DisplayName("debug() - 调试信息")
    class DebugTests {

        @Test
        @DisplayName("返回各阶段处理结果")
        void debug_returnsSteps() {
            SynonymNormalizer.NormalizationDebugInfo debug = normalizer.debug("给我介绍一下Redis咋用啊？");

            assertThat(debug.original()).isEqualTo("给我介绍一下Redis咋用啊？");
            assertThat(debug.afterLowercase()).isEqualTo("给我介绍一下redis咋用啊？");
            assertThat(debug.afterRemovePunctuation()).isEqualTo("给我介绍一下redis咋用啊");
            assertThat(debug.afterRemoveParticles()).isEqualTo("给我介绍一下redis咋用");
            assertThat(debug.afterSynonymReplace()).contains("什么是").contains("怎么用");
            assertThat(debug.finalResult()).isEqualTo("什么是redis怎么用");
        }
    }

    @Nested
    @DisplayName("缓存命中率提升验证")
    class CacheHitImprovementTests {

        @Test
        @DisplayName("语义相同的不同表述归一到同一Key")
        void normalize_semanticSame_sameKey() {
            String key1 = normalizer.normalizeForCache("Spring Boot是什么");
            String key2 = normalizer.normalizeForCache("什么是Spring Boot");
            String key3 = normalizer.normalizeForCache("介绍一下Spring Boot");
            String key4 = normalizer.normalizeForCache("给我讲讲sb是个啥");

            // 所有这些应该归一到同一个标准化形式
            assertThat(key1).isEqualTo(key2);
            assertThat(key2).isEqualTo(key3);
            assertThat(key3).isEqualTo(key4);
        }

        @Test
        @DisplayName("用法类问题的不同表述")
        void normalize_usageQuestions_sameKey() {
            String key1 = normalizer.normalizeForCache("Redis怎么用");
            String key2 = normalizer.normalizeForCache("Redis咋用");
            String key3 = normalizer.normalizeForCache("咋使用Redis");

            assertThat(key1).isEqualTo(key2);
            assertThat(key2).isEqualTo(key3);
        }
    }
}
