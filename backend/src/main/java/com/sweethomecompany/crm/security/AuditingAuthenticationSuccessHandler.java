package com.sweethomecompany.crm.security;

import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogService;
import com.sweethomecompany.crm.user.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AuditingAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AppUserRepository appUserRepository;
    private final AuditLogService auditLogService;
    private final UUID defaultAgencyId;

    public AuditingAuthenticationSuccessHandler(
            AppUserRepository appUserRepository,
            AuditLogService auditLogService,
            @Value("${app.default-agency-id}") UUID defaultAgencyId) {
        this.appUserRepository = appUserRepository;
        this.auditLogService = auditLogService;
        this.defaultAgencyId = defaultAgencyId;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();

        appUserRepository.findByEmail(principal.getUsername()).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            appUserRepository.save(user);
        });

        auditLogService.log(defaultAgencyId, principal.getId(), AuditAction.LOGIN, "APP_USER", principal.getId(), null);

        response.setStatus(HttpServletResponse.SC_OK);
    }
}
