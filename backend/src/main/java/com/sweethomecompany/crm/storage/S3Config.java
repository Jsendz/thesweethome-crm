package com.sweethomecompany.crm.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Wires the AWS S3 SDK's client at MinIO's S3-compatible endpoint rather
 * than real AWS - see the Phase 3 design doc for why the AWS SDK was
 * chosen over MinIO's own client.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${storage.s3.endpoint}") String endpoint,
            @Value("${storage.s3.access-key}") String accessKey,
            @Value("${storage.s3.secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                // MinIO ignores the region, but the SDK requires one to be set.
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                // Required for MinIO: it doesn't support virtual-hosted-style
                // bucket addressing (bucket.endpoint.com) the way real S3 does.
                .forcePathStyle(true)
                .build();
    }
}
