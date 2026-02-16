package com.kxj.knowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadResponse {
    private String taskId;
    private int totalDocuments;
    private int successCount;
    private int failureCount;
    private long totalProcessingTime;
    private List<DocumentUploadResult> results;
}