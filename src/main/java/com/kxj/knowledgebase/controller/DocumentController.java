package com.kxj.knowledgebase.controller;

import com.kxj.knowledgebase.dto.ApiResponse;
import com.kxj.knowledgebase.dto.BatchUploadProgress;

import com.kxj.knowledgebase.entity.Document;

import com.kxj.knowledgebase.service.DocumentServiceOptimized;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentServiceOptimized documentService;

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long id) {
        try {
            log.info("[收到删除文档请求: {}]", id);
            documentService.deleteDocument(id);
            return ApiResponse.success("文档删除成功", null);
        } catch (IllegalArgumentException e) {
            log.warn("[删除文档失败: {}]", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (RuntimeException e) {
            log.error("[删除文档异常]", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("[删除文档未知异常]", e);
            return ApiResponse.error("删除文档失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<Document>> listDocuments() {
        try {
            log.info("[收到获取文档列表请求]");
            List<Document> documents = documentService.listDocuments();
            log.info("[返回 {} 个文档]", documents.size());
            return ApiResponse.success(documents);
        } catch (Exception e) {
            log.error("[获取文档列表失败]", e);
            return ApiResponse.error("获取文档列表失败: " + e.getMessage());
        }
    }

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry("txt",  "text/plain; charset=UTF-8"),
            Map.entry("md",   "text/plain; charset=UTF-8"),
            Map.entry("pdf",  "application/pdf"),
            Map.entry("png",  "image/png"),
            Map.entry("jpg",  "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif",  "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg",  "image/svg+xml")
    );

    @GetMapping("/{id}/content")
    public ResponseEntity<StreamingResponseBody> getDocumentContent(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean download) {
        log.info("[收到获取文档内容请求: id={}, download={}]", id, download);
        Document document = documentService.getDocument(id);

        String mimeType = MIME_TYPES.getOrDefault(
                document.getFileType().toLowerCase(),
                "application/octet-stream"
        );
        String encodedName = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String disposition = (download ? "attachment" : "inline") + "; filename*=UTF-8''" + encodedName;

        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = documentService.getMinioService().downloadFile(document.getFilePath())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(body);
    }

    @PostMapping("/batch-upload")
    public ApiResponse<String> batchUploadDocuments(@RequestParam("files") MultipartFile[] files) {
        try {
            log.info("[收到批量文档上传请求, 文档数量: {}]", files.length);
            
            if (files.length == 0)
                return ApiResponse.error("请选择要上传的文件");
            
            if (files.length > 100)
                return ApiResponse.error("单次最多支持上传100个文件");

            return ApiResponse.success(documentService.processDocumentsBatch(List.of(files)));
        } catch (Exception e) {
            log.error("[批量上传异常]", e);
            return ApiResponse.error("批量上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/batch-upload/progress/{taskId}")
    public ApiResponse<BatchUploadProgress> getBatchUploadProgress(@PathVariable String taskId) {
        try {
            log.info("[收到获取批量上传进度请求: {}]", taskId);
            BatchUploadProgress progress = documentService.getBatchUploadProgress(taskId);
            if (progress == null) {
                return ApiResponse.error("未找到批量上传任务");
            }
            return ApiResponse.success(progress);
        } catch (Exception e) {
            log.error("[获取批量上传进度异常]", e);
            return ApiResponse.error("获取批量上传进度失败: " + e.getMessage());
        }
    }
}