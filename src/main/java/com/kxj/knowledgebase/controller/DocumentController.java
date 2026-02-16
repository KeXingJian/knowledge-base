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
    public ResponseEntity<ApiResponse<Document>> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            log.info("[AI: 收到文档上传请求: {}, 大小: {} bytes]", file.getOriginalFilename(), file.getSize());
            Document document = documentService.processDocument(file);
            return ResponseEntity.ok(ApiResponse.success("文档上传成功", document));
        } catch (IOException e) {
            log.error("[AI: 文档上传失败]", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("文档上传失败: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("[AI: 文档上传被拒绝: {}]", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[AI: 文档上传异常]", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("文档上传异常: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable Long id) {
        try {
            log.info("[AI: 收到删除文档请求: {}]", id);
            documentService.deleteDocument(id);
            return ResponseEntity.ok(ApiResponse.success("文档删除成功", null));
        } catch (IllegalArgumentException e) {
            log.warn("[AI: 删除文档失败: {}]", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("[AI: 删除文档异常]", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[AI: 删除文档未知异常]", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("删除文档失败: " + e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<Document>>> listDocuments() {
        try {
            log.info("[AI: 收到获取文档列表请求]");
            List<Document> documents = documentService.listDocuments();
            log.info("[AI: 返回 {} 个文档]", documents.size());
            return ResponseEntity.ok(ApiResponse.success(documents));
        } catch (Exception e) {
            log.error("[AI: 获取文档列表失败]", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("获取文档列表失败: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Document>> getDocument(@PathVariable Long id) {
        try {
            log.info("[AI: 收到获取文档详情请求: {}]", id);
            Document document = documentService.getDocument(id);
            return ResponseEntity.ok(ApiResponse.success(document));
        } catch (IllegalArgumentException e) {
            log.warn("[AI: 获取文档失败: {}]", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("[AI: 获取文档详情异常]", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("获取文档详情失败: " + e.getMessage()));
        }
    }
}