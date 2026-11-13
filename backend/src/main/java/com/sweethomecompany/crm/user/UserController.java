package com.sweethomecompany.crm.user;

import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogService;
import com.sweethomecompany.crm.security.AppUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final SessionRegistry sessionRegistry;
    private final UUID defaultAgencyId;

    public UserController(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            SessionRegistry sessionRegistry,
            @Value("${app.default-agency-id}") UUID defaultAgencyId) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.sessionRegistry = sessionRegistry;
        this.defaultAgencyId = defaultAgencyId;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        if (appUserRepository.findByEmail(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setAgencyId(defaultAgencyId);
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(request.role());
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        AppUser saved = appUserRepository.save(user);

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

        user.setActive(request.active());
        AppUser saved = appUserRepository.save(user);

        auditLogService.log(defaultAgencyId, actor.getId(), AuditAction.UPDATE, "APP_USER", id, null);

        if (!request.active()) {
            for (Object principal : sessionRegistry.getAllPrincipals()) {
                if (principal instanceof AppUserPrincipal appUserPrincipal && appUserPrincipal.getId().equals(id)) {
                    for (SessionInformation sessionInformation : sessionRegistry.getAllSessions(principal, false)) {
                        sessionInformation.expireNow();
                    }
                }
            }
        }

        return UserResponse.from(saved);
    }
}
