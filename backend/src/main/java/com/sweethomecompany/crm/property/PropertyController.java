package com.sweethomecompany.crm.property;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogService;
import com.sweethomecompany.crm.contact.Contact;
import com.sweethomecompany.crm.contact.ContactRepository;
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
    private final ContactRepository contactRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final UUID defaultAgencyId;

    public PropertyController(
            PropertyRepository propertyRepository,
            PropertyTranslationRepository propertyTranslationRepository,
            PropertyMediaRepository propertyMediaRepository,
            ContactRepository contactRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            @Value("${app.default-agency-id}") UUID defaultAgencyId) {
        this.propertyRepository = propertyRepository;
        this.propertyTranslationRepository = propertyTranslationRepository;
        this.propertyMediaRepository = propertyMediaRepository;
        this.contactRepository = contactRepository;
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
        if (request.ownerContactId() != null && contactRepository.findById(request.ownerContactId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner contact not found");
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
        property.setOwnerContactId(request.ownerContactId());
        property.setCreatedByUserId(actor.getId());
        Instant now = Instant.now();
        property.setCreatedAt(now);
        property.setUpdatedAt(now);

        Property saved;
        try {
            // saveAndFlush, not save: this entity has a client-assigned
            // (non-generated) id, so a plain save() only persist()s into the
            // session - the actual INSERT, and therefore the unique-index
            // violation this catch exists for, doesn't happen until the
            // transaction commits, which is after this method (and this
            // try/catch) has already returned.
            saved = propertyRepository.saveAndFlush(property);
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
        // Final-review fix: a present-but-blank referenceCode/caTitle used to
        // be applied as-is, silently undoing create()'s own required-field
        // validation through PATCH instead of being rejected.
        if (request.referenceCode() != null && request.referenceCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reference code cannot be blank");
        }
        if (request.caTitle() != null && request.caTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catalan title cannot be blank");
        }
        Contact ownerContact = null;
        if (request.ownerContactId() != null) {
            ownerContact = contactRepository.findById(request.ownerContactId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner contact not found"));
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
        // Final-review fix: every field below used to be applied without
        // being recorded in `changes`, so an update that touched only one of
        // them wrote no audit row at all - contradicting design doc §5's
        // "every create/update calls AuditLogService.log". Each now records
        // its own from/to the same way referenceCode/propertyType/etc. above
        // always did.
        if (request.addressLine() != null && !request.addressLine().equals(property.getAddressLine())) {
            changes.put("addressLine", Map.of("from", str(property.getAddressLine()), "to", request.addressLine()));
            property.setAddressLine(request.addressLine());
        }
        if (request.parish() != null && !request.parish().equals(property.getParish())) {
            changes.put("parish", Map.of("from", str(property.getParish()), "to", request.parish()));
            property.setParish(request.parish());
        }
        if (request.latitude() != null && !request.latitude().equals(property.getLatitude())) {
            changes.put("latitude", Map.of("from", str(property.getLatitude()), "to", request.latitude()));
            property.setLatitude(request.latitude());
        }
        if (request.longitude() != null && !request.longitude().equals(property.getLongitude())) {
            changes.put("longitude", Map.of("from", str(property.getLongitude()), "to", request.longitude()));
            property.setLongitude(request.longitude());
        }
        if (request.price() != null && request.price().compareTo(property.getPrice()) != 0) {
            changes.put("price", Map.of("from", property.getPrice(), "to", request.price()));
            property.setPrice(request.price());
        }
        if (request.areaM2() != null && request.areaM2().compareTo(nullToZero(property.getAreaM2())) != 0) {
            changes.put("areaM2", Map.of("from", str(property.getAreaM2()), "to", request.areaM2()));
            property.setAreaM2(request.areaM2());
        }
        if (request.rooms() != null && !request.rooms().equals(property.getRooms())) {
            changes.put("rooms", Map.of("from", str(property.getRooms()), "to", request.rooms()));
            property.setRooms(request.rooms());
        }
        if (request.bathrooms() != null && !request.bathrooms().equals(property.getBathrooms())) {
            changes.put("bathrooms", Map.of("from", str(property.getBathrooms()), "to", request.bathrooms()));
            property.setBathrooms(request.bathrooms());
        }
        if (request.yearBuilt() != null && !request.yearBuilt().equals(property.getYearBuilt())) {
            changes.put("yearBuilt", Map.of("from", str(property.getYearBuilt()), "to", request.yearBuilt()));
            property.setYearBuilt(request.yearBuilt());
        }
        if (request.energyRating() != null && !request.energyRating().equals(property.getEnergyRating())) {
            changes.put("energyRating", Map.of("from", str(property.getEnergyRating()), "to", request.energyRating()));
            property.setEnergyRating(request.energyRating());
        }
        if (request.isExclusive() != null && !request.isExclusive().equals(property.isExclusive())) {
            changes.put("isExclusive", Map.of("from", property.isExclusive(), "to", request.isExclusive()));
            property.setExclusive(request.isExclusive());
        }
        if (request.exclusivityEndDate() != null && !request.exclusivityEndDate().equals(property.getExclusivityEndDate())) {
            changes.put("exclusivityEndDate",
                    Map.of("from", str(property.getExclusivityEndDate()), "to", request.exclusivityEndDate()));
            property.setExclusivityEndDate(request.exclusivityEndDate());
        }
        if (ownerContact != null && !ownerContact.getId().equals(property.getOwnerContactId())) {
            changes.put("ownerContactId", Map.of("from", str(property.getOwnerContactId()), "to", ownerContact.getId()));
            property.setOwnerContactId(ownerContact.getId());
        }

        List<PropertyTranslation> translations = propertyTranslationRepository.findByPropertyId(id);
        if (request.caTitle() != null || request.caDescription() != null) {
            PropertyTranslation ca = translations.stream()
                    .filter(t -> t.getLocale().equals("ca"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Property " + id + " is missing its ca translation"));
            if (request.caTitle() != null && !request.caTitle().equals(ca.getTitle())) {
                changes.put("caTitle", Map.of("from", ca.getTitle(), "to", request.caTitle()));
                ca.setTitle(request.caTitle());
            }
            if (request.caDescription() != null && !request.caDescription().equals(ca.getDescription())) {
                changes.put("caDescription", Map.of("from", str(ca.getDescription()), "to", request.caDescription()));
                ca.setDescription(request.caDescription());
            }
            propertyTranslationRepository.save(ca);
        }

        property.setUpdatedAt(Instant.now());
        Property saved;
        try {
            // saveAndFlush for the same reason create() uses it: a changed
            // referenceCode's unique-index violation must surface inside
            // this try/catch, not after the method has already returned.
            saved = propertyRepository.saveAndFlush(property);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reference code already in use");
        }

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

    // Map.of(...) rejects null values outright, and several of the
    // optional fields above are legitimately null before their first edit -
    // this keeps the audit diff's "from" side a plain, readable value in
    // either case rather than throwing when building `changes`.
    private static Object str(Object value) {
        return value == null ? "null" : value;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toJson(Map<String, Object> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
