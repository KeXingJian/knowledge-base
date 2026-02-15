package com.kxj.knowledgebase.service.retriever;

import com.kxj.knowledgebase.entity.DocumentChunk;
import com.kxj.knowledgebase.repository.DocumentChunkRepository;
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

    public List<SearchResult> retrieve(String query, float[] queryEmbedding, int topK) {
        log.info("[AI: 开始混合检索，query: {}, topK: {}]", query, topK);

        List<SearchResult> vectorResults = vectorSearch(queryEmbedding, topK);
        List<SearchResult> keywordResults = keywordSearch(query, topK);

        List<SearchResult> combinedResults = mergeAndRank(vectorResults, keywordResults, topK);

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

    private List<SearchResult> keywordSearch(String query, int topK) {
        log.info("[AI: 执行关键词检索]");
        List<DocumentChunk> allChunks = chunkRepository.findAll();
        
        String[] keywords = query.toLowerCase().split("\\s+");

        return allChunks.stream()
            .map(chunk -> {
                String content = chunk.getContent().toLowerCase();
                int matchCount = 0;
                for (String keyword : keywords) {
                    if (content.contains(keyword)) {
                        matchCount++;
                    }
                }
                double score = (double) matchCount / keywords.length;
                return new SearchResult(chunk, score, "keyword");
            })
            .filter(result -> result.getScore() > 0)
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .limit(topK)
            .collect(Collectors.toList());
    }

    private List<SearchResult> mergeAndRank(List<SearchResult> vectorResults, 
                                           List<SearchResult> keywordResults, 
                                           int topK) {
        log.info("[AI: 合并并重排序结果]");
        
        Map<Long, SearchResult> mergedMap = new HashMap<>();

        for (SearchResult result : vectorResults) {
            Long chunkId = result.getChunk().getId();
            mergedMap.put(chunkId, result);
        }

        for (SearchResult result : keywordResults) {
            Long chunkId = result.getChunk().getId();
            if (mergedMap.containsKey(chunkId)) {
                SearchResult existing = mergedMap.get(chunkId);
                double combinedScore = existing.getScore() * 0.6 + result.getScore() * 0.4;
                existing.setScore(combinedScore);
                existing.setSource("hybrid");
            } else {
                result.setScore(result.getScore() * 0.4);
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