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
    private int maxConcurrentDocuments = 5;
    private int maxConcurrentChunks = 20;
    /** 块间重叠比例 (0.0-1.0)，默认0.2表示20%重叠 */
    private double overlapRatio = 0.2;
}
