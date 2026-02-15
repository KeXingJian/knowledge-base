package com.kxj.knowledgebase.entity;

import com.kxj.knowledgebase.config.VectorType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_chunk", indexes = {
    @Index(name = "idx_document_id", columnList = "document_id"),
    @Index(name = "idx_chunk_index", columnList = "chunk_index")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "vector(768)")
    @Type(VectorType.class)
    private String embedding;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, length = 500)
    private String metadata;

    @Column(nullable = false)
    private Integer tokenCount;
}