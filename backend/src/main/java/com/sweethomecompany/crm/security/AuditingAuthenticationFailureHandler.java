package com.sweethomecompany.crm.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogService;
import com.sweethomecompany.crm.user.AppUser;
import com.sweethomecompany.crm.user.AppUserRepository;
import com.sweethomecompany.crm.user.EmailNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AuditingAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final AppUserRepository appUserRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final UUID defaultAgencyId;

    public AuditingAuthenticationFailureHandler(
            AppUserRepository appUserRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            @Value("${app.default-agency-id}") UUID defaultAgencyId) {
        this.appUserRepository = appUserRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.defaultAgencyId = defaultAgencyId;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        String attemptedEmail = request.getParameter("email");
        String normalizedEmail = EmailNormalizer.normalize(attemptedEmail);

        UUID matchedUserIdOrNull = normalizedEmail == null
            ? null
            : appUserRepository.findByEmail(normalizedEmail).map(AppUser::getId).orElse(null);

        // actorUserId is deliberately always null, never matchedUserIdOrNull:
        // whoever sent this request isn't authenticated, so attributing a
        // failed attempt to the account it targeted would make that
        // account's own audit trail (wrongly) include someone else's
        // password guesses. The targeted account, if the email matched one,
        // goes in entityId instead - that's the account the attempt was
        // about, not the actor who made it.
        auditLogService.log(
            defaultAgencyId, null, AuditAction.LOGIN_FAILED, "APP_USER", matchedUserIdOrNull, toDetailJson(attemptedEmail));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private String toDetailJson(String attemptedEmail) {
        try {
            return objectMapper.writeValueAsString(Map.of("attemptedEmail", attemptedEmail == null ? "" : attemptedEmail));
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
