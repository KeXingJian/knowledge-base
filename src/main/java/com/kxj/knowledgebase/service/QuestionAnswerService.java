package com.kxj.knowledgebase.service;

import com.kxj.knowledgebase.constants.CacheConstants;
import com.kxj.knowledgebase.service.embedding.EmbeddingService;
import com.kxj.knowledgebase.service.rag.RAGService;
import com.kxj.knowledgebase.service.retriever.HybridRetriever;
import com.kxj.knowledgebase.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionAnswerService {

    private final HybridRetriever hybridRetriever;
    private final RAGService ragService;
    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, String> redisTemplate;

    public String answer(String question) {
        log.info("[收到问题: {}]", question);

        String cacheKey = CacheConstants.ANSWER_CACHE_PREFIX + question.hashCode();
        String cachedAnswer = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedAnswer != null) {
            log.info("[从缓存获取答案]");
            return cachedAnswer;
        }

        float[] queryEmbedding = embeddingService.embed(question);

        List<SearchResult> searchResults = hybridRetriever.retrieve(question, queryEmbedding, 3);

        if (searchResults.isEmpty()) {
            log.warn("[未找到相关文档片段]");
            return "抱歉，我在知识库中没有找到与您问题相关的信息。";
        }

        String context = searchResults.stream()
            .map(result -> result.getChunk().getContent())
            .collect(Collectors.joining("\n\n"));

        log.info("[找到 {} 个相关片段，开始生成回答]", searchResults.size());

        String answer = ragService.answer(question, context);

        redisTemplate.opsForValue().set(cacheKey, answer, CacheConstants.ANSWER_CACHE_TTL, TimeUnit.SECONDS);
        log.info("[答案已缓存，TTL: {}秒]", CacheConstants.ANSWER_CACHE_TTL);

        log.info("[问答完成]");
        return answer;
    }
}