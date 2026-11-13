package com.sweethomecompany.crm.security;

import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogService;
import com.sweethomecompany.crm.user.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuditingAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final AppUserRepository appUserRepository;
    private final AuditLogService auditLogService;
    private final UUID defaultAgencyId;

    public AuditingAuthenticationFailureHandler(
            AppUserRepository appUserRepository,
            AuditLogService auditLogService,
            @Value("${app.default-agency-id}") UUID defaultAgencyId) {
        this.appUserRepository = appUserRepository;
        this.auditLogService = auditLogService;
        this.defaultAgencyId = defaultAgencyId;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        String attemptedEmail = request.getParameter("email");

        UUID actorIdOrNull = attemptedEmail == null
            ? null
            : appUserRepository.findByEmail(attemptedEmail).map(user -> user.getId()).orElse(null);

        auditLogService.log(defaultAgencyId, actorIdOrNull, AuditAction.LOGIN_FAILED, "APP_USER", actorIdOrNull, null);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
