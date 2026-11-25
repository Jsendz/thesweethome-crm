package com.sweethomecompany.crm.property;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
// Explicit import required: this package declares its own MediaType enum,
// which would otherwise shadow Spring's MediaType used below - a
// same-package type always wins over a wildcard import.
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres and MinIO the project already uses
 * for its other integration tests (ObjectStorageServiceTest,
 * PropertyControllerTest) - no Testcontainers/mocking. Requires
 * `docker compose up -d minio` and the usual local Postgres to be running.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PropertyMediaControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private PropertyTranslationRepository propertyTranslationRepository;

    @Autowired
    private PropertyMediaRepository propertyMediaRepository;

    @Autowired
    private ImagePipelineService imagePipelineService;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.default-agency-id}")
    private UUID defaultAgencyId;

    @Value("${storage.s3.bucket}")
    private String bucket;

    // Same reasoning as PropertyControllerTest: real HTTP calls off-thread,
    // so @Transactional rollback doesn't apply - clean up by reference code.
    private static final List<String> TEST_REFERENCE_CODES = List.of(
            "PM-UPLOAD-OK", "PM-BAD-FILE", "PM-VIDEO", "PM-TOO-MANY", "PM-TOO-BIG",
            "PM-PARTIAL-FAIL", "PM-DELETE", "PM-REORDER", "PM-REORDER-BAD",
            "PM-REORDER-DUP", "PM-REORDER-MISSING");

    @BeforeEach
    @AfterEach
    void cleanUpTestProperties() {
        TEST_REFERENCE_CODES.forEach(code ->
                propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, code).ifPresent(property -> {
                    propertyMediaRepository.deleteAll(
                            propertyMediaRepository.findByPropertyIdOrderBySortOrder(property.getId()));
                    propertyTranslationRepository.deleteAll(
                            propertyTranslationRepository.findByPropertyId(property.getId()));
                    propertyRepository.delete(property);
                }));
    }

    private String extractCookie(ResponseEntity<?> response, String name) {
        for (String cookie : response.getHeaders().get(HttpHeaders.SET_COOKIE)) {
            if (cookie.startsWith(name + "=")) {
                return cookie.split(";")[0].substring(name.length() + 1);
            }
        }
        throw new AssertionError(name + " cookie not found");
    }

    private record Session(String csrfToken, String sessionCookie) {
    }

    private Session loginAsAdmin() {
        ResponseEntity<String> csrfResponse = restTemplate.getForEntity("/api/auth/me", String.class);
        String csrfToken = extractCookie(csrfResponse, "XSRF-TOKEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("email", "admin@example.com");
        body.add("password", "change-me-now");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(body, headers), String.class);
        return new Session(csrfToken, extractCookie(response, "SESSION"));
    }

    private HttpHeaders authedHeaders(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", session.csrfToken());
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + session.csrfToken() + "; SESSION=" + session.sessionCookie());
        return headers;
    }

    // No Content-Type set here: when the body map contains a Resource value,
    // RestTemplate's FormHttpMessageConverter switches to multipart/form-data
    // and generates its own boundary automatically.
    private HttpHeaders multipartAuthedHeaders(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-XSRF-TOKEN", session.csrfToken());
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + session.csrfToken() + "; SESSION=" + session.sessionCookie());
        return headers;
    }

    private static Resource fileResource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private byte[] realPhotoBytes() throws Exception {
        try (var in = getClass().getResourceAsStream("/test-photo.jpg")) {
            return in.readAllBytes();
        }
    }

    private UUID createProperty(Session admin, String referenceCode) {
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"%s","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Test property"}""".formatted(referenceCode),
                authedHeaders(admin)), String.class);
        return propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, referenceCode)
                .orElseThrow().getId();
    }

    private ResponseEntity<String> upload(Session admin, UUID propertyId, String mediaType, MultiValueMap<String, Object> files) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>(files);
        return restTemplate.postForEntity(
                "/api/properties/" + propertyId + "/media?mediaType=" + mediaType,
                new HttpEntity<>(body, multipartAuthedHeaders(admin)),
                String.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseMediaList(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
        });
    }

    private void deleteMedia(Session admin, UUID propertyId, UUID mediaId) {
        restTemplate.exchange(
                "/api/properties/" + propertyId + "/media/" + mediaId, HttpMethod.DELETE,
                new HttpEntity<>(authedHeaders(admin)), String.class);
    }

    @Test
    void uploadingRealPhotosCreatesOneMediaRowPerPhotoWithAllExpectedVariantKeys() throws Exception {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-UPLOAD-OK");
        byte[] photo = realPhotoBytes();

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("files", fileResource(photo, "photo1.jpg"));
        files.add("files", fileResource(photo, "photo2.jpg"));

        ResponseEntity<String> response = upload(admin, propertyId, "PHOTO", files);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<Map<String, Object>> media = parseMediaList(response.getBody());
        assertThat(media).hasSize(2);

        Set<String> expectedJpegKeys = Set.of("thumbnail_jpeg", "card_jpeg", "gallery_jpeg", "full_detail_jpeg");
        String firstVariantUrl = null;
        for (Map<String, Object> entry : media) {
            @SuppressWarnings("unchecked")
            Map<String, String> variants = (Map<String, String>) entry.get("variants");
            assertThat(variants.keySet()).containsAll(expectedJpegKeys);
            if (imagePipelineService.isWebpAvailable()) {
                assertThat(variants.keySet()).containsAll(
                        Set.of("thumbnail_webp", "card_webp", "gallery_webp", "full_detail_webp"));
            }
            if (firstVariantUrl == null) {
                firstVariantUrl = variants.get("thumbnail_jpeg");
            }
        }

        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> fetched = http.send(
                HttpRequest.newBuilder(URI.create(firstVariantUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(fetched.statusCode()).isEqualTo(200);

        for (Map<String, Object> entry : media) {
            deleteMedia(admin, propertyId, UUID.fromString((String) entry.get("id")));
        }
    }

    @Test
    void uploadingANonImageFileReturns400AndWritesNothing() {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-BAD-FILE");

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("files", fileResource("this is not an image".getBytes(), "notes.txt"));

        ResponseEntity<String> response = upload(admin, propertyId, "PHOTO", files);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(propertyMediaRepository.findByPropertyIdOrderBySortOrder(propertyId)).isEmpty();
    }

    @Test
    void uploadingWithMediaTypeVideoReturns400() throws Exception {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-VIDEO");

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("files", fileResource(realPhotoBytes(), "photo.jpg"));

        ResponseEntity<String> response = upload(admin, propertyId, "VIDEO", files);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(propertyMediaRepository.findByPropertyIdOrderBySortOrder(propertyId)).isEmpty();
    }

    @Test
    void uploadingMoreThanThirtyFilesInOneRequestReturns400() throws Exception {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-TOO-MANY");
        byte[] photo = realPhotoBytes();

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        for (int i = 0; i < 31; i++) {
            files.add("files", fileResource(photo, "photo" + i + ".jpg"));
        }

        ResponseEntity<String> response = upload(admin, propertyId, "PHOTO", files);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(propertyMediaRepository.findByPropertyIdOrderBySortOrder(propertyId)).isEmpty();
    }

    // Final-review finding (Review Focus gap): nothing previously exercised
    // the per-file 20MB limit at all. Also exercises the related fix to
    // application.yml's spring.servlet.multipart.max-file-size, which used
    // to equal this controller's own cap exactly - meaning Spring's filter
    // rejected an oversized part with its own 413 before this 400 check
    // ever ran.
    @Test
    void uploadingAFileOverTwentyMegabytesReturns400() {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-TOO-BIG");
        byte[] tooLarge = new byte[21 * 1024 * 1024];

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("files", fileResource(tooLarge, "huge.jpg"));

        ResponseEntity<String> response = upload(admin, propertyId, "PHOTO", files);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(propertyMediaRepository.findByPropertyIdOrderBySortOrder(propertyId)).isEmpty();
    }

    @Test
    void whenOneFileInABatchFailsNoMediaRowsAreCreatedAndAnyAlreadyUploadedObjectIsDeleted() throws Exception {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-PARTIAL-FAIL");

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("files", fileResource(realPhotoBytes(), "valid.jpg"));
        files.add("files", fileResource("corrupt, not a real image".getBytes(), "corrupt.jpg"));

        ResponseEntity<String> response = upload(admin, propertyId, "PHOTO", files);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(propertyMediaRepository.findByPropertyIdOrderBySortOrder(propertyId)).isEmpty();

        // valid.jpg's variants were uploaded to MinIO before corrupt.jpg failed
        // processing; the controller must have deleted them again rather than
        // leaving orphaned objects under this property's prefix.
        var listing = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix("properties/" + propertyId + "/")
                .build());
        assertThat(listing.contents()).isEmpty();
    }

    @Test
    void deletingAMediaRowRemovesItAndItsUnderlyingObjects() throws Exception {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-DELETE");

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("files", fileResource(realPhotoBytes(), "photo.jpg"));
        ResponseEntity<String> uploadResponse = upload(admin, propertyId, "PHOTO", files);
        List<Map<String, Object>> media = parseMediaList(uploadResponse.getBody());
        UUID mediaId = UUID.fromString((String) media.get(0).get("id"));
        @SuppressWarnings("unchecked")
        Map<String, String> variants = (Map<String, String>) media.get(0).get("variants");
        String someVariantUrl = variants.get("thumbnail_jpeg");

        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> beforeDelete = http.send(
                HttpRequest.newBuilder(URI.create(someVariantUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(beforeDelete.statusCode()).isEqualTo(200);

        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                "/api/properties/" + propertyId + "/media/" + mediaId, HttpMethod.DELETE,
                new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(propertyMediaRepository.findById(mediaId)).isEmpty();

        HttpResponse<String> afterDelete = http.send(
                HttpRequest.newBuilder(URI.create(someVariantUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
        // Final-review fix: the bucket policy grants only s3:GetObject, not
        // s3:ListBucket, so MinIO can legitimately answer a missing key with
        // 403 AccessDenied instead of 404 (same reasoning as
        // ObjectStorageServiceTest).
        assertThat(afterDelete.statusCode()).isIn(403, 404);
    }

    @Test
    void reorderingWithTheFullCurrentSetOfMediaIdsUpdatesSortOrderToMatch() throws Exception {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-REORDER");
        byte[] photo = realPhotoBytes();

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("files", fileResource(photo, "a.jpg"));
        files.add("files", fileResource(photo, "b.jpg"));
        ResponseEntity<String> uploadResponse = upload(admin, propertyId, "PHOTO", files);
        List<Map<String, Object>> media = parseMediaList(uploadResponse.getBody());
        UUID firstId = UUID.fromString((String) media.get(0).get("id"));
        UUID secondId = UUID.fromString((String) media.get(1).get("id"));

        String orderJson = objectMapper.writeValueAsString(Map.of("mediaIdsInOrder", List.of(secondId, firstId)));
        ResponseEntity<String> reorderResponse = restTemplate.exchange(
                "/api/properties/" + propertyId + "/media/order", HttpMethod.PATCH,
                new HttpEntity<>(orderJson, authedHeaders(admin)), String.class);

        assertThat(reorderResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        List<PropertyMedia> reordered = propertyMediaRepository.findByPropertyIdOrderBySortOrder(propertyId);
        assertThat(reordered).hasSize(2);
        assertThat(reordered.get(0).getId()).isEqualTo(secondId);
        assertThat(reordered.get(1).getId()).isEqualTo(firstId);

        deleteMedia(admin, propertyId, firstId);
        deleteMedia(admin, propertyId, secondId);
    }

    @Test
    void reorderingWithAMissingOrUnknownMediaIdReturns400() throws Exception {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-REORDER-BAD");

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("files", fileResource(realPhotoBytes(), "a.jpg"));
        ResponseEntity<String> uploadResponse = upload(admin, propertyId, "PHOTO", files);
        List<Map<String, Object>> media = parseMediaList(uploadResponse.getBody());
        UUID onlyId = UUID.fromString((String) media.get(0).get("id"));

        String orderJson = objectMapper.writeValueAsString(
                Map.of("mediaIdsInOrder", List.of(onlyId, UUID.randomUUID())));
        ResponseEntity<String> reorderResponse = restTemplate.exchange(
                "/api/properties/" + propertyId + "/media/order", HttpMethod.PATCH,
                new HttpEntity<>(orderJson, authedHeaders(admin)), String.class);

        assertThat(reorderResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        deleteMedia(admin, propertyId, onlyId);
    }

    // Final-review finding (Review Focus gap): the existing reorder-rejection
    // test only covered "a stray unknown id plus the wrong list size" -
    // a duplicate of an existing id (same size as the real set, but not a
    // valid permutation of it) was never exercised on its own.
    @Test
    void reorderingWithADuplicateMediaIdReturns400() throws Exception {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-REORDER-DUP");
        byte[] photo = realPhotoBytes();

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("files", fileResource(photo, "a.jpg"));
        files.add("files", fileResource(photo, "b.jpg"));
        ResponseEntity<String> uploadResponse = upload(admin, propertyId, "PHOTO", files);
        List<Map<String, Object>> media = parseMediaList(uploadResponse.getBody());
        UUID firstId = UUID.fromString((String) media.get(0).get("id"));
        UUID secondId = UUID.fromString((String) media.get(1).get("id"));

        String orderJson = objectMapper.writeValueAsString(Map.of("mediaIdsInOrder", List.of(firstId, firstId)));
        ResponseEntity<String> reorderResponse = restTemplate.exchange(
                "/api/properties/" + propertyId + "/media/order", HttpMethod.PATCH,
                new HttpEntity<>(orderJson, authedHeaders(admin)), String.class);

        assertThat(reorderResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        deleteMedia(admin, propertyId, firstId);
        deleteMedia(admin, propertyId, secondId);
    }

    // Final-review finding (Review Focus gap): a list that's a strict subset
    // of the property's current media ids (missing one, no stray id) was
    // never exercised on its own either.
    @Test
    void reorderingWithAMissingMediaIdReturns400() throws Exception {
        Session admin = loginAsAdmin();
        UUID propertyId = createProperty(admin, "PM-REORDER-MISSING");
        byte[] photo = realPhotoBytes();

        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("files", fileResource(photo, "a.jpg"));
        files.add("files", fileResource(photo, "b.jpg"));
        ResponseEntity<String> uploadResponse = upload(admin, propertyId, "PHOTO", files);
        List<Map<String, Object>> media = parseMediaList(uploadResponse.getBody());
        UUID firstId = UUID.fromString((String) media.get(0).get("id"));
        UUID secondId = UUID.fromString((String) media.get(1).get("id"));

        String orderJson = objectMapper.writeValueAsString(Map.of("mediaIdsInOrder", List.of(firstId)));
        ResponseEntity<String> reorderResponse = restTemplate.exchange(
                "/api/properties/" + propertyId + "/media/order", HttpMethod.PATCH,
                new HttpEntity<>(orderJson, authedHeaders(admin)), String.class);

        assertThat(reorderResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        deleteMedia(admin, propertyId, firstId);
        deleteMedia(admin, propertyId, secondId);
    }
}
