package com.kxj.knowledgebase.service;

import com.kxj.knowledgebase.config.DocumentProcessingProperties;
import com.kxj.knowledgebase.entity.Document;
import com.kxj.knowledgebase.entity.DocumentChunk;
import com.kxj.knowledgebase.repository.DocumentRepository;
import com.kxj.knowledgebase.service.embedding.EmbeddingService;
import com.kxj.knowledgebase.service.loader.DocumentLoaderFactory;
import com.kxj.knowledgebase.service.splitter.Chunk;
import com.kxj.knowledgebase.service.splitter.SimpleTextSplitter;
import com.kxj.knowledgebase.service.storage.MinioService;
import com.kxj.knowledgebase.service.storage.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentLoaderFactory documentLoaderFactory;
    private final SimpleTextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final MinioService minioService;
    private final ExecutorService embeddingExecutorService;
    private final DocumentProcessingProperties documentProcessingProperties;

    private static final String UPLOAD_DIR = "./uploads";

    @Transactional
    public Document processDocument(MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("[AI: 开始处理文档: {}]", file.getOriginalFilename());

        String fileHash = calculateFileHash(file);
        
        documentRepository.findByFileHash(fileHash).ifPresent(doc -> {
            log.info("[AI: 文档已存在，跳过处理: {}]", file.getOriginalFilename());
            throw new IllegalArgumentException("文档已存在");
        });

        String fileName = file.getOriginalFilename();
        String fileType = getFileExtension(fileName);
        String objectName = fileHash + "/" + fileName;
        
        log.info("[AI: 开始上传文件到 MinIO: {}]", objectName);
        minioService.uploadFile(objectName, file.getInputStream(), file.getSize(), file.getContentType());

        Document document = Document.builder()
            .fileName(fileName)
            .fileType(fileType)
            .filePath(objectName)
            .fileSize(file.getSize())
            .fileHash(fileHash)
            .chunkCount(0)
            .uploadTime(LocalDateTime.now())
            .updateTime(LocalDateTime.now())
            .processed(false)
            .build();

        document = documentRepository.save(document);

        log.info("[AI: 开始流式处理文档]");
        processDocumentInBatches(objectName, document);

        document.setProcessed(true);
        document.setUpdateTime(LocalDateTime.now());
        document = documentRepository.save(document);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        log.info("[AI: 文档处理完成: {}, 耗时: {}ms, 片段数: {}]", fileName, duration, document.getChunkCount());
        return document;
    }

    private void processDocumentInBatches(String objectName, Document document) throws IOException {
        String fileType = getFileExtension(objectName);
        
        log.info("[AI: 开始从 MinIO 下载文件: {}]", objectName);
        try (InputStream inputStream = minioService.downloadFile(objectName);
             BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(inputStream))) {
            
            log.info("[AI: 开始流式切分文档]");
            
            List<String> chunkContents = new ArrayList<>();
            StringBuilder chunkBuilder = new StringBuilder();
            String line;
            int currentLength = 0;
            int chunkSize = documentProcessingProperties.getChunkSize();

            while ((line = reader.readLine()) != null) {
                chunkBuilder.append(line).append("\n");
                currentLength += line.length() + 1;

                if (currentLength >= chunkSize) {
                    chunkContents.add(chunkBuilder.toString());
                    chunkBuilder.setLength(0);
                    currentLength = 0;
                }
            }

            if (chunkBuilder.length() > 0) {
                chunkContents.add(chunkBuilder.toString());
            }

            int totalChunks = chunkContents.size();
            log.info("[AI: 文档切分完成，共 {} 个片段，开始并发向量化]", totalChunks);

            List<DocumentChunk> allChunks;
            
            if (documentProcessingProperties.isEnableParallelProcessing()) {
                log.info("[AI: 启用并发向量化处理]");
                allChunks = processChunksInParallel(chunkContents, document);
            } else {
                log.info("[AI: 使用串行向量化处理]");
                allChunks = processChunksSequentially(chunkContents, document);
            }

            log.info("[AI: 向量化完成，开始批量保存 {} 个片段]", allChunks.size());
            
            int batchSize = documentProcessingProperties.getBatchSize();
            for (int i = 0; i < allChunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allChunks.size());
                List<DocumentChunk> batch = allChunks.subList(i, end);
                vectorStoreService.saveChunks(new ArrayList<>(batch));
                log.info("[AI: 已保存批次 {}/{}, 片段数: {}]", 
                    (i / batchSize) + 1, 
                    (allChunks.size() + batchSize - 1) / batchSize, 
                    batch.size());
            }

            document.setChunkCount(allChunks.size());
            log.info("[AI: 流式处理完成，共生成 {} 个片段]", allChunks.size());
        }
    }

    private List<DocumentChunk> processChunksInParallel(List<String> chunkContents, Document document) {
        int totalChunks = chunkContents.size();
        List<CompletableFuture<DocumentChunk>> futures = new ArrayList<>();
        
        for (int i = 0; i < totalChunks; i++) {
            final int chunkIndex = i;
            final String chunkContent = chunkContents.get(i);
            
            CompletableFuture<DocumentChunk> future = CompletableFuture.supplyAsync(() -> {
                return processChunk(chunkContent, chunkIndex, document);
            }, embeddingExecutorService);
            
            futures.add(future);
        }

        log.info("[AI: 等待所有向量化任务完成]");
        List<DocumentChunk> allChunks = new ArrayList<>(totalChunks);
        for (CompletableFuture<DocumentChunk> future : futures) {
            try {
                DocumentChunk chunk = future.get();
                if (chunk != null) {
                    allChunks.add(chunk);
                }
            } catch (Exception e) {
                log.error("[AI: 向量化任务失败]", e);
            }
        }
        
        return allChunks;
    }

    private List<DocumentChunk> processChunksSequentially(List<String> chunkContents, Document document) {
        List<DocumentChunk> allChunks = new ArrayList<>(chunkContents.size());
        
        for (int i = 0; i < chunkContents.size(); i++) {
            DocumentChunk chunk = processChunk(chunkContents.get(i), i, document);
            if (chunk != null) {
                allChunks.add(chunk);
            }
        }
        
        return allChunks;
    }

    private DocumentChunk processChunk(String chunkContent, int index, Document document) {
        if (chunkContent.trim().isEmpty()) {
            return null;
        }

        log.info("[AI: 开始向量化片段 {}, 内容长度: {}]", index, chunkContent.length());
        
        float[] embedding = embeddingService.embed(chunkContent);
        String embeddingString = floatArrayToString(embedding);
        int tokenCount = estimateTokenCount(chunkContent);
        String metadata = "chunk_index=" + index + ",token_count=" + tokenCount;

        DocumentChunk documentChunk = DocumentChunk.builder()
            .documentId(document.getId())
            .chunkIndex(index)
            .content(chunkContent)
            .embedding(embeddingString)
            .createTime(LocalDateTime.now())
            .metadata(metadata)
            .tokenCount(tokenCount)
            .build();

        log.info("[AI: 片段 {} 向量化完成]", index);
        return documentChunk;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long documentId) {
        log.info("[AI: 开始删除文档: {}]", documentId);
        
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));

        log.info("[AI: 开始删除文档 {} 的 {} 个切片]", documentId, document.getChunkCount());
        vectorStoreService.deleteChunksByDocumentId(documentId);
        log.info("[AI: 切片删除完成]");

        try {
            log.info("[AI: 开始从 MinIO 删除文件: {}]", document.getFilePath());
            minioService.deleteFile(document.getFilePath());
            log.info("[AI: MinIO 文件删除完成]");
        } catch (Exception e) {
            log.error("[AI: 删除 MinIO 文件失败: {}]", document.getFilePath(), e);
            throw new RuntimeException("删除MinIO文件失败: " + e.getMessage(), e);
        }

        documentRepository.deleteById(documentId);
        log.info("[AI: 文档删除完成: {}]", documentId);
    }

    public List<Document> listDocuments() {
        log.info("[AI: 获取文档列表]");
        return documentRepository.findAll();
    }

    public Document getDocument(Long documentId) {
        log.info("[AI: 获取文档详情: {}]", documentId);
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));
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

    private int estimateTokenCount(String text) {
        return text.length() / 3;
    }

    private String calculateFileHash(MultipartFile file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = file.getBytes();
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("计算文件哈希失败", e);
        }
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}