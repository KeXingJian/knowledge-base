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
public class ThreadPoolConfigOptimized {

    @Value("${document.processing.max-concurrent-chunks:20}")
    private int maxConcurrentChunks;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService optimizedExecutorService() {
        log.info("[初始化优化后的线程池，使用虚拟线程，最大并发数: {}]", maxConcurrentChunks);
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public Semaphore globalSemaphore() {
        log.info("[初始化全局信号量，最大并发数: {}]", maxConcurrentChunks);
        return new Semaphore(maxConcurrentChunks);
    }
}
