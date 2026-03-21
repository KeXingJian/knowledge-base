package com.kxj.knowledgebase.service.cache;

import com.kxj.knowledgebase.config.SynonymProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 同义词标准化处理器
 * <p>
 * 将用户问题的口语化表达转换为标准形式，提高缓存命中率。
 * 例如：
 * - "Spring Boot咋用" → "springboot怎么用"
 * - "给我介绍一下Redis" → "什么是redis"
 * - "sb咋配置" → "springboot怎么配置"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SynonymNormalizer {

    private final SynonymProperties synonymProperties;

    // 预编译正则，提高性能
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\s\\p{P}]+");
    private static final Pattern PARTICLE_PATTERN = Pattern.compile("(的|了|吗|呢|吧|啊|呀|哇|哦|哈|咧|呗)");

    // 按长度降序排列的同义词键列表（避免短词覆盖长词）
    private List<String> sortedKeys;

    @PostConstruct
    public void init() {
        Map<String, String> mappings = synonymProperties.getEffectiveMappings();
        // 按长度降序排序，确保 "咋用" 先于 "咋" 被替换
        this.sortedKeys = mappings.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        log.info("[同义词标准化器初始化完成] 共加载 {} 条同义词映射", mappings.size());
    }

    /**
     * 标准化问题文本
     * <p>
     * 处理流程：
     * 1. 转小写
     * 2. 去除空格和标点
     * 3. 去除中文语气词
     * 4. 同义词替换（按长度降序）
     * 5. 清理空字符
     *
     * @param question 原始用户问题
     * @return 标准化后的文本
     */
    public String normalize(String question) {
        if (question == null || question.trim().isEmpty()) {
            return "";
        }

        String normalized = question.toLowerCase().trim();

        // 1. 去除空格和标点
        normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll("");

        // 2. 去除中文语气词
        normalized = PARTICLE_PATTERN.matcher(normalized).replaceAll("");

        // 3. 同义词替换
        Map<String, String> mappings = synonymProperties.getEffectiveMappings();
        for (String key : sortedKeys) {
            if (normalized.contains(key)) {
                String value = mappings.get(key);
                // 如果替换值为空字符串，直接删除该词
                if (value.isEmpty()) {
                    normalized = normalized.replace(key, "");
                } else {
                    normalized = normalized.replace(key, value);
                }
            }
        }

        // 4. 最终清理（同义词替换可能引入空格或重复）
        normalized = normalized.trim();

        return normalized;
    }

    /**
     * 标准化并生成缓存Key
     *
     * @param question 原始问题
     * @return 标准化后的问题（用于缓存Key）
     */
    public String normalizeForCache(String question) {
        return normalize(question);
    }

    /**
     * 获取标准化过程的调试信息
     *
     * @param question 原始问题
     * @return 标准化各阶段信息
     */
    public NormalizationDebugInfo debug(String question) {
        String step1 = question.toLowerCase().trim();
        String step2 = WHITESPACE_PATTERN.matcher(step1).replaceAll("");
        String step3 = PARTICLE_PATTERN.matcher(step2).replaceAll("");
        String step4 = applySynonyms(step3);

        return NormalizationDebugInfo.builder()
                .original(question)
                .afterLowercase(step1)
                .afterRemovePunctuation(step2)
                .afterRemoveParticles(step3)
                .afterSynonymReplace(step4)
                .finalResult(step4.trim())
                .build();
    }

    private String applySynonyms(String text) {
        String result = text;
        Map<String, String> mappings = synonymProperties.getEffectiveMappings();
        for (String key : sortedKeys) {
            if (result.contains(key)) {
                String value = mappings.get(key);
                result = result.replace(key, value);
            }
        }
        return result;
    }

    /**
     * 标准化调试信息
     */
    public record NormalizationDebugInfo(
            String original,
            String afterLowercase,
            String afterRemovePunctuation,
            String afterRemoveParticles,
            String afterSynonymReplace,
            String finalResult
    ) {
        public static NormalizationDebugInfoBuilder builder() {
            return new NormalizationDebugInfoBuilder();
        }

        public static class NormalizationDebugInfoBuilder {
            private String original;
            private String afterLowercase;
            private String afterRemovePunctuation;
            private String afterRemoveParticles;
            private String afterSynonymReplace;
            private String finalResult;

            public NormalizationDebugInfoBuilder original(String original) {
                this.original = original;
                return this;
            }

            public NormalizationDebugInfoBuilder afterLowercase(String val) {
                this.afterLowercase = val;
                return this;
            }

            public NormalizationDebugInfoBuilder afterRemovePunctuation(String val) {
                this.afterRemovePunctuation = val;
                return this;
            }

            public NormalizationDebugInfoBuilder afterRemoveParticles(String val) {
                this.afterRemoveParticles = val;
                return this;
            }

            public NormalizationDebugInfoBuilder afterSynonymReplace(String val) {
                this.afterSynonymReplace = val;
                return this;
            }

            public NormalizationDebugInfoBuilder finalResult(String val) {
                this.finalResult = val;
                return this;
            }

            public NormalizationDebugInfo build() {
                return new NormalizationDebugInfo(
                        original, afterLowercase, afterRemovePunctuation,
                        afterRemoveParticles, afterSynonymReplace, finalResult
                );
            }
        }
    }
}
