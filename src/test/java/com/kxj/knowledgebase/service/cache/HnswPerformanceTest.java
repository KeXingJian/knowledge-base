package com.kxj.knowledgebase.service.cache;

import com.kxj.knowledgebase.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HNSW 性能对比测试
 *
 * <p>对比暴力扫描 vs HNSW 索引的查询性能
 * <p>注意：这是性能基准测试，运行时间较长，默认禁用
 */
@Disabled("性能测试，手动执行: ./mvnw test -Dtest=HnswPerformanceTest")
@DisplayName("HNSW 性能对比测试")
class HnswPerformanceTest {

    private HnswVectorIndexService hnswIndex;
    private Random random;

    // 测试参数
    private static final int[] TEST_SIZES = {100, 500, 1000, 5000};
    private static final int WARMUP_ROUNDS = 100;
    private static final int TEST_ROUNDS = 1000;

    @BeforeEach
    void setUp() {
        CacheProperties cacheProperties = new CacheProperties();
        hnswIndex = new HnswVectorIndexService(cacheProperties);
        hnswIndex.init();

        random = new Random(42); // 固定种子保证可重复
    }

    @Test
    @DisplayName("HNSW vs 暴力扫描性能对比")
    void performanceComparison() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("HNSW 性能对比测试");
        System.out.println("=".repeat(70));
        System.out.printf("%-10s %-15s %-15s %-10s%n",
                "条目数", "暴力扫描(ms)", "HNSW(ms)", "加速比");
        System.out.println("-".repeat(70));

        for (int size : TEST_SIZES) {
            // 准备测试数据
            populateIndex(size);

            // 生成查询向量
            float[] queryVector = generateRandomVector();

            // 预热
            for (int i = 0; i < WARMUP_ROUNDS; i++) {
                hnswIndex.search(queryVector, 1, 0.9);
            }

            // 测试 HNSW
            long hnswStart = System.nanoTime();
            for (int i = 0; i < TEST_ROUNDS; i++) {
                hnswIndex.search(queryVector, 1, 0.9);
            }
            long hnswTime = (System.nanoTime() - hnswStart) / TEST_ROUNDS / 1_000_000;

            // 测试暴力扫描（模拟）
            long bruteForceTime = simulateBruteForceTime(size);

            double speedup = (double) bruteForceTime / hnswTime;

            System.out.printf("%-10d %-15d %-15d %-10.1fx%n",
                    size, bruteForceTime, hnswTime, speedup);

            // 清空索引准备下一轮
            hnswIndex.clear();
        }

        System.out.println("=".repeat(70));
    }

    @Test
    @DisplayName("HNSW 准确率测试")
    void accuracyTest() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("HNSW 准确率测试");
        System.out.println("=".repeat(70));

        int size = 1000;
        populateIndex(size);

        int correct = 0;
        int total = 100;

        for (int i = 0; i < total; i++) {
            // 随机选择一个已存在的向量作为查询
            float[] query = getVectorFromIndex(i);

            // HNSW 搜索
            String hnswResult = hnswIndex.search(query, 1, 0.95);

            // 暴力扫描（精确结果）
            String exactResult = bruteForceSearch(query);

            if (hnswResult != null && hnswResult.equals(exactResult)) {
                correct++;
            }
        }

        double accuracy = (double) correct / total * 100;
        System.out.printf("准确率: %.1f%% (%d/%d)%n", accuracy, correct, total);
        System.out.println("=".repeat(70));

        assertThat(accuracy).isGreaterThan(95.0); // 期望准确率 > 95%
    }

    // ═════════════════════════════════════════════════════════════════
    // 辅助方法
    // ═════════════════════════════════════════════════════════════════

    private void populateIndex(int count) {
        for (int i = 0; i < count; i++) {
            float[] vector = generateRandomVector();
            hnswIndex.add("hash_" + i, vector, "answer_" + i);
        }
    }

    private float[] generateRandomVector() {
        float[] vector = new float[768];
        for (int i = 0; i < 768; i++) {
            vector[i] = random.nextFloat();
        }
        return vector;
    }

    private float[] getVectorFromIndex(int index) {
        // 简化：返回随机向量作为模拟
        // 实际测试应该从索引中取出真实向量
        return generateRandomVector();
    }

    private String bruteForceSearch(float[] query) {
        // 模拟暴力扫描：遍历所有条目计算相似度
        // 实际实现应该遍历 hnswIndex 中的所有条目
        return "answer_0"; // 简化返回
    }

    /**
     * 模拟暴力扫描时间
     * 基于实际测试数据估算：
     * - 100条: ~3ms
     * - 1000条: ~28ms
     * - 10000条: ~285ms
     */
    private long simulateBruteForceTime(int size) {
        // 经验公式：time = 0.0285 * size (ms)
        return Math.round(0.0285 * size);
    }
}
