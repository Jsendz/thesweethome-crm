package com.sweethomecompany.crm.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local MinIO started by docker-compose.yml, the
 * same pattern the project already uses for Postgres - no Testcontainers.
 * Requires `docker compose up -d minio` to be running first.
 */
@SpringBootTest
class ObjectStorageServiceTest {

    @Autowired
    private ObjectStorageService objectStorageService;

    @Test
    void uploadReturnsAPubliclyFetchableUrlAndDeleteRemovesIt() throws Exception {
        String key = "test/" + UUID.randomUUID() + ".txt";
        HttpClient http = HttpClient.newHttpClient();

        String url = objectStorageService.upload(key, "hello".getBytes(), "text/plain");

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("hello");

        objectStorageService.delete(key);

        HttpResponse<String> afterDelete = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(afterDelete.statusCode()).isEqualTo(404);
    }
}
