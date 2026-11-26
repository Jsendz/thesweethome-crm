package com.sweethomecompany.crm.property;

import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogService;
import com.sweethomecompany.crm.security.AppUserPrincipal;
import com.sweethomecompany.crm.storage.ObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bulk photo/floorplan upload, reordering, and deletion for a property's
 * media. Pulled out of {@link PropertyController} (which already owns the
 * property CRUD endpoints) since this upload flow - multipart handling,
 * per-file image processing, per-variant object storage, and
 * cleanup-on-partial-failure - is a distinct enough responsibility to earn
 * its own file, per the Task 4 brief's "your call."
 *
 * <p>No role restriction here either, same as {@link PropertyController} -
 * Phase 3 has no role gating (design doc decision).
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/media")
public class PropertyMediaController {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Set.of(MediaType.PHOTO, MediaType.FLOORPLAN);
    private static final int MAX_FILES_PER_UPLOAD = 30;
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private final PropertyRepository propertyRepository;
    private final PropertyMediaRepository propertyMediaRepository;
    private final ImagePipelineService imagePipelineService;
    private final ObjectStorageService objectStorageService;
    private final AuditLogService auditLogService;
    private final UUID defaultAgencyId;

    public PropertyMediaController(
            PropertyRepository propertyRepository,
            PropertyMediaRepository propertyMediaRepository,
            ImagePipelineService imagePipelineService,
            ObjectStorageService objectStorageService,
            AuditLogService auditLogService,
            @Value("${app.default-agency-id}") UUID defaultAgencyId) {
        this.propertyRepository = propertyRepository;
        this.propertyMediaRepository = propertyMediaRepository;
        this.imagePipelineService = imagePipelineService;
        this.objectStorageService = objectStorageService;
        this.auditLogService = auditLogService;
        this.defaultAgencyId = defaultAgencyId;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<List<MediaResponse>> upload(
            @PathVariable UUID propertyId,
            @RequestParam MediaType mediaType,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mediaType must be PHOTO or FLOORPLAN");
        }
        if (files == null || files.isEmpty() || files.size() > MAX_FILES_PER_UPLOAD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Must upload between 1 and 30 files");
        }
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "File " + file.getOriginalFilename() + " exceeds the 20MB limit");
            }
        }

        List<PropertyMedia> existing = propertyMediaRepository.findByPropertyIdOrderBySortOrder(propertyId);
        int nextSortOrder = existing.stream().mapToInt(PropertyMedia::getSortOrder).max().orElse(-1) + 1;

        List<String> uploadedKeys = new ArrayList<>();
        List<PropertyMedia> newMedia = new ArrayList<>();

        try {
            int sortOrder = nextSortOrder;
            for (MultipartFile file : files) {
                List<ImagePipelineService.Variant> variants;
                try {
                    variants = imagePipelineService.process(file.getBytes());
                } catch (IOException e) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "File " + file.getOriginalFilename() + " is not a decodable image");
                }

                UUID mediaId = UUID.randomUUID();
                Map<String, String> urls = new LinkedHashMap<>();
                for (ImagePipelineService.Variant variant : variants) {
                    String extension = "jpeg".equals(variant.format()) ? "jpg" : variant.format();
                    String key = "properties/%s/%s/%s_%s.%s".formatted(
                            propertyId, mediaId, variant.size(), variant.format(), extension);
                    String url = objectStorageService.upload(key, variant.bytes(), "image/" + variant.format());
                    uploadedKeys.add(key);
                    urls.put(variant.size() + "_" + variant.format(), url);
                }

                PropertyMedia media = new PropertyMedia();
                media.setId(mediaId);
                media.setPropertyId(propertyId);
                media.setMediaType(mediaType);
                media.setVariants(urls);
                media.setSortOrder(sortOrder++);
                media.setUploadedAt(Instant.now());
                newMedia.add(media);
            }
        } catch (RuntimeException e) {
            // Broadened from catching only ResponseStatusException: an MinIO
            // hiccup mid-batch (S3Exception/SdkClientException) or any other
            // unexpected failure must still trigger cleanup of whatever this
            // request already uploaded, not just the "bad file" case. Each
            // delete is best-effort so one failed cleanup doesn't stop the
            // rest, or hide the original failure behind a cleanup failure.
            for (String key : uploadedKeys) {
                try {
                    objectStorageService.delete(key);
                } catch (RuntimeException cleanupFailure) {
                    // Nothing more we can do here; the original failure
                    // below is still what the caller needs to see.
                }
            }
            throw e;
        }

        List<PropertyMedia> saved = propertyMediaRepository.saveAll(newMedia);
        auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.CREATE, "PROPERTY_MEDIA", propertyId, null);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(saved.stream().map(MediaResponse::from).toList());
    }

    @PatchMapping("/order")
    @Transactional
    public ResponseEntity<Void> reorder(@PathVariable UUID propertyId, @RequestBody MediaOrderRequest request) {
        propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<PropertyMedia> existing = propertyMediaRepository.findByPropertyIdOrderBySortOrder(propertyId);
        Set<UUID> existingIds = existing.stream().map(PropertyMedia::getId).collect(Collectors.toSet());
        List<UUID> requestedIds = request.mediaIdsInOrder();

        boolean valid = requestedIds != null
                && requestedIds.size() == existingIds.size()
                && new HashSet<>(requestedIds).size() == requestedIds.size()
                && existingIds.equals(new HashSet<>(requestedIds));
        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "mediaIdsInOrder must exactly match the property's current media ids");
        }

        Map<UUID, PropertyMedia> byId =
                existing.stream().collect(Collectors.toMap(PropertyMedia::getId, m -> m));
        for (int index = 0; index < requestedIds.size(); index++) {
            byId.get(requestedIds.get(index)).setSortOrder(index);
        }
        propertyMediaRepository.saveAll(byId.values());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{mediaId}")
    @Transactional
    public ResponseEntity<Void> delete(
            @PathVariable UUID propertyId,
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        PropertyMedia media = propertyMediaRepository.findById(mediaId)
                .filter(m -> m.getPropertyId().equals(propertyId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        for (String url : media.getVariants().values()) {
            objectStorageService.delete(keyFromPublicUrl(url));
        }
        propertyMediaRepository.delete(media);
        auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.DELETE, "PROPERTY_MEDIA", mediaId, null);

        return ResponseEntity.noContent().build();
    }

    /**
     * {@link ObjectStorageService#upload} returns {@code publicBaseUrl + "/" + key}
     * and every key this controller writes starts with {@code properties/}, so
     * recovering the key is a matter of finding that segment again.
     */
    private String keyFromPublicUrl(String url) {
        int index = url.indexOf("properties/");
        return index >= 0 ? url.substring(index) : url;
    }
}
