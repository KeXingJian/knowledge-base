package com.kxj.knowledgebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "retrieval.search")
public class RetrievalProperties {
    private int topK = 3;
    private double vectorWeight = 0.6;
    private double textWeight = 0.4;
    private boolean enableFulltextSearch = true;
    private boolean fallbackToKeywordSearch = true;
}
