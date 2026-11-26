package com.sweethomecompany.crm.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Thin wrapper around the injected {@link S3Client}, pointed at MinIO.
 * Returns direct public URLs (not presigned ones) - the bucket is
 * created public-read by {@link MinioBucketInitializer}, matching the
 * Phase 3 design doc's decision that `property_media.variants` stores
 * permanent, non-expiring URLs.
 */
@Service
public class ObjectStorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public ObjectStorageService(
            S3Client s3Client,
            @Value("${storage.s3.bucket}") String bucket,
            @Value("${storage.s3.public-base-url}") String publicBaseUrl) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    public String upload(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
        return publicBaseUrl + "/" + key;
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }
}
