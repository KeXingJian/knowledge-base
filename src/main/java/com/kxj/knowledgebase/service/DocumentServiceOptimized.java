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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceOptimized {

    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    @Getter
    private final MinioService minioService;
    private final ExecutorService embeddingExecutorService;
    private final DocumentProcessingProperties documentProcessingProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Semaphore globalSemaphore;

    @Transactional
    public String processDocumentsBatch(List<MultipartFile> files) {
        log.info("[开始文档去重，原始文件数: {}]", files.size());

        Set<String> fileHashSet = new HashSet<>();
        Map<String, MultipartFile> hashToFileMap = new HashMap<>();

        for (MultipartFile file : files) {
            try {
                String fileHash = FileUtils.calculateFileHash(file);
                if (!fileHashSet.contains(fileHash)) {
                    fileHashSet.add(fileHash);
                    hashToFileMap.put(fileHash, file);
                } else {
                    log.info("[跳过重复文件: {}]", file.getOriginalFilename());
                }
            } catch (IOException e) {
                log.error("[计算文件哈希失败: {}]", file.getOriginalFilename(), e);
            }
        }

        log.info("[第一层过滤完成，去重后文件数: {}]", fileHashSet.size());

        List<MultipartFile> uniqueFiles = new ArrayList<>();
        if (!fileHashSet.isEmpty()) {
            List<String> hashList = new ArrayList<>(fileHashSet);
            List<Document> existingDocs = documentRepository.findByFileHashIn(hashList);
            Set<String> existingHashes = existingDocs.stream()
                    .map(Document::getFileHash)
                    .collect(Collectors.toSet());
            log.info("[数据库已存在文档数: {}]", existingHashes.size());

            uniqueFiles = fileHashSet.stream()
                    .filter(hash -> !existingHashes.contains(hash))
                    .map(hashToFileMap::get)
                    .toList();
        }

        log.info("[第二层过滤完成，最终待处理文件数: {}]", uniqueFiles.size());

        log.info("[开始批量处理文档，文档数量: {}]", uniqueFiles.size());
        String taskId = UUID.randomUUID().toString();

        String completedKey = CacheConstants.DOCUMENT_UPLOAD_PROGRESS_PREFIX + taskId + ":completed";
        String failedKey = CacheConstants.DOCUMENT_UPLOAD_PROGRESS_PREFIX + taskId + ":failed";
        String totalKey = CacheConstants.DOCUMENT_UPLOAD_PROGRESS_PREFIX + taskId + ":total";

        redisTemplate.opsForValue().set(
                completedKey, 0, CacheConstants.DOCUMENT_UPLOAD_PROGRESS_TTL, java.util.concurrent.TimeUnit.SECONDS
        );
        redisTemplate.opsForValue().set(
                failedKey, 0, CacheConstants.DOCUMENT_UPLOAD_PROGRESS_TTL, java.util.concurrent.TimeUnit.SECONDS
        );
        redisTemplate.opsForValue().set(
                totalKey, uniqueFiles.size(), CacheConstants.DOCUMENT_UPLOAD_PROGRESS_TTL, java.util.concurrent.TimeUnit.SECONDS
        );

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (MultipartFile file : uniqueFiles) {
            final String fileName = file.getOriginalFilename();
            final long fileSize = file.getSize();
            final String contentType = file.getContentType();

            try {
                final byte[] fileBytes = file.getBytes();
                final String fileHash = FileUtils.calculateFileHash(fileBytes);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        globalSemaphore.acquire();
                        log.info("[获取信号量，开始处理文档: {}]", fileName);
                        long startTime = System.currentTimeMillis();
                        processDocument(fileName, fileSize, contentType, fileBytes, fileHash);
                        long processingTime = System.currentTimeMillis() - startTime;
                        incrementCounter(completedKey);
                        log.info("[文档处理完成: {}, 耗时: {}ms]", fileName, processingTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("[文档处理被中断: {}]", fileName, e);
                        incrementCounter(failedKey);
                    } catch (Exception e) {
                        log.error("[文档处理失败: {}]", fileName, e);
                        incrementCounter(failedKey);
                    } finally {
                        globalSemaphore.release();
                        log.info("[释放信号量，文档: {}]", fileName);
                    }
                }, embeddingExecutorService);

                futures.add(future);
            } catch (IOException e) {
                log.error("[读取文件失败: {}]", fileName, e);
                incrementCounter(failedKey);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[批量处理完成，但有异常]", ex);
            } else {
                log.info("[批量处理全部完成]");
            }
        });

        return taskId;
    }

    @Transactional
    public void processDocument(String fileName, long fileSize, String contentType, byte[] fileBytes, String fileHash) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("[开始处理文档: {}]", fileName);

        String fileType = FileUtils.getFileExtension(fileName);
        String objectName = fileHash + "/" + fileName;

        log.info("[开始上传文件到 MinIO: {}]", objectName);
        minioService.uploadFile(objectName, new java.io.ByteArrayInputStream(fileBytes), fileSize, contentType);

        Document document = Document.builder()
                .fileName(fileName)
                .fileType(fileType)
                .filePath(objectName)
                .fileSize(fileSize)
                .fileHash(fileHash)
                .chunkCount(0)
                .uploadTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .processed(false)
                .build();

        document = documentRepository.save(document);

        log.info("[开始流式处理文档]");
        processDocumentInBatchesOptimized(objectName, document);

        document.setProcessed(true);
        document.setUpdateTime(LocalDateTime.now());
        document = documentRepository.save(document);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        log.info("[文档处理完成: {}, 耗时: {}ms, 片段数: {}]", fileName, duration, document.getChunkCount());
    }

    private void processDocumentInBatchesOptimized(String objectName, Document document) throws IOException {
        log.info("[开始从 MinIO 下载文件: {}]", objectName);
        try (InputStream inputStream = minioService.downloadFile(objectName);
             BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(inputStream))) {

            log.info("[开始流式切分文档]");

            List<String> chunkContents = new ArrayList<>();
            StringBuilder chunkBuilder = new StringBuilder();
            String line;
            int currentLength = 0;
            int chunkSize = documentProcessingProperties.getChunkSize();

            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.strip();
                chunkBuilder.append(trimmedLine).append("\n");
                currentLength += trimmedLine.length() + 1;

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
            log.info("[文档切分完成，共 {} 个片段，开始并发向量化]", totalChunks);

            List<DocumentChunk> allChunks;

            log.info("[启用并发向量化处理]");
            allChunks = processChunksInParallelOptimized(chunkContents, document);

            log.info("[向量化完成，开始批量保存 {} 个片段]", allChunks.size());

            int batchSize = documentProcessingProperties.getBatchSize();
            for (int i = 0; i < allChunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allChunks.size());
                List<DocumentChunk> batch = allChunks.subList(i, end);
                vectorStoreService.saveChunks(new ArrayList<>(batch));
                log.info("[已保存批次 {}/{}, 片段数: {}]",
                    (i / batchSize) + 1,
                    (allChunks.size() + batchSize - 1) / batchSize,
                    batch.size());
            }

            document.setChunkCount(allChunks.size());
            log.info("[流式处理完成，共生成 {} 个片段]", allChunks.size());
        }
    }

    private List<DocumentChunk> processChunksInParallelOptimized(List<String> chunkContents, Document document) {
        int totalChunks = chunkContents.size();
        AtomicInteger completedCount = new AtomicInteger(0);

        List<DocumentChunk> allChunks = chunkContents.stream()
                .map(chunkContent -> CompletableFuture.supplyAsync(() -> {
                    int index = completedCount.getAndIncrement();
                    return processChunk(chunkContent, index, document);
                }, embeddingExecutorService))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .filter(chunk -> chunk != null)
                .collect(Collectors.toList());

        log.info("[所有向量化任务完成，成功处理 {} 个片段]", allChunks.size());
        return allChunks;
    }

    private DocumentChunk processChunk(String chunkContent, int index, Document document) {
        if (chunkContent.trim().isEmpty()) {
            return null;
        }

        log.info("[开始向量化片段 {}, 内容长度: {}]", index, chunkContent.length());

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

        log.info("[片段 {} 向量化完成]", index);
        return documentChunk;
    }

    private void incrementCounter(String key) {
        try {
            Long result = redisTemplate.opsForValue().increment(key, 1);
            log.debug("[计数器增加: key={}, result={}]", key, result);
        } catch (Exception e) {
            log.error("[计数器增加失败: key={}]", key, e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long documentId) {
        log.info("[开始删除文档: {}]", documentId);

        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));

        log.info("[开始删除文档 {} 的 {} 个切片]", documentId, document.getChunkCount());
        vectorStoreService.deleteChunksByDocumentId(documentId);
        log.info("[切片删除完成]");

        try {
            log.info("[开始从 MinIO 删除文件: {}]", document.getFilePath());
            minioService.deleteFile(document.getFilePath());
            log.info("[MinIO 文件删除完成]");
        } catch (Exception e) {
            log.error("[删除 MinIO 文件失败: {}]", document.getFilePath(), e);
            throw new RuntimeException("删除MinIO文件失败: " + e.getMessage(), e);
        }

        documentRepository.deleteById(documentId);
        log.info("[文档删除完成: {}]", documentId);
    }

    public List<Document> listDocuments() {
        log.info("[获取文档列表]");
        return documentRepository.findAll();
    }

    public Document getDocument(Long documentId) {
        log.info("[获取文档详情: {}]", documentId);
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
                log.warn("[未找到批量上传任务进度: {}]", taskId);
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
            log.error("[获取批量上传进度失败]", e);
            return null;
        }
    }
}
