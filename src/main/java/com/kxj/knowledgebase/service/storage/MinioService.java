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
            log.info("[检查并创建 MinIO bucket: {}]", minioProperties.getBucketName());
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
                log.info("[MinIO bucket 创建成功: {}]", minioProperties.getBucketName());
            } else {
                log.info("[MinIO bucket 已存在: {}]", minioProperties.getBucketName());
            }
        } catch (Exception e) {
            log.error("[初始化 MinIO bucket 失败]", e);
            throw new RuntimeException("初始化 MinIO bucket 失败", e);
        }
    }

    public void uploadFile(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            log.info("[开始上传文件到 MinIO: {}, size: {}]", objectName, size);
            
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            
            log.info("[文件上传成功: {}]", objectName);
        } catch (Exception e) {
            log.error("[文件上传失败: {}]", objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    public InputStream downloadFile(String objectName) {
        try {
            log.info("[开始从 MinIO 下载文件: {}]", objectName);
            
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("[文件下载失败: {}]", objectName, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    public void deleteFile(String objectName) {
        try {
            log.info("[开始从 MinIO 删除文件: {}]", objectName);
            
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
            
            log.info("[文件删除成功: {}]", objectName);
        } catch (Exception e) {
            log.error("[文件删除失败: {}]", objectName, e);
            throw new RuntimeException("文件删除失败", e);
        }
    }

    public String getPresignedUrl(String objectName, int expires) {
        try {
            log.info("[生成预签名URL: {}, 过期时间: {}秒]", objectName, expires);
            
            MinioClient client = minioClient;
            if (minioProperties.getPublicEndpoint() != null) {
                log.info("[使用公网地址生成预签名URL: {}]", minioProperties.getPublicEndpoint());
                client = MinioClient.builder()
                        .endpoint(minioProperties.getPublicEndpoint())
                        .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                        .build();
            }
            
            return client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .method(io.minio.http.Method.GET)
                            .expiry(expires)
                            .build()
            );
        } catch (Exception e) {
            log.error("[生成预签名URL失败: {}]", objectName, e);
            throw new RuntimeException("生成预签名URL失败", e);
        }
    }

}
