package com.sweethomecompany.crm.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogService;
import com.sweethomecompany.crm.security.AppUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final UUID defaultAgencyId;

    public UserController(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            SessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            @Value("${app.default-agency-id}") UUID defaultAgencyId) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.defaultAgencyId = defaultAgencyId;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required");
        }
        if (request.role() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }
        if (request.password() == null || request.password().isBlank() || request.password().length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        String email = EmailNormalizer.normalize(request.email());
        if (appUserRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setAgencyId(defaultAgencyId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(request.role());
        user.setActive(true);
        user.setCreatedAt(Instant.now());

        AppUser saved;
        try {
            saved = appUserRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent creates can both pass the findByEmail check above
            // before either commits. The unique index (plain and lower(email),
            // migrations V1/V2) is the real guarantee against a duplicate row;
            // this just gives the loser of that race a clean 409 instead of a
            // raw 500.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.CREATE, "APP_USER", saved.getId(), null);

        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(saved));
    }

    @GetMapping
    public List<UserResponse> list() {
        return appUserRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @PatchMapping("/{id}")
    public UserResponse update(
            @PathVariable UUID id,
            @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        AppUser user = appUserRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (Boolean.FALSE.equals(request.active()) && id.equals(actor.getId())) {
            // Deactivation takes effect immediately (SessionRegistry expiry
            // below), so an admin who deactivates themself would be locked
            // out on the spot - and if they're the only admin, there's no
            // recovery short of direct SQL, since AdminUserSeeder only runs
            // when app_user is empty.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An admin cannot deactivate their own account");
        }
        if (request.password() != null && (request.password().isBlank() || request.password().length() < MIN_PASSWORD_LENGTH)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        boolean endSessions = false;

        if (request.active() != null && request.active() != user.isActive()) {
            changes.put("active", Map.of("from", user.isActive(), "to", request.active()));
            user.setActive(request.active());
            if (!request.active()) {
                endSessions = true;
            }
        }
        if (request.fullName() != null && !request.fullName().isBlank() && !request.fullName().equals(user.getFullName())) {
            changes.put("fullName", Map.of("from", user.getFullName(), "to", request.fullName()));
            user.setFullName(request.fullName());
        }
        if (request.role() != null && request.role() != user.getRole()) {
            changes.put("role", Map.of("from", user.getRole(), "to", request.role()));
            user.setRole(request.role());
            // Any session this user already has caches the old role/authorities
            // in its AppUserPrincipal - a role change must end those sessions the
            // same way deactivation does, or the old authorities stay live until
            // the session naturally expires.
            endSessions = true;
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            changes.put("password", "changed"); // never log the actual value
            endSessions = true;
        }

        AppUser saved = appUserRepository.save(user);

        if (!changes.isEmpty()) {
            auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.UPDATE, "APP_USER", id, toJson(changes));
        }
        if (endSessions) {
            expireSessionsFor(id);
        }

        return UserResponse.from(saved);
    }

    private void expireSessionsFor(UUID userId) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AppUserPrincipal appUserPrincipal && appUserPrincipal.getId().equals(userId)) {
                for (SessionInformation sessionInformation : sessionRegistry.getAllSessions(principal, false)) {
                    sessionInformation.expireNow();
                }
            }
        }
    }

    private String toJson(Map<String, Object> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
