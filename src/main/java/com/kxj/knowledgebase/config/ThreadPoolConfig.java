package com.kxj.knowledgebase.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class ThreadPoolConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService embeddingExecutorService() {
        log.info("[AI: 初始化向量化线程池，使用虚拟线程]");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
