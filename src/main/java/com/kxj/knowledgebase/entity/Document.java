package com.kxj.knowledgebase.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "document")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String fileType;

    @Column(nullable = false, length = 500)
    private String filePath;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 1000)
    private String fileHash;

    @Column(nullable = false)
    private Integer chunkCount;

    @Column(nullable = false)
    private LocalDateTime uploadTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;

    @Column(nullable = false)
    private Boolean processed;

    @Column(length = 500)
    private String errorMessage;
}