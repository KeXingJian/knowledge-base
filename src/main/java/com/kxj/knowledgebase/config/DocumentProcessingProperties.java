package com.kxj.knowledgebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "document.processing")
public class DocumentProcessingProperties {
    private int chunkSize = 300;
    private int batchSize = 10;
    private boolean enableParallelProcessing = true;
}
