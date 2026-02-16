package com.kxj.knowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadProgress {
    private String taskId;
    private int totalDocuments;
    private int completedDocuments;
    private int failedDocuments;
    private double progress;
    private String status;
}