package com.sweethomecompany.crm.property;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogService;
import com.sweethomecompany.crm.security.AppUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * No role restriction on any endpoint here - any authenticated user may
 * create, edit, and withdraw a property, per the Phase 3 design doc's
 * decision. "Withdraw" is just PATCH with status=WITHDRAWN; there's no
 * separate delete endpoint, same disable-not-delete rule as app_user.
 */
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private static final Set<String> NON_CATALAN_LOCALES = Set.of("es", "fr", "en");

    private final PropertyRepository propertyRepository;
    private final PropertyTranslationRepository propertyTranslationRepository;
    private final PropertyMediaRepository propertyMediaRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final UUID defaultAgencyId;

    public PropertyController(
            PropertyRepository propertyRepository,
            PropertyTranslationRepository propertyTranslationRepository,
            PropertyMediaRepository propertyMediaRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            @Value("${app.default-agency-id}") UUID defaultAgencyId) {
        this.propertyRepository = propertyRepository;
        this.propertyTranslationRepository = propertyTranslationRepository;
        this.propertyMediaRepository = propertyMediaRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.defaultAgencyId = defaultAgencyId;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<PropertyResponse> create(
            @RequestBody CreatePropertyRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        if (request.referenceCode() == null || request.referenceCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reference code is required");
        }
        if (request.propertyType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Property type is required");
        }
        if (request.operation() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operation is required");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be greater than zero");
        }
        if (request.caTitle() == null || request.caTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A Catalan title is required");
        }
        if (propertyRepository.existsByAgencyIdAndReferenceCode(defaultAgencyId, request.referenceCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reference code already in use");
        }

        Property property = new Property();
        property.setId(UUID.randomUUID());
        property.setAgencyId(defaultAgencyId);
        property.setReferenceCode(request.referenceCode());
        property.setPropertyType(request.propertyType());
        property.setOperation(request.operation());
        property.setStatus(PropertyStatus.DRAFT);
        property.setAddressLine(request.addressLine());
        property.setParish(request.parish());
        property.setLatitude(request.latitude());
        property.setLongitude(request.longitude());
        property.setPrice(request.price());
        property.setAreaM2(request.areaM2());
        property.setRooms(request.rooms());
        property.setBathrooms(request.bathrooms());
        property.setYearBuilt(request.yearBuilt());
        property.setEnergyRating(request.energyRating());
        property.setExclusive(Boolean.TRUE.equals(request.isExclusive()));
        property.setExclusivityEndDate(request.exclusivityEndDate());
        property.setCreatedByUserId(actor.getId());
        Instant now = Instant.now();
        property.setCreatedAt(now);
        property.setUpdatedAt(now);

        Property saved;
        try {
            saved = propertyRepository.save(property);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reference code already in use");
        }

        PropertyTranslation translation = new PropertyTranslation();
        translation.setId(UUID.randomUUID());
        translation.setPropertyId(saved.getId());
        translation.setLocale("ca");
        translation.setTitle(request.caTitle());
        translation.setDescription(request.caDescription());
        propertyTranslationRepository.save(translation);

        auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.CREATE, "PROPERTY", saved.getId(), null);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PropertyResponse.from(saved, List.of(translation), List.of()));
    }

    @GetMapping
    public List<PropertySummaryResponse> list() {
        return propertyRepository.findAll().stream()
                .map(property -> PropertySummaryResponse.from(
                        property, propertyMediaRepository.findByPropertyIdOrderBySortOrder(property.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public PropertyResponse get(@PathVariable UUID id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return PropertyResponse.from(
                property,
                propertyTranslationRepository.findByPropertyId(id),
                propertyMediaRepository.findByPropertyIdOrderBySortOrder(id));
    }

    @PatchMapping("/{id}")
    @Transactional
    public PropertyResponse update(
            @PathVariable UUID id,
            @RequestBody UpdatePropertyRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request.price() != null && request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be greater than zero");
        }

        Map<String, Object> changes = new LinkedHashMap<>();

        if (request.referenceCode() != null && !request.referenceCode().equals(property.getReferenceCode())) {
            changes.put("referenceCode", Map.of("from", property.getReferenceCode(), "to", request.referenceCode()));
            property.setReferenceCode(request.referenceCode());
        }
        if (request.propertyType() != null && request.propertyType() != property.getPropertyType()) {
            changes.put("propertyType", Map.of("from", property.getPropertyType(), "to", request.propertyType()));
            property.setPropertyType(request.propertyType());
        }
        if (request.operation() != null && request.operation() != property.getOperation()) {
            changes.put("operation", Map.of("from", property.getOperation(), "to", request.operation()));
            property.setOperation(request.operation());
        }
        if (request.status() != null && request.status() != property.getStatus()) {
            changes.put("status", Map.of("from", property.getStatus(), "to", request.status()));
            property.setStatus(request.status());
        }
        if (request.addressLine() != null) {
            property.setAddressLine(request.addressLine());
        }
        if (request.parish() != null) {
            property.setParish(request.parish());
        }
        if (request.latitude() != null) {
            property.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            property.setLongitude(request.longitude());
        }
        if (request.price() != null && request.price().compareTo(property.getPrice()) != 0) {
            changes.put("price", Map.of("from", property.getPrice(), "to", request.price()));
            property.setPrice(request.price());
        }
        if (request.areaM2() != null) {
            property.setAreaM2(request.areaM2());
        }
        if (request.rooms() != null) {
            property.setRooms(request.rooms());
        }
        if (request.bathrooms() != null) {
            property.setBathrooms(request.bathrooms());
        }
        if (request.yearBuilt() != null) {
            property.setYearBuilt(request.yearBuilt());
        }
        if (request.energyRating() != null) {
            property.setEnergyRating(request.energyRating());
        }
        if (request.isExclusive() != null) {
            property.setExclusive(request.isExclusive());
        }
        if (request.exclusivityEndDate() != null) {
            property.setExclusivityEndDate(request.exclusivityEndDate());
        }

        List<PropertyTranslation> translations = propertyTranslationRepository.findByPropertyId(id);
        if (request.caTitle() != null || request.caDescription() != null) {
            PropertyTranslation ca = translations.stream()
                    .filter(t -> t.getLocale().equals("ca"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Property " + id + " is missing its ca translation"));
            if (request.caTitle() != null) {
                ca.setTitle(request.caTitle());
            }
            if (request.caDescription() != null) {
                ca.setDescription(request.caDescription());
            }
            propertyTranslationRepository.save(ca);
        }

        property.setUpdatedAt(Instant.now());
        Property saved = propertyRepository.save(property);

        if (!changes.isEmpty()) {
            auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.UPDATE, "PROPERTY", id, toJson(changes));
        }

        return PropertyResponse.from(
                saved,
                propertyTranslationRepository.findByPropertyId(id),
                propertyMediaRepository.findByPropertyIdOrderBySortOrder(id));
    }

    @PostMapping("/{id}/translations")
    public PropertyResponse addTranslation(@PathVariable UUID id, @RequestBody AddTranslationRequest request) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request.locale() == null || !NON_CATALAN_LOCALES.contains(request.locale())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Locale must be one of es, fr, en");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }

        PropertyTranslation translation = propertyTranslationRepository
                .findByPropertyIdAndLocale(id, request.locale())
                .orElseGet(PropertyTranslation::new);
        translation.setId(translation.getId() == null ? UUID.randomUUID() : translation.getId());
        translation.setPropertyId(id);
        translation.setLocale(request.locale());
        translation.setTitle(request.title());
        translation.setDescription(request.description());
        propertyTranslationRepository.save(translation);

        return PropertyResponse.from(
                property,
                propertyTranslationRepository.findByPropertyId(id),
                propertyMediaRepository.findByPropertyIdOrderBySortOrder(id));
    }

    private String toJson(Map<String, Object> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
