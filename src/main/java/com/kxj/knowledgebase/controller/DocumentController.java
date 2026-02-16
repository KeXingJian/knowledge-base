package com.kxj.knowledgebase.controller;

import com.kxj.knowledgebase.dto.ApiResponse;
import com.kxj.knowledgebase.entity.Document;
import com.kxj.knowledgebase.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ApiResponse<Document> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            log.info("[AI: 收到文档上传请求: {}, 大小: {} bytes]", file.getOriginalFilename(), file.getSize());
            Document document = documentService.processDocument(file);
            return ApiResponse.success("文档上传成功", document);
        } catch (IOException e) {
            log.error("[AI: 文档上传失败]", e);
            return ApiResponse.error("文档上传失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("[AI: 文档上传被拒绝: {}]", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("[AI: 文档上传异常]", e);
            return ApiResponse.error("文档上传异常: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long id) {
        try {
            log.info("[AI: 收到删除文档请求: {}]", id);
            documentService.deleteDocument(id);
            return ApiResponse.success("文档删除成功", null);
        } catch (IllegalArgumentException e) {
            log.warn("[AI: 删除文档失败: {}]", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (RuntimeException e) {
            log.error("[AI: 删除文档异常]", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("[AI: 删除文档未知异常]", e);
            return ApiResponse.error("删除文档失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<Document>> listDocuments() {
        try {
            log.info("[AI: 收到获取文档列表请求]");
            List<Document> documents = documentService.listDocuments();
            log.info("[AI: 返回 {} 个文档]", documents.size());
            return ApiResponse.success(documents);
        } catch (Exception e) {
            log.error("[AI: 获取文档列表失败]", e);
            return ApiResponse.error("获取文档列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Document> getDocument(@PathVariable Long id) {
        try {
            log.info("[AI: 收到获取文档详情请求: {}]", id);
            Document document = documentService.getDocument(id);
            return ApiResponse.success(document);
        } catch (IllegalArgumentException e) {
            log.warn("[AI: 获取文档失败: {}]", e.getMessage());
            return ApiResponse.error("获取文档详情失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[AI: 获取文档详情异常]", e);
            return ApiResponse.error("获取文档详情失败: " + e.getMessage());
        }
    }
}