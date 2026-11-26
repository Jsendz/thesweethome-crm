package com.sweethomecompany.crm.deal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogService;
import com.sweethomecompany.crm.contact.Contact;
import com.sweethomecompany.crm.contact.ContactRepository;
import com.sweethomecompany.crm.property.Property;
import com.sweethomecompany.crm.property.PropertyRepository;
import com.sweethomecompany.crm.security.AppUserPrincipal;
import com.sweethomecompany.crm.user.AppUser;
import com.sweethomecompany.crm.user.AppUserRepository;
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
 * PropertyController/ContactController.
 */
@RestController
@RequestMapping("/api/deals")
public class DealController {

    private final DealRepository dealRepository;
    private final ContactRepository contactRepository;
    private final PropertyRepository propertyRepository;
    private final AppUserRepository appUserRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final UUID defaultAgencyId;

    public DealController(
            DealRepository dealRepository,
            ContactRepository contactRepository,
            PropertyRepository propertyRepository,
            AppUserRepository appUserRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            @Value("${app.default-agency-id}") UUID defaultAgencyId) {
        this.dealRepository = dealRepository;
        this.contactRepository = contactRepository;
        this.propertyRepository = propertyRepository;
        this.appUserRepository = appUserRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.defaultAgencyId = defaultAgencyId;
    }

    @PostMapping
    public ResponseEntity<DealResponse> create(
            @RequestBody CreateDealRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        if (request.contactId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact is required");
        }
        Contact contact = contactRepository.findById(request.contactId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found"));
        Property property = null;
        if (request.propertyId() != null) {
            property = propertyRepository.findById(request.propertyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
        }
        UUID assignedUserId = request.assignedUserId() != null ? request.assignedUserId() : actor.getId();
        AppUser assignedUser = appUserRepository.findById(assignedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned user not found"));

        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setAgencyId(defaultAgencyId);
        deal.setContactId(contact.getId());
        deal.setPropertyId(property != null ? property.getId() : null);
        deal.setAssignedUserId(assignedUser.getId());
        deal.setStage(DealStage.LEAD);
        deal.setExpectedCloseDate(request.expectedCloseDate());
        Instant now = Instant.now();
        deal.setCreatedAt(now);
        deal.setUpdatedAt(now);

        Deal saved = dealRepository.save(deal);

        auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.CREATE, "DEAL", saved.getId(), null);

        return ResponseEntity.status(HttpStatus.CREATED).body(DealResponse.from(saved, contact, property, assignedUser));
    }

    @GetMapping
    public List<DealResponse> list(@RequestParam(required = false) UUID contactId) {
        List<Deal> deals = contactId != null ? dealRepository.findByContactId(contactId) : dealRepository.findAll();
        return deals.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public DealResponse get(@PathVariable UUID id) {
        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toResponse(deal);
    }

    @PatchMapping("/{id}")
    public DealResponse update(
            @PathVariable UUID id,
            @RequestBody UpdateDealRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Map<String, Object> changes = new LinkedHashMap<>();

        if (request.propertyId() != null && !request.propertyId().equals(deal.getPropertyId())) {
            Property property = propertyRepository.findById(request.propertyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
            changes.put("propertyId", Map.of("from", str(deal.getPropertyId()), "to", property.getId()));
            deal.setPropertyId(property.getId());
        }
        if (request.assignedUserId() != null && !request.assignedUserId().equals(deal.getAssignedUserId())) {
            AppUser assignedUser = appUserRepository.findById(request.assignedUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned user not found"));
            changes.put("assignedUserId", Map.of("from", deal.getAssignedUserId(), "to", assignedUser.getId()));
            deal.setAssignedUserId(assignedUser.getId());
        }
        if (request.expectedCloseDate() != null && !request.expectedCloseDate().equals(deal.getExpectedCloseDate())) {
            changes.put("expectedCloseDate", Map.of("from", str(deal.getExpectedCloseDate()), "to", request.expectedCloseDate()));
            deal.setExpectedCloseDate(request.expectedCloseDate());
        }

        if (request.stage() != null) {
            if (request.stage() == DealStage.COLD) {
                if (request.coldReason() == null || request.coldReason().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required to mark a deal as cold");
                }
                // Checked before mutating stage: re-submitting COLD on an
                // already-COLD deal must not reset coldMarkedAt/coldMarkedByUserId.
                boolean wasAlreadyCold = deal.getStage() == DealStage.COLD;
                if (deal.getStage() != DealStage.COLD) {
                    changes.put("stage", Map.of("from", deal.getStage(), "to", DealStage.COLD));
                }
                if (!request.coldReason().equals(deal.getColdReason())) {
                    changes.put("coldReason", Map.of("from", str(deal.getColdReason()), "to", request.coldReason()));
                }
                deal.setColdReason(request.coldReason());
                if (!wasAlreadyCold) {
                    deal.setColdMarkedByUserId(actor.getId());
                    deal.setColdMarkedAt(Instant.now());
                }
                deal.setStage(DealStage.COLD);
            } else if (request.stage() == DealStage.CLOSED_LOST) {
                if (request.lostReason() == null || request.lostReason().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required to close a deal as lost");
                }
                if (deal.getStage() != DealStage.CLOSED_LOST) {
                    changes.put("stage", Map.of("from", deal.getStage(), "to", DealStage.CLOSED_LOST));
                }
                if (!request.lostReason().equals(deal.getLostReason())) {
                    changes.put("lostReason", Map.of("from", str(deal.getLostReason()), "to", request.lostReason()));
                }
                deal.setLostReason(request.lostReason());
                deal.setStage(DealStage.CLOSED_LOST);
            } else if (request.stage() != deal.getStage()) {
                // Any other stage: set it with no extra validation and no change
                // to coldReason/coldMarkedByUserId/coldMarkedAt/lostReason - those
                // are left exactly as they were, preserving history of a deal that
                // was once COLD or CLOSED_LOST.
                changes.put("stage", Map.of("from", deal.getStage(), "to", request.stage()));
                deal.setStage(request.stage());
            }
        }

        deal.setUpdatedAt(Instant.now());
        Deal saved = dealRepository.save(deal);

        if (!changes.isEmpty()) {
            auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.UPDATE, "DEAL", id, toJson(changes));
        }

        return toResponse(saved);
    }

    private DealResponse toResponse(Deal deal) {
        Contact contact = contactRepository.findById(deal.getContactId())
                .orElseThrow(() -> new IllegalStateException("Deal " + deal.getId() + " references a missing contact"));
        Property property = deal.getPropertyId() != null
                ? propertyRepository.findById(deal.getPropertyId()).orElse(null)
                : null;
        AppUser assignedUser = appUserRepository.findById(deal.getAssignedUserId())
                .orElseThrow(() -> new IllegalStateException("Deal " + deal.getId() + " references a missing assigned user"));
        return DealResponse.from(deal, contact, property, assignedUser);
    }

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
