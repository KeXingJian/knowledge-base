package com.kxj.knowledgebase.service.rag;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class RAGService {

    private ChatLanguageModel chatModel;

    @Value("${langchain4j.ollama.base-url}")
    private String baseUrl;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.ollama.chat-model.temperature:0.7}")
    private Double temperature;

    @Value("${langchain4j.ollama.chat-model.timeout}")
    private Duration timeout;

    @PostConstruct
    public void init() {
        log.info("[AI: 初始化聊天模型，baseUrl: {}, modelName: {}, temperature: {}]", baseUrl, modelName, temperature);
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .build();
    }

    public String answer(String question, String context) {
        log.info("[AI: 开始RAG问答，question: {}]", question);

        String prompt = buildPrompt(question, context);

        log.info("[AI: 调用大模型生成回答]");
        String answer = chatModel.generate(prompt);

        log.info("[AI: RAG问答完成，回答长度: {}]", answer.length());
        return answer;
    }

    private String buildPrompt(String question, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能助手，请根据以下提供的文档内容回答用户的问题。\n\n");
        prompt.append("文档内容：\n");
        prompt.append(context);
        prompt.append("\n\n");
        prompt.append("用户问题：\n");
        prompt.append(question);
        prompt.append("\n\n");
        prompt.append("请根据文档内容回答问题。如果文档中没有相关信息，请直接说明，不要编造内容。");

        return prompt.toString();
    }
}