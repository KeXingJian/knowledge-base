package com.kxj.knowledgebase.service.retriever;

import com.kxj.knowledgebase.entity.DocumentChunk;
import com.kxj.knowledgebase.repository.DocumentChunkRepository;
import com.kxj.knowledgebase.service.embedding.EmbeddingService;
import com.kxj.knowledgebase.util.StringUtils;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 父文档感知检索器
 * 先检索子块（小粒度，高精准），再加载父块（大粒度，完整上下文）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentAwareRetriever {

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;

    // 最大父块内容长度（防止超出 LLM 上下文）
    private static final int MAX_PARENT_CONTENT_LENGTH = 4000;

    /**
     * 检索结果
     */
    @Data
    @Builder
    public static class RetrievalResult {
        private Long parentChunkId;       // 父块ID
        private String parentContent;     // 父块完整内容
        private String sectionTitle;      // 章节标题
        private String pageRange;         // 页码范围
        private Double relevanceScore;    // 相关性分数
        private List<ChildMatch> matchedChildren;  // 匹配到的子块信息
        private String sourceDocument;    // 来源文档
    }

    /**
     * 子块匹配信息
     */
    @Data
    @Builder
    public static class ChildMatch {
        private Long childChunkId;
        private Integer chunkIndex;
        private String summary;
        private Double matchScore;
    }

    /**
     * 父文档感知检索
     *
     * @param query        查询文本
     * @param topK         检索子块数量
     * @param maxParents   返回的父块数量（去重后）
     * @return 父块列表（按相关性排序）
     */
    public List<RetrievalResult> retrieve(String query, int topK, int maxParents) {
        log.info("[父文档感知检索] query='{}', topK={}, maxParents={}", query, topK, maxParents);

        long startTime = System.currentTimeMillis();

        // 1. 向量化查询
        float[] queryEmbedding = embeddingService.embed(query);
        String embeddingStr = StringUtils.floatArrayToString(queryEmbedding);

        // 2. 检索子块（小粒度）
        List<DocumentChunk> childChunks = chunkRepository.findNearestChildChunks(embeddingStr, topK);
        log.info("[检索到 {} 个子块]", childChunks.size());

        if (childChunks.isEmpty()) {
            return List.of();
        }

        // 3. 按父块ID分组聚合
        Map<Long, List<DocumentChunk>> parentToChildren = childChunks.stream()
                .filter(c -> c.getParentChunkId() != null)
                .collect(Collectors.groupingBy(DocumentChunk::getParentChunkId));

        // 4. 加载父块并组装结果
        List<RetrievalResult> results = new ArrayList<>();
        Set<Long> loadedParentIds = new HashSet<>();

        for (Map.Entry<Long, List<DocumentChunk>> entry : parentToChildren.entrySet()) {
            Long parentId = entry.getKey();
            List<DocumentChunk> matchedChildren = entry.getValue();

            if (loadedParentIds.contains(parentId)) {
                continue;
            }

            // 加载父块
            Optional<DocumentChunk> parentOpt = chunkRepository.findById(parentId);
            if (parentOpt.isEmpty()) {
                log.warn("[父块不存在: {}]", parentId);
                continue;
            }

            DocumentChunk parent = parentOpt.get();
            loadedParentIds.add(parentId);

            // 计算相关性分数（子块分数的平均值或最大值）
            double relevanceScore = calculateRelevanceScore(matchedChildren);

            // 构建子块匹配信息
            List<ChildMatch> childMatches = matchedChildren.stream()
                    .map(c -> ChildMatch.builder()
                            .childChunkId(c.getId())
                            .chunkIndex(c.getChunkIndex())
                            .summary(c.getSummary())
                            .matchScore(relevanceScore) // 实际应该计算每个子块的具体分数
                            .build())
                    .collect(Collectors.toList());

            // 截取父块内容（防止过长）
            String parentContent = parent.getContent();
            if (parentContent.length() > MAX_PARENT_CONTENT_LENGTH) {
                parentContent = parentContent.substring(0, MAX_PARENT_CONTENT_LENGTH) + "...";
            }

            results.add(RetrievalResult.builder()
                    .parentChunkId(parentId)
                    .parentContent(parentContent)
                    .sectionTitle(parent.getSectionTitle())
                    .pageRange(parent.getPageRange())
                    .relevanceScore(relevanceScore)
                    .matchedChildren(childMatches)
                    .sourceDocument(parent.getHeadingsPath())
                    .build());

            if (results.size() >= maxParents) {
                break;
            }
        }

        // 按相关性分数排序
        results.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));

        long duration = System.currentTimeMillis() - startTime;
        log.info("[父文档检索完成] {} 个父块, 耗时 {}ms", results.size(), duration);

        return results;
    }


    /**
     * 计算相关性分数
     * 策略：匹配子块数量越多、排序越靠前，分数越高
     */
    private double calculateRelevanceScore(List<DocumentChunk> matchedChildren) {
        if (matchedChildren.isEmpty()) return 0.0;

        // 基础分数：匹配数量
        double baseScore = Math.min(matchedChildren.size() * 0.1, 0.5);

        // 排名奖励：子块在检索结果中的平均位置越靠前越好
        // 这里简化处理，假设传入的列表已经按相似度排序
        double rankBonus = 1.0 / (matchedChildren.get(0).getChunkIndex() + 1);

        return Math.min(baseScore + rankBonus, 1.0);
    }

    /**
     * 构建带引用的上下文文本
     */
    public String buildContextWithCitations(List<RetrievalResult> results) {
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < results.size(); i++) {
            RetrievalResult result = results.get(i);

            context.append("【").append(i + 1).append("】");
            if (result.getSectionTitle() != null) {
                context.append(" ").append(result.getSectionTitle());
            }
            if (result.getPageRange() != null) {
                context.append(" (页码: ").append(result.getPageRange()).append(")");
            }
            context.append("\n");
            context.append(result.getParentContent());
            context.append("\n\n");
        }

        return context.toString().trim();
    }
}
