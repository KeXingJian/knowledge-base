package com.kxj.knowledgebase.service;

import com.kxj.knowledgebase.config.DocumentProcessingProperties;
import com.kxj.knowledgebase.constants.CacheConstants;
import com.kxj.knowledgebase.dto.BatchUploadProgress;
import com.kxj.knowledgebase.entity.Document;
import com.kxj.knowledgebase.entity.DocumentChunk;
import com.kxj.knowledgebase.repository.DocumentRepository;
import com.kxj.knowledgebase.service.cache.CacheInvalidationService;
import com.kxj.knowledgebase.service.embedding.EmbeddingService;
import com.kxj.knowledgebase.service.parser.DocumentParserFactory;
import com.kxj.knowledgebase.service.parser.ParseResult;
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

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final VectorStoreService vectorStoreService;
    private final MinioService minioService;
    private final ExecutorService embeddingExecutorService;
    private final DocumentProcessingProperties documentProcessingProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Semaphore globalSemaphore;
    private final CacheInvalidationService cacheInvalidationService;
    private final DocumentParserFactory parserFactory;
    private final HierarchicalChunkService hierarchicalChunkService;

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
            // 清除问答缓存，确保新上传的文档能被检索到
            cacheInvalidationService.onBatchProcessingCompleted();
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

        try (InputStream inputStream = minioService.downloadFile(objectName)) {

            log.info("[开始分层切分文档：父块+子块结构]");

            // 使用解析器提取文本内容
            String fileType = document.getFileType();
            var parser = parserFactory.getParser(fileType);
            ParseResult parseResult = parser.parse(inputStream, document.getFileName());

            if (!parseResult.isSuccess()) {
                throw new IOException("文档解析失败: " + parseResult.getErrorMessage());
            }

            // 使用分层切分服务创建父块+子块结构
            List<DocumentChunk> allChunks = hierarchicalChunkService.createHierarchicalChunks(
                    parseResult,
                    document
            );

            if (allChunks.isEmpty()) {
                log.warn("[文档内容为空: {}]", document.getFileName());
                document.setChunkCount(0);
                return;
            }

            // 统计父子块数量
            long parentCount = allChunks.stream().filter(c -> c.getChunkLevel() == 0).count();
            long childCount = allChunks.stream().filter(c -> c.getChunkLevel() == 1).count();

            // 验证 document_id 一致性
            long distinctDocIds = allChunks.stream().map(DocumentChunk::getDocumentId).distinct().count();
            Long firstDocId = allChunks.isEmpty() ? null : allChunks.get(0).getDocumentId();
            log.info("[文档切分完成: {} 个父块, {} 个子块, documentId={}, 一致性检查: {} 个不同值]",
                    parentCount, childCount, firstDocId, distinctDocIds);

            if (distinctDocIds != 1) {
                log.error("[严重错误：document_id 不一致！发现 {} 个不同的值]", distinctDocIds);
                allChunks.stream()
                        .collect(Collectors.groupingBy(DocumentChunk::getDocumentId, Collectors.counting()))
                        .forEach((docId, count) -> log.error("  document_id={}: {} 个chunks", docId, count));
            }

            // 分批保存所有 chunks
            int batchSize = documentProcessingProperties.getBatchSize();
            for (int i = 0; i < allChunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allChunks.size());
                List<DocumentChunk> batch = allChunks.subList(i, end);

                // 验证批次内的 document_id
                Set<Long> batchDocIds = batch.stream().map(DocumentChunk::getDocumentId).collect(Collectors.toSet());
                if (batchDocIds.size() > 1) {
                    log.error("[批次 {} 包含多个 document_id: {}]", (i / batchSize) + 1, batchDocIds);
                }

                vectorStoreService.saveChunks(new ArrayList<>(batch));
                log.info("[已保存批次 {}/{}, 片段数: {}]",
                    (i / batchSize) + 1,
                    (allChunks.size() + batchSize - 1) / batchSize,
                    batch.size());
            }

            // 更新关联 ID（parent_chunk_id, prev/next chunk_id）
            updateChunkRelationships(allChunks);

            document.setChunkCount((int) childCount); // 文档的 chunkCount 记录子块数（可检索的）
            log.info("[文档处理完成: {} 个父块, {} 个子块]", parentCount, childCount);
        }
    }


    /**
     * 更新 chunks 之间的关联关系
     * - 更新子块的 parent_chunk_id（从临时ID更新为真实ID）
     * - 更新相邻子块的 prev/next chunk_id
     */
    private void updateChunkRelationships(List<DocumentChunk> allChunks) {
        log.info("[开始更新 chunks 关联关系]");

        // 1. 建立临时索引到真实 chunk 的映射
        // 父块使用临时负ID：-1, -2, -3...
        Map<Long, Long> tempIdToRealId = new HashMap<>();

        int parentIndex = 0;
        for (DocumentChunk chunk : allChunks) {
            if (chunk.getChunkLevel() == 0) {
                // 父块：使用临时负ID
                long tempId = -1L * (parentIndex + 1);
                tempIdToRealId.put(tempId, chunk.getId());
                parentIndex++;
            }
        }

        // 2. 按临时父块ID分组（必须在更新parentChunkId之前做！）
        Map<Long, List<DocumentChunk>> parentToChildren = allChunks.stream()
                .filter(c -> c.getChunkLevel() == 1 && c.getParentChunkId() != null && c.getParentChunkId() < 0)
                .collect(Collectors.groupingBy(DocumentChunk::getParentChunkId));

        log.info("[父块分组完成] {} 个父块有子块", parentToChildren.size());

        // 3. 更新子块的 parent_chunk_id（从临时ID更新为真实ID）
        List<DocumentChunk> chunksToUpdate = new ArrayList<>();
        for (DocumentChunk chunk : allChunks) {
            if (chunk.getChunkLevel() == 1 && chunk.getParentChunkId() != null) {
                Long tempParentId = chunk.getParentChunkId();
                if (tempParentId < 0) {
                    Long realParentId = tempIdToRealId.get(tempParentId);
                    if (realParentId != null) {
                        chunk.setParentChunkId(realParentId);
                        chunksToUpdate.add(chunk);
                    } else {
                        log.warn("[未找到临时父块ID的映射: tempId={}]", tempParentId);
                    }
                }
            }
        }

        // 4. 按父块分组，设置子块之间的 prev/next 关系
        for (Map.Entry<Long, List<DocumentChunk>> entry : parentToChildren.entrySet()) {
            Long tempParentId = entry.getKey();
            List<DocumentChunk> children = entry.getValue();
            Long realParentId = tempIdToRealId.get(tempParentId);

            if (realParentId == null) {
                log.warn("[跳过 prev/next 设置: 临时父块ID {} 未找到映射]", tempParentId);
                continue;
            }

            // 按 chunkIndex 排序
            children.sort(Comparator.comparingInt(DocumentChunk::getChunkIndex));

            log.debug("[父块 {}] 设置 {} 个子块的 prev/next 关系", realParentId, children.size());

            for (int i = 0; i < children.size(); i++) {
                DocumentChunk current = children.get(i);
                if (i > 0) {
                    current.setPrevChunkId(children.get(i - 1).getId());
                }
                if (i < children.size() - 1) {
                    current.setNextChunkId(children.get(i + 1).getId());
                }
                if (!chunksToUpdate.contains(current)) {
                    chunksToUpdate.add(current);
                }
            }
        }

        // 5. 批量保存更新
        if (!chunksToUpdate.isEmpty()) {
            vectorStoreService.saveChunks(chunksToUpdate);
            log.info("[关联关系更新完成: {} 个 chunks 已更新]", chunksToUpdate.size());
        }
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

        // 清除问答缓存，避免返回已删除文档的内容
        cacheInvalidationService.onDocumentDeleted();
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
