package com.sweethomecompany.crm.contact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogService;
import com.sweethomecompany.crm.security.AppUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * No role restriction on any endpoint here, same decision as
 * PropertyController: any authenticated user may create, view, and edit
 * contacts, per the Phase 4 design doc.
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactRepository contactRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final UUID defaultAgencyId;

    public ContactController(
            ContactRepository contactRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            @Value("${app.default-agency-id}") UUID defaultAgencyId) {
        this.contactRepository = contactRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.defaultAgencyId = defaultAgencyId;
    }

    @PostMapping
    public ResponseEntity<ContactResponse> create(
            @RequestBody CreateContactRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        if (request.contactType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact type is required");
        }
        if (request.firstName() == null || request.firstName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "First name is required");
        }
        if (request.lastName() == null || request.lastName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last name is required");
        }
        if (request.source() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source is required");
        }

        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setAgencyId(defaultAgencyId);
        contact.setContactType(request.contactType());
        contact.setFirstName(request.firstName());
        contact.setLastName(request.lastName());
        contact.setEmail(request.email());
        contact.setPhone(request.phone());
        contact.setPreferredLocale(request.preferredLocale());
        contact.setSource(request.source());
        contact.setSourceDetail(request.sourceDetail());
        contact.setNotes(request.notes());
        contact.setOwnerUserId(request.ownerUserId() != null ? request.ownerUserId() : actor.getId());
        Instant now = Instant.now();
        contact.setCreatedAt(now);
        contact.setUpdatedAt(now);

        Contact saved = contactRepository.save(contact);

        auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.CREATE, "CONTACT", saved.getId(), null);

        return ResponseEntity.status(HttpStatus.CREATED).body(ContactResponse.from(saved));
    }

    @GetMapping
    public List<ContactResponse> list() {
        return contactRepository.findAll().stream().map(ContactResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ContactResponse get(@PathVariable UUID id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ContactResponse.from(contact);
    }

    @PatchMapping("/{id}")
    public ContactResponse update(
            @PathVariable UUID id,
            @RequestBody UpdateContactRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (request.firstName() != null && request.firstName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "First name cannot be blank");
        }
        if (request.lastName() != null && request.lastName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last name cannot be blank");
        }

        Map<String, Object> changes = new LinkedHashMap<>();

        if (request.contactType() != null && request.contactType() != contact.getContactType()) {
            changes.put("contactType", Map.of("from", contact.getContactType(), "to", request.contactType()));
            contact.setContactType(request.contactType());
        }
        if (request.firstName() != null && !request.firstName().equals(contact.getFirstName())) {
            changes.put("firstName", Map.of("from", contact.getFirstName(), "to", request.firstName()));
            contact.setFirstName(request.firstName());
        }
        if (request.lastName() != null && !request.lastName().equals(contact.getLastName())) {
            changes.put("lastName", Map.of("from", contact.getLastName(), "to", request.lastName()));
            contact.setLastName(request.lastName());
        }
        if (request.email() != null && !request.email().equals(contact.getEmail())) {
            changes.put("email", Map.of("from", str(contact.getEmail()), "to", request.email()));
            contact.setEmail(request.email());
        }
        if (request.phone() != null && !request.phone().equals(contact.getPhone())) {
            changes.put("phone", Map.of("from", str(contact.getPhone()), "to", request.phone()));
            contact.setPhone(request.phone());
        }
        if (request.preferredLocale() != null && !request.preferredLocale().equals(contact.getPreferredLocale())) {
            changes.put("preferredLocale", Map.of("from", str(contact.getPreferredLocale()), "to", request.preferredLocale()));
            contact.setPreferredLocale(request.preferredLocale());
        }
        if (request.source() != null && request.source() != contact.getSource()) {
            changes.put("source", Map.of("from", contact.getSource(), "to", request.source()));
            contact.setSource(request.source());
        }
        if (request.sourceDetail() != null && !request.sourceDetail().equals(contact.getSourceDetail())) {
            changes.put("sourceDetail", Map.of("from", str(contact.getSourceDetail()), "to", request.sourceDetail()));
            contact.setSourceDetail(request.sourceDetail());
        }
        if (request.notes() != null && !request.notes().equals(contact.getNotes())) {
            changes.put("notes", Map.of("from", str(contact.getNotes()), "to", request.notes()));
            contact.setNotes(request.notes());
        }
        if (request.ownerUserId() != null && !request.ownerUserId().equals(contact.getOwnerUserId())) {
            changes.put("ownerUserId", Map.of("from", contact.getOwnerUserId(), "to", request.ownerUserId()));
            contact.setOwnerUserId(request.ownerUserId());
        }

        contact.setUpdatedAt(Instant.now());
        Contact saved = contactRepository.save(contact);

        if (!changes.isEmpty()) {
            auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.UPDATE, "CONTACT", id, toJson(changes));
        }

        return ContactResponse.from(saved);
    }

    // Map.of(...) rejects null values outright, and several of the optional
    // fields above are legitimately null before their first edit - this
    // keeps the audit diff's "from" side a plain, readable value either way.
    private static Object str(Object value) {
        return value == null ? "null" : value;
    }

    private String toJson(Map<String, Object> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
