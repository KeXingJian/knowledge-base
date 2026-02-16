package com.kxj.knowledgebase.controller;

import com.kxj.knowledgebase.dto.ApiResponse;
import com.kxj.knowledgebase.dto.BatchUploadProgress;
import com.kxj.knowledgebase.dto.BatchUploadResponse;
import com.kxj.knowledgebase.dto.DocumentUploadResult;
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

    @PostMapping("/batch-upload")
    public ApiResponse<String> batchUploadDocuments(@RequestParam("files") MultipartFile[] files) {
        try {
            log.info("[AI: 收到批量文档上传请求, 文档数量: {}]", files.length);
            
            if (files.length == 0)
                return ApiResponse.error("请选择要上传的文件");
            
            if (files.length > 100)
                return ApiResponse.error("单次最多支持上传100个文件");

            return ApiResponse.success(documentService.processDocumentsBatch(List.of(files)));
        } catch (Exception e) {
            log.error("[AI: 批量上传异常]", e);
            return ApiResponse.error("批量上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/batch-upload/progress/{taskId}")
    public ApiResponse<BatchUploadProgress> getBatchUploadProgress(@PathVariable String taskId) {
        try {
            log.info("[AI: 收到获取批量上传进度请求: {}]", taskId);
            BatchUploadProgress progress = documentService.getBatchUploadProgress(taskId);
            if (progress == null) {
                return ApiResponse.error("未找到批量上传任务");
            }
            return ApiResponse.success(progress);
        } catch (Exception e) {
            log.error("[AI: 获取批量上传进度异常]", e);
            return ApiResponse.error("获取批量上传进度失败: " + e.getMessage());
        }
    }
}