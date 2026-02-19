package com.kxj.knowledgebase.service.rag;

import com.kxj.knowledgebase.dto.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
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
        log.info("[初始化聊天模型，baseUrl: {}, modelName: {}, temperature: {}]", baseUrl, modelName, temperature);
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .build();
    }

    public String answer(String question, String context) {
        log.info("[开始RAG问答，question: {}]", question);

        String prompt = buildPrompt(question, context);

        log.info("[调用大模型生成回答]");
        String answer = chatModel.generate(prompt);

        log.info("[RAG问答完成，回答长度: {}]", answer.length());
        return answer;
    }

    public String answerWithContext(String question, String context, List<ChatMessage> history) {
        log.info("[开始多轮RAG问答，question: {}, historySize: {}]", question, history.size());

        String prompt = buildPromptWithContext(question, context, history);

        log.info("[调用大模型生成回答]");
        String answer = chatModel.generate(prompt);

        log.info("[多轮RAG问答完成，回答长度: {}]", answer.length());
        return answer;
    }

    private String buildPrompt(String question, String context) {
        return "你是一个智能助手，请根据以下提供的文档内容回答用户的问题。\n\n" +
                "文档内容：\n" +
                context +
                "\n\n" +
                "用户问题：\n" +
                question +
                "\n\n" +
                "请根据文档内容回答问题。如果文档中没有相关信息，请直接说明，不要编造内容。";
    }

    private String buildPromptWithContext(String question, String context, List<ChatMessage> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能助手，请根据以下提供的文档内容和对话历史回答用户的问题。\n\n");

        if (!history.isEmpty()) {
            prompt.append("对话历史：\n");
            for (ChatMessage message : history) {
                prompt.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("文档内容：\n")
              .append(context)
              .append("\n\n")
              .append("用户问题：\n")
              .append(question)
              .append("\n\n")
              .append("请根据文档内容和对话历史回答问题。如果文档中没有相关信息，请直接说明，不要编造内容。");

        return prompt.toString();
    }
}