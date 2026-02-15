package com.kxj.knowledgebase.controller;

import com.kxj.knowledgebase.dto.ApiResponse;
import com.kxj.knowledgebase.service.QuestionAnswerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QuestionAnswerController {

    private final QuestionAnswerService questionAnswerService;

    @PostMapping("/ask")
    public ApiResponse<String> askQuestion(@RequestBody Map<String, String> request) {
        try {
            String question = request.get("question");
            if (question == null || question.trim().isEmpty()) {
                return ApiResponse.error("问题不能为空");
            }

            log.info("[AI: 收到问题请求: {}]", question);
            String answer = questionAnswerService.answer(question);
            return ApiResponse.success(answer);
        } catch (Exception e) {
            log.error("[AI: 问答失败]", e);
            return ApiResponse.error("问答失败: " + e.getMessage());
        }
    }
}