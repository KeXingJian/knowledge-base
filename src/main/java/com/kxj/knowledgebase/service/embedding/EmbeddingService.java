package com.kxj.knowledgebase.service.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmbeddingService {

    private EmbeddingModel embeddingModel;

    @Value("${langchain4j.ollama.base-url}")
    private String baseUrl;

    @Value("${langchain4j.ollama.embedding-model.model-name}")
    private String modelName;

    @PostConstruct
    public void init() {
        log.info("[AI: 初始化嵌入模型，baseUrl: {}, modelName: {}]", baseUrl, modelName);
        this.embeddingModel = OllamaEmbeddingModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .build();
    }

    public float[] embed(String text) {
        log.info("[AI: 开始向量化文本，长度: {}]", text.length());
        Embedding embedding = embeddingModel.embed(text).content();
        float[] vector = embedding.vector();
        log.info("[AI: 向量化完成，向量维度: {}]", vector.length);
        return vector;
    }

    public int getDimension() {
        return 768;
    }
}