package com.kxj.knowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResult {
    private String fileName;
    private Long documentId;
    private boolean success;
    private String message;
    private int chunkCount;
    private long processingTime;
}