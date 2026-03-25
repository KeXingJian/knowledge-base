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
    @Index(name = "idx_chunk_index", columnList = "chunk_index"),
    @Index(name = "idx_section_title", columnList = "section_title"),
    @Index(name = "idx_page_number", columnList = "page_number")
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

    @Column(nullable = false, length = 2000)
    private String metadata;

    @Column(nullable = false)
    private Integer tokenCount;

    // ========== 增强元数据字段 ==========

    @Column(name = "section_title", length = 500)
    private String sectionTitle;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "page_range", length = 50)
    private String pageRange;

    @Column(name = "headings_path", length = 1000)
    private String headingsPath;

    @Column(name = "content_type", length = 20)
    private String contentType;

    @Column(name = "summary", length = 1000)
    private String summary;

    @Column(name = "prev_chunk_id")
    private Long prevChunkId;

    @Column(name = "next_chunk_id")
    private Long nextChunkId;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    // ========== 父文档检索字段 ==========

    @Column(name = "parent_chunk_id")
    private Long parentChunkId;       // 父块ID（子块才有）

    @Column(name = "chunk_level")
    private Integer chunkLevel;       // 0=父块, 1=子块

    @Column(name = "sub_chunk_count")
    private Integer subChunkCount;    // 父块：包含多少子块
}