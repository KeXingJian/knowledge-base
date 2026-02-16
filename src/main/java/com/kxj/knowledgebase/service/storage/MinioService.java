package com.kxj.knowledgebase.service.storage;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;


@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final com.kxj.knowledgebase.config.MinioProperties minioProperties;

    @PostConstruct
    public void init() {
        try {
            log.info("[AI: 检查并创建 MinIO bucket: {}]", minioProperties.getBucketName());
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .build()
            );
            
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(minioProperties.getBucketName())
                                .build()
                );
                log.info("[AI: MinIO bucket 创建成功: {}]", minioProperties.getBucketName());
            } else {
                log.info("[AI: MinIO bucket 已存在: {}]", minioProperties.getBucketName());
            }
        } catch (Exception e) {
            log.error("[AI: 初始化 MinIO bucket 失败]", e);
            throw new RuntimeException("初始化 MinIO bucket 失败", e);
        }
    }

    public void uploadFile(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            log.info("[AI: 开始上传文件到 MinIO: {}, size: {}]", objectName, size);
            
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            
            log.info("[AI: 文件上传成功: {}]", objectName);
        } catch (Exception e) {
            log.error("[AI: 文件上传失败: {}]", objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    public InputStream downloadFile(String objectName) {
        try {
            log.info("[AI: 开始从 MinIO 下载文件: {}]", objectName);
            
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("[AI: 文件下载失败: {}]", objectName, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    public void deleteFile(String objectName) {
        try {
            log.info("[AI: 开始从 MinIO 删除文件: {}]", objectName);
            
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
            
            log.info("[AI: 文件删除成功: {}]", objectName);
        } catch (Exception e) {
            log.error("[AI: 文件删除失败: {}]", objectName, e);
            throw new RuntimeException("文件删除失败", e);
        }
    }

}
