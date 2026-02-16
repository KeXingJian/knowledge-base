package com.kxj.knowledgebase.service.retriever;

import com.kxj.knowledgebase.config.RetrievalProperties;
import com.kxj.knowledgebase.entity.DocumentChunk;
import com.kxj.knowledgebase.repository.DocumentChunkRepository;
import com.kxj.knowledgebase.repository.FullTextSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetriever {

    private final DocumentChunkRepository chunkRepository;
    private final RetrievalProperties retrievalProperties;

    public List<SearchResult> retrieve(String query, float[] queryEmbedding, int topK) {
        log.info("[AI: 开始混合检索，query: {}, topK: {}]", query, topK);

        List<SearchResult> vectorResults = vectorSearch(queryEmbedding, topK);
        List<SearchResult> textResults = textSearch(query, topK);

        List<SearchResult> combinedResults = mergeAndRank(vectorResults, textResults, topK);

        log.info("[AI: 混合检索完成，返回 {} 个结果]", combinedResults.size());
        return combinedResults;
    }

    private List<SearchResult> vectorSearch(float[] queryEmbedding, int topK) {
        log.info("[AI: 执行向量检索]");
        String embeddingString = floatArrayToString(queryEmbedding);
        List<DocumentChunk> chunks = chunkRepository.findNearestNeighbors(embeddingString, topK);
        return chunks.stream()
            .map(chunk -> new SearchResult(chunk, 1.0, "vector"))
            .collect(Collectors.toList());
    }

    private List<SearchResult> textSearch(String query, int topK) {
        if (retrievalProperties.isEnableFulltextSearch()) {
            return fullTextSearch(query, topK);
        } else if (retrievalProperties.isFallbackToKeywordSearch()) {
            return keywordSearch(query, topK);
        } else {
            log.info("[AI: 文本检索已禁用]");
            return Collections.emptyList();
        }
    }

    private List<SearchResult> fullTextSearch(String query, int topK) {
        log.info("[AI: 执行全文检索，query: {}]", query);
        
        try {
            List<FullTextSearchResult> results = chunkRepository.fullTextSearch(query, topK);
            
            return results.stream()
                .map(result -> {
                    DocumentChunk chunk = result.toDocumentChunk();
                    return new SearchResult(chunk, 1.0, "fulltext", result.getRank());
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[AI: 全文检索失败，降级为关键词检索]", e);
            if (retrievalProperties.isFallbackToKeywordSearch()) {
                return keywordSearch(query, topK);
            } else {
                return Collections.emptyList();
            }
        }
    }

    private List<SearchResult> keywordSearch(String query, int topK) {
        log.info("[AI: 执行关键词检索（降级方案）]");
        
        try {
            List<DocumentChunk> chunks = chunkRepository.findByContentLike(query, topK);
            
            return chunks.stream()
                .map(chunk -> new SearchResult(chunk, 1.0, "keyword"))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[AI: 关键词检索失败]", e);
            return Collections.emptyList();
        }
    }

    private List<SearchResult> mergeAndRank(List<SearchResult> vectorResults, 
                                           List<SearchResult> textResults, 
                                           int topK) {
        log.info("[AI: 合并并重排序结果]");
        
        Map<Long, SearchResult> mergedMap = new HashMap<>();

        for (SearchResult result : vectorResults) {
            Long chunkId = result.getChunk().getId();
            mergedMap.put(chunkId, result);
        }

        double vectorWeight = retrievalProperties.getVectorWeight();
        double textWeight = retrievalProperties.getTextWeight();

        for (SearchResult result : textResults) {
            Long chunkId = result.getChunk().getId();
            if (mergedMap.containsKey(chunkId)) {
                SearchResult existing = mergedMap.get(chunkId);
                double textScore = result.getRank() != null ? result.getRank().doubleValue() : result.getScore();
                double combinedScore = existing.getScore() * vectorWeight + textScore * textWeight;
                existing.setScore(combinedScore);
                existing.setSource("hybrid");
            } else {
                double textScore = result.getRank() != null ? result.getRank().doubleValue() : result.getScore();
                result.setScore(textScore * textWeight);
                mergedMap.put(chunkId, result);
            }
        }

        return mergedMap.values().stream()
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .limit(topK)
            .collect(Collectors.toList());
    }

    private String floatArrayToString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}