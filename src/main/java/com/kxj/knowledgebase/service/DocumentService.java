package com.kxj.knowledgebase.service;

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

    private static final String UPLOAD_DIR = "./uploads";
    private static final int BATCH_SIZE = 10;

    @Transactional
    public Document processDocument(MultipartFile file) throws IOException {
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

        log.info("[AI: 文档处理完成: {}]", fileName);
        return document;
    }

    private void processDocumentInBatches(String objectName, Document document) throws IOException {
        String fileType = getFileExtension(objectName);
        
        log.info("[AI: 开始从 MinIO 下载文件: {}]", objectName);
        try (InputStream inputStream = minioService.downloadFile(objectName);
             BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(inputStream))) {
            
            log.info("[AI: 开始流式切分和向量化]");
            
            StringBuilder chunkBuilder = new StringBuilder();
            int chunkIndex = 0;
            int totalChunks = 0;
            List<DocumentChunk> batch = new ArrayList<>(BATCH_SIZE);
            String line;
            int currentLength = 0;

            while ((line = reader.readLine()) != null) {
                chunkBuilder.append(line).append("\n");
                currentLength += line.length() + 1;

                if (currentLength >= 300) {
                    processChunk(chunkBuilder.toString(), chunkIndex++, document, batch);
                    chunkBuilder.setLength(0);
                    currentLength = 0;
                    totalChunks++;

                    if (batch.size() >= BATCH_SIZE) {
                        vectorStoreService.saveChunks(new ArrayList<>(batch));
                        batch.clear();
                        log.info("[AI: 已保存批次，当前总片段数: {}]", totalChunks);
                    }
                }
            }

            if (chunkBuilder.length() > 0) {
                processChunk(chunkBuilder.toString(), chunkIndex++, document, batch);
                totalChunks++;
            }

            if (!batch.isEmpty()) {
                vectorStoreService.saveChunks(batch);
            }

            document.setChunkCount(totalChunks);
            log.info("[AI: 流式处理完成，共生成 {} 个片段]", totalChunks);
        }
    }

    private void processChunk(String chunkContent, int index, Document document, List<DocumentChunk> batch) {
        if (chunkContent.trim().isEmpty()) {
            return;
        }

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

        batch.add(documentChunk);
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        log.info("[AI: 开始删除文档: {}]", documentId);
        
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("文档不存在"));

        vectorStoreService.deleteChunksByDocumentId(documentId);

        try {
            log.info("[AI: 开始从 MinIO 删除文件: {}]", document.getFilePath());
            minioService.deleteFile(document.getFilePath());
        } catch (Exception e) {
            log.error("[AI: 删除 MinIO 文件失败: {}]", document.getFilePath(), e);
        }

        documentRepository.deleteById(documentId);
        log.info("[AI: 文档删除完成: {}]", documentId);
    }

    public List<Document> listDocuments() {
        log.info("[AI: 获取文档列表]");
        return documentRepository.findAll();
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