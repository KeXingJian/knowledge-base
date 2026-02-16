package com.kxj.knowledgebase.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Slf4j
@Configuration
public class ThreadPoolConfig {

    @Value("${document.processing.max-concurrent-documents:5}")
    private int maxConcurrentDocuments;

    @Value("${document.processing.max-concurrent-chunks:20}")
    private int maxConcurrentChunks;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService embeddingExecutorService() {
        log.info("[AI: 初始化向量化线程池，使用虚拟线程，最大并发片段数: {}]", maxConcurrentChunks);
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService documentProcessingExecutorService() {
        log.info("[AI: 初始化文档处理线程池，使用虚拟线程，最大并发文档数: {}]", maxConcurrentDocuments);
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public Semaphore documentUploadSemaphore() {
        log.info("[AI: 初始化文档上传信号量，最大并发数: {}]", maxConcurrentDocuments);
        return new Semaphore(maxConcurrentDocuments);
    }

    @Bean
    public Semaphore chunkProcessingSemaphore() {
        log.info("[AI: 初始化片段处理信号量，最大并发数: {}]", maxConcurrentChunks);
        return new Semaphore(maxConcurrentChunks);
    }
}
