package com.reportcentre.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.buckets.staging-raw}")
    private String stagingBucket;

    @Value("${minio.buckets.reports-watermarked}")
    private String watermarkedBucket;

    public void uploadRaw(String objectName, InputStream stream, long size, String contentType) {
        upload(stagingBucket, objectName, stream, size, contentType);
    }

    public void uploadWatermarked(String objectName, InputStream stream, long size, String contentType) {
        upload(watermarkedBucket, objectName, stream, size, contentType);
    }

    public InputStream downloadRaw(String objectName) {
        return download(stagingBucket, objectName);
    }

    public InputStream downloadWatermarked(String objectName) {
        return download(watermarkedBucket, objectName);
    }

    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(watermarkedBucket)
                            .object(objectName)
                            .expiry(5, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for {}", objectName, e);
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    private void upload(String bucket, String objectName, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
            log.info("Uploaded {} to bucket {}", objectName, bucket);
        } catch (Exception e) {
            log.error("Failed to upload {} to bucket {}", objectName, bucket, e);
            throw new RuntimeException("File upload failed", e);
        }
    }

    private InputStream download(String bucket, String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("Failed to download {} from bucket {}", objectName, bucket, e);
            throw new RuntimeException("File download failed", e);
        }
    }
}
