package com.kxj.knowledgebase.service.retriever;

import com.kxj.knowledgebase.config.RetrievalProperties;
import com.kxj.knowledgebase.dto.SearchResult;
import com.kxj.knowledgebase.entity.DocumentChunk;
import com.kxj.knowledgebase.repository.DocumentChunkRepository;
import com.kxj.knowledgebase.repository.FullTextSearchResult;
import com.kxj.knowledgebase.util.StringUtils;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 父文档感知的混合检索器
 * 结合向量检索 + 全文检索，然后加载父块作为上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentAwareHybridRetriever {

    private final DocumentChunkRepository chunkRepository;
    private final RetrievalProperties retrievalProperties;

    // 最大父块内容长度
    private static final int MAX_PARENT_CONTENT_LENGTH = 4000;

    @Data
    @Builder
    public static class RetrievalResult {
        private Long parentChunkId;
        private String parentContent;
        private String sectionTitle;
        private String pageRange;
        private Double relevanceScore;
        private List<ChildMatch> matchedChildren;
        private String sourceDocument;
        private String sourceType; // "vector", "fulltext", "hybrid"
    }

    @Data
    @Builder
    public static class ChildMatch {
        private Long childChunkId;
        private Integer chunkIndex;
        private String content;      // 子块完整内容
        private String summary;
        private Double matchScore;
        private String matchType; // "vector", "fulltext", "hybrid"
    }

    /**
     * 父文档感知的混合检索
     *
     * @param query          查询文本
     * @param queryEmbedding 查询向量
     * @param childTopK      检索子块数量
     * @param maxParents     返回的父块数量
     * @return 父块列表
     */
    public List<RetrievalResult> retrieve(String query, float[] queryEmbedding, int childTopK, int maxParents) {
        log.info("[父文档混合检索] query='{}', childTopK={}, maxParents={}", query, childTopK, maxParents);

        long startTime = System.currentTimeMillis();

        // 1. 混合检索子块（向量 + 全文）
        List<SearchResult> hybridResults = performHybridSearch(query, queryEmbedding, childTopK);
        log.info("[混合检索到 {} 个子块]", hybridResults.size());

        if (hybridResults.isEmpty()) {
            return List.of();
        }

        // 2. 按父块分组并组装结果
        List<RetrievalResult> results = assembleParentResults(hybridResults, maxParents);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[父文档混合检索完成] {} 个父块, 耗时 {}ms", results.size(), duration);

        return results;
    }

    /**
     * 执行混合检索（只检索子块）
     */
    private List<SearchResult> performHybridSearch(String query, float[] queryEmbedding, int topK) {
        String embeddingStr = StringUtils.floatArrayToString(queryEmbedding);

        // 向量检索 - 只查子块
        List<DocumentChunk> vectorChunks = chunkRepository.findNearestChildChunks(embeddingStr, topK);
        log.info("[向量检索] 返回 {} 个子块", vectorChunks.size());
        vectorChunks.forEach(c -> log.debug("  向量 chunk#{} docId={} content={}...",
                c.getChunkIndex(), c.getDocumentId(),
                c.getContent().substring(0, Math.min(50, c.getContent().length()))));

        Map<Long, SearchResult> vectorResults = vectorChunks.stream()
                .collect(Collectors.toMap(
                        DocumentChunk::getId,
                        chunk -> new SearchResult(chunk, 1.0, "vector")
                ));

        // 全文检索 - 只查子块
        List<FullTextSearchResult> fullTextResults = chunkRepository.fullTextSearch(query, topK);
        log.info("[全文检索] 返回 {} 个子块", fullTextResults.size());
        fullTextResults.forEach(r -> log.debug("  全文 chunk_id={} rank={}",
                r.getChunk_id(), r.getRank()));

        Map<Long, SearchResult> textResults = fullTextResults.stream()
                .collect(Collectors.toMap(
                        FullTextSearchResult::getChunk_id,
                        result -> new SearchResult(result.toDocumentChunk(), 1.0, "fulltext", result.getRank())
                ));

        // 混合排序
        List<SearchResult> merged = mergeAndRank(vectorResults, textResults, topK);
        log.info("[混合检索] 合并后 {} 个子块", merged.size());
        merged.forEach(m -> log.debug("  合并 chunk#{} source={} score={}",
                m.getChunk().getChunkIndex(), m.getSource(), m.getScore()));

        return merged;
    }

    /**
     * 合并向量检索和全文检索结果
     */
    private List<SearchResult> mergeAndRank(Map<Long, SearchResult> vectorResults,
                                            Map<Long, SearchResult> textResults,
                                            int topK) {
        Map<Long, SearchResult> mergedMap = new HashMap<>(vectorResults);

        double vectorWeight = retrievalProperties.getVectorWeight();
        double textWeight = retrievalProperties.getTextWeight();

        for (Map.Entry<Long, SearchResult> entry : textResults.entrySet()) {
            Long chunkId = entry.getKey();
            SearchResult textResult = entry.getValue();

            if (mergedMap.containsKey(chunkId)) {
                // 混合评分
                SearchResult existing = mergedMap.get(chunkId);
                double textScore = textResult.getRank() != null ? textResult.getRank() : textResult.getScore();
                double combinedScore = existing.getScore() * vectorWeight + textScore * textWeight;
                existing.setScore(combinedScore);
                existing.setSource("hybrid");
            } else {
                double textScore = textResult.getRank() != null ? textResult.getRank() : textResult.getScore();
                textResult.setScore(textScore * textWeight);
                mergedMap.put(chunkId, textResult);
            }
        }

        return mergedMap.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 组装父块结果
     */
    private List<RetrievalResult> assembleParentResults(List<SearchResult> childResults, int maxParents) {
        // 按父块ID分组
        Map<Long, List<SearchResult>> parentGroups = new HashMap<>();

        for (SearchResult result : childResults) {
            DocumentChunk child = result.getChunk();
            Long parentId = child.getParentChunkId();

            if (parentId == null) {
                // 如果子块没有父块ID，跳过（不应该发生）
                log.warn("[子块没有父块ID] chunkId={}", child.getId());
                continue;
            }

            parentGroups.computeIfAbsent(parentId, k -> new ArrayList<>()).add(result);
        }

        log.info("[父块分组] 共 {} 个父块", parentGroups.size());
        parentGroups.forEach((parentId, children) -> {
            log.debug("  父块ID={}: {} 个子块", parentId, children.size());
        });

        List<RetrievalResult> results = new ArrayList<>();

        for (Map.Entry<Long, List<SearchResult>> entry : parentGroups.entrySet()) {
            if (results.size() >= maxParents) {
                break;
            }

            Long parentId = entry.getKey();
            List<SearchResult> matchedChildren = entry.getValue();

            // 加载父块
            Optional<DocumentChunk> parentOpt = chunkRepository.findById(parentId);
            if (parentOpt.isEmpty()) {
                log.warn("[父块不存在: {}]", parentId);
                continue;
            }

            DocumentChunk parent = parentOpt.get();

            // 计算综合分数
            double avgScore = matchedChildren.stream()
                    .mapToDouble(SearchResult::getScore)
                    .average()
                    .orElse(0.0);

            // 构建子块匹配信息
            List<ChildMatch> childMatches = matchedChildren.stream()
                    .map(r -> ChildMatch.builder()
                            .childChunkId(r.getChunk().getId())
                            .chunkIndex(r.getChunk().getChunkIndex())
                            .content(r.getChunk().getContent())  // 子块完整内容
                            .summary(r.getChunk().getSummary())
                            .matchScore(r.getScore())
                            .matchType(r.getSource())
                            .build())
                    .collect(Collectors.toList());

            // 截取父块内容
            String parentContent = parent.getContent();
            if (parentContent.length() > MAX_PARENT_CONTENT_LENGTH) {
                parentContent = parentContent.substring(0, MAX_PARENT_CONTENT_LENGTH) + "...";
            }

            results.add(RetrievalResult.builder()
                    .parentChunkId(parentId)
                    .parentContent(parentContent)
                    .sectionTitle(parent.getSectionTitle())
                    .pageRange(parent.getPageRange())
                    .relevanceScore(avgScore)
                    .matchedChildren(childMatches)
                    .sourceDocument(parent.getHeadingsPath())
                    .sourceType(determineSourceType(matchedChildren))
                    .build());
        }

        // 按相关性排序
        results.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));

        return results;
    }

    private String determineSourceType(List<SearchResult> children) {
        boolean hasVector = children.stream().anyMatch(c -> "vector".equals(c.getSource()) || "hybrid".equals(c.getSource()));
        boolean hasFullText = children.stream().anyMatch(c -> "fulltext".equals(c.getSource()) || "hybrid".equals(c.getSource()));

        if (hasVector && hasFullText) return "hybrid";
        if (hasVector) return "vector";
        if (hasFullText) return "fulltext";
        return "unknown";
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
