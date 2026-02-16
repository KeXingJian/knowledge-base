package com.kxj.knowledgebase.service;

import com.kxj.knowledgebase.config.DocumentProcessingProperties;
import com.kxj.knowledgebase.constants.CacheConstants;
import com.kxj.knowledgebase.dto.BatchUploadProgress;
import com.kxj.knowledgebase.entity.Document;
import com.kxj.knowledgebase.entity.DocumentChunk;
import com.kxj.knowledgebase.repository.DocumentRepository;
import com.kxj.knowledgebase.service.embedding.EmbeddingService;
import com.kxj.knowledgebase.service.storage.MinioService;
import com.kxj.knowledgebase.service.storage.VectorStoreService;
import com.kxj.knowledgebase.util.FileUtils;
import com.kxj.knowledgebase.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final MinioService minioService;
    private final ExecutorService embeddingExecutorService;
    private final DocumentProcessingProperties documentProcessingProperties;
    private final RedisTemplate<String, Object> redisTemplate;

    private final Semaphore documentUploadSemaphore = new Semaphore(5);

    private static final String INCREMENT_COUNTER_LUA = 
        "local key = KEYS[1] " +
        "local increment = tonumber(ARGV[1]) " +
        "local current = tonumber(redis.call('GET', key) or '0') " +
        "local new = current + increment " +
        "redis.call('SET', key, new) " +
        "redis.call('EXPIRE', key, ARGV[2]) " +
        "return new";

    @Transactional
    public void processDocumentsBatch(List<MultipartFile> files) {
        log.info("[AI: 开始批量处理文档, 文档数量: {}]", files.size());
        String taskId = UUID.randomUUID().toString();

        String completedKey = CacheConstants.DOCUMENT_UPLOAD_PROGRESS_PREFIX + taskId + ":completed";
        String failedKey = CacheConstants.DOCUMENT_UPLOAD_PROGRESS_PREFIX + taskId + ":failed";
        String totalKey = CacheConstants.DOCUMENT_UPLOAD_PROGRESS_PREFIX + taskId + ":total";

        redisTemplate.opsForValue().set(completedKey, 0, CacheConstants.DOCUMENT_UPLOAD_PROGRESS_TTL, java.util.concurrent.TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(failedKey, 0, CacheConstants.DOCUMENT_UPLOAD_PROGRESS_TTL, java.util.concurrent.TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(totalKey, files.size(), CacheConstants.DOCUMENT_UPLOAD_PROGRESS_TTL, java.util.concurrent.TimeUnit.SECONDS);

        for (MultipartFile file : files) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    documentUploadSemaphore.acquire();
                    log.info("[AI: 第一层并发: 开始处理文档]");
                    long startTime = System.currentTimeMillis();
                    processDocument(file);
                    long processingTime = System.currentTimeMillis() - startTime;
                    incrementCounter(completedKey);
                    log.info("[AI: 文档处理完成: {}, 耗时: {}ms]", file.getOriginalFilename(), processingTime);
                } catch (Exception e) {
                    log.error("[AI: 文档处理失败: {}]", file.getOriginalFilename(), e);
                    incrementCounter(failedKey);
                } finally {
                    documentUploadSemaphore.release();
                }
                return null;
            }, embeddingExecutorService);
        }


    }

    @Transactional
    public void processDocument(MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("[AI: 开始处理文档: {}]", file.getOriginalFilename());

        String fileHash = FileUtils.calculateFileHash(file);

        documentRepository.findByFileHash(fileHash).ifPresent(doc -> {
            log.info("[AI: 文档已存在，跳过处理: {}]", file.getOriginalFilename());
            throw new IllegalArgumentException("文档已存在");
        });

        String fileName = file.getOriginalFilename();
        String fileType = FileUtils.getFileExtension(fileName);
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
    }

    private void incrementCounter(String key) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCREMENT_COUNTER_LUA, Long.class);
            Long result = redisTemplate.execute(script,
                    java.util.Collections.singletonList(key),
                    String.valueOf(1),
                    String.valueOf(CacheConstants.DOCUMENT_UPLOAD_PROGRESS_TTL));
            log.debug("[AI: 计数器增加: key={}, increment={}, result={}]", key, 1, result);
        } catch (Exception e) {
            log.error("[AI: 计数器增加失败: key={}]", key, e);
        }
    }

    private void processDocumentInBatches(String objectName, Document document) throws IOException {
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

            if (!chunkBuilder.isEmpty()) {
                chunkContents.add(chunkBuilder.toString());
            }

            int totalChunks = chunkContents.size();
            log.info("[AI: 文档切分完成，共 {} 个片段，开始并发向量化]", totalChunks);

            List<DocumentChunk> allChunks;

            log.info("[AI: 启用并发向量化处理]");
            allChunks = processChunksInParallel(chunkContents, document);

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
            
            CompletableFuture<DocumentChunk> future = CompletableFuture.supplyAsync(() ->
                    processChunk(chunkContent, chunkIndex, document), embeddingExecutorService
            );
            
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


    private DocumentChunk processChunk(String chunkContent, int index, Document document) {
        if (chunkContent.trim().isEmpty()) {
            return null;
        }

        log.info("[AI: 开始向量化片段 {}, 内容长度: {}]", index, chunkContent.length());
        
        float[] embedding = embeddingService.embed(chunkContent);
        String embeddingString = StringUtils.floatArrayToString(embedding);
        int tokenCount = StringUtils.estimateTokenCount(chunkContent);
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



    public BatchUploadProgress getBatchUploadProgress(String taskId) {
        try {
            
            Integer completed = (Integer) redisTemplate.opsForValue().get(
                    CacheConstants.DOCUMENT_UPLOAD_PROGRESS_PREFIX + taskId + ":completed"
            );
            Integer failed = (Integer) redisTemplate.opsForValue().get(
                    CacheConstants.DOCUMENT_UPLOAD_PROGRESS_PREFIX + taskId + ":failed"
            );
            Integer total = (Integer) redisTemplate.opsForValue().get(
                    CacheConstants.DOCUMENT_UPLOAD_PROGRESS_PREFIX + taskId + ":total"
            );
            
            if (completed == null && failed == null && total == null) {
                log.warn("[AI: 未找到批量上传任务进度: {}]", taskId);
                return null;
            }
            
            if (completed == null) completed = 0;
            if (failed == null) failed = 0;
            if (total == null) total = 0;

            return BatchUploadProgress.builder()
                .taskId(taskId)
                .totalDocuments(total)
                .completedDocuments(completed)
                .failedDocuments(failed)
                .progress(total > 0 ? (double) (completed + failed) / total * 100 : 0)
                .status(total > 0 && (completed + failed) >= total ? "COMPLETED" : "PROCESSING")
                .build();
        } catch (Exception e) {
            log.error("[AI: 获取批量上传进度失败]", e);
            return null;
        }
    }
}