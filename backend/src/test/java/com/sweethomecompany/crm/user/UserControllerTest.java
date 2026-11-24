package com.sweethomecompany.crm.user;

import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    // These tests create real users via real HTTP POSTs, so - unlike the direct-call
    // repository/service tests elsewhere in this codebase - @Transactional rollback
    // doesn't apply (TestRestTemplate + RANDOM_PORT runs off-thread). Without this
    // cleanup, a second run would fail on the unique email constraint. Scoped to
    // exactly the emails these tests create, not deleteAll(), so the seeded admin
    // account survives.
    private static final List<String> TEST_EMAILS = List.of(
        "new-agent@example.com", "dupe@example.com", "manager@example.com",
        "blocked@example.com", "to-deactivate@example.com", "csrf-check@example.com",
        "no-password@example.com", "casedupe@example.com", "role-change-agent@example.com");

    @BeforeEach
    @AfterEach
    void cleanUpTestUsers() {
        // A test user's own LOGIN/LOGIN_FAILED rows have audit_log.actor_user_id pointing
        // back at them (a real FK), so those rows must go first or the user delete below
        // violates audit_log_actor_user_id_fkey. CREATE/UPDATE rows the admin generates
        // (entity_id = the test user's id) aren't a problem: entity_id isn't a FK.
        TEST_EMAILS.forEach(email -> appUserRepository.findByEmail(email).ifPresent(user -> {
            auditLogRepository.deleteAll(auditLogRepository.findByActorUserId(user.getId()));
            appUserRepository.delete(user);
        }));
    }

    private String extractCookie(ResponseEntity<?> response, String name) {
        for (String cookie : response.getHeaders().get(HttpHeaders.SET_COOKIE)) {
            if (cookie.startsWith(name + "=")) {
                return cookie.split(";")[0].substring(name.length() + 1);
            }
        }
        throw new AssertionError(name + " cookie not found");
    }

    private record Session(String csrfToken, String sessionCookie) {}

    private Session loginAs(String email, String password) {
        ResponseEntity<String> csrfResponse = restTemplate.getForEntity("/api/auth/me", String.class);
        String csrfToken = extractCookie(csrfResponse, "XSRF-TOKEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("email", email);
        body.add("password", password);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/auth/login", new HttpEntity<>(body, headers), String.class);
        return new Session(csrfToken, extractCookie(response, "SESSION"));
    }

    private HttpHeaders authedHeaders(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", session.csrfToken());
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + session.csrfToken() + "; SESSION=" + session.sessionCookie());
        return headers;
    }

    @Test
    void adminCreatesAUserWithAHashedPasswordAndAnAuditRow() {
        Session admin = loginAs("admin@example.com", "change-me-now");
        String createJson = """
            {"email":"new-agent@example.com","password":"a-strong-password","fullName":"New Agent","role":"AGENT"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/users", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().toLowerCase()).doesNotContain("password");
        AppUser saved = appUserRepository.findByEmail("new-agent@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("a-strong-password");
        assertThat(auditLogRepository.findAll().stream()
            .anyMatch(row -> row.getAction() == AuditAction.CREATE && row.getEntityId().equals(saved.getId()))).isTrue();
    }

    @Test
    void creatingWithADuplicateEmailReturns409() {
        Session admin = loginAs("admin@example.com", "change-me-now");
        String createJson = """
            {"email":"dupe@example.com","password":"a-strong-password","fullName":"First","role":"AGENT"}""";
        restTemplate.postForEntity("/api/users", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        ResponseEntity<String> secondResponse = restTemplate.postForEntity(
            "/api/users", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void nonAdminCannotCreateUsers() {
        Session admin = loginAs("admin@example.com", "change-me-now");
        String createJson = """
            {"email":"manager@example.com","password":"a-strong-password","fullName":"A Manager","role":"MANAGER"}""";
        restTemplate.postForEntity("/api/users", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        Session manager = loginAs("manager@example.com", "a-strong-password");
        String anotherJson = """
            {"email":"blocked@example.com","password":"whatever12345","fullName":"Blocked","role":"AGENT"}""";
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/users", new HttpEntity<>(anotherJson, authedHeaders(manager)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deactivatingAUserExpiresTheirSessionAndBlocksTheirNextLogin() {
        Session admin = loginAs("admin@example.com", "change-me-now");
        String createJson = """
            {"email":"to-deactivate@example.com","password":"a-strong-password","fullName":"Soon Gone","role":"AGENT"}""";
        restTemplate.postForEntity("/api/users", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);
        Session target = loginAs("to-deactivate@example.com", "a-strong-password");

        HttpHeaders meHeaders = new HttpHeaders();
        meHeaders.add(HttpHeaders.COOKIE, "SESSION=" + target.sessionCookie());
        assertThat(restTemplate.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(meHeaders), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        AppUser targetUser = appUserRepository.findByEmail("to-deactivate@example.com").orElseThrow();
        ResponseEntity<String> deactivateResponse = restTemplate.exchange(
            "/api/users/" + targetUser.getId(), HttpMethod.PATCH,
            new HttpEntity<>("{\"active\":false}", authedHeaders(admin)), String.class);
        assertThat(deactivateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> meAfterDeactivation = restTemplate.exchange(
            "/api/auth/me", HttpMethod.GET, new HttpEntity<>(meHeaders), String.class);
        assertThat(meAfterDeactivation.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> csrfResponse = restTemplate.getForEntity("/api/auth/me", String.class);
        String csrfToken = extractCookie(csrfResponse, "XSRF-TOKEN");
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        loginHeaders.add("X-XSRF-TOKEN", csrfToken);
        loginHeaders.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("email", "to-deactivate@example.com");
        body.add("password", "a-strong-password");
        ResponseEntity<String> loginAfterDeactivation = restTemplate.postForEntity(
            "/api/auth/login", new HttpEntity<>(body, loginHeaders), String.class);
        assertThat(loginAfterDeactivation.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void mutatingWithAValidSessionButNoCsrfTokenIsRejected() {
        Session admin = loginAs("admin@example.com", "change-me-now");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Deliberately only the SESSION cookie - no X-XSRF-TOKEN header and
        // no XSRF-TOKEN cookie. This is exactly the Review Focus scenario:
        // "a request with a valid session cookie but no CSRF token must be
        // rejected on any mutating endpoint" - postingToLogoutWithoutACsrfTokenIsRejected
        // (in LoginTest) proves this with no session at all, which would
        // still pass even if someone later exempted /api/users from CSRF.
        headers.add(HttpHeaders.COOKIE, "SESSION=" + admin.sessionCookie());
        String createJson = """
            {"email":"csrf-check@example.com","password":"a-strong-password","fullName":"Csrf Check","role":"AGENT"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/users", new HttpEntity<>(createJson, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(appUserRepository.findByEmail("csrf-check@example.com")).isEmpty();
    }

    @Test
    void creatingWithABlankPasswordReturns400() {
        Session admin = loginAs("admin@example.com", "change-me-now");
        String createJson = """
            {"email":"no-password@example.com","password":"","fullName":"No Password","role":"AGENT"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/users", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(appUserRepository.findByEmail("no-password@example.com")).isEmpty();
    }

    @Test
    void creatingWithABlankEmailReturns400() {
        Session admin = loginAs("admin@example.com", "change-me-now");
        String createJson = """
            {"email":"","password":"a-strong-password","fullName":"No Email","role":"AGENT"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/users", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void creatingAnEmailThatDiffersOnlyByCaseFromAnExistingOneReturns409() {
        Session admin = loginAs("admin@example.com", "change-me-now");
        String firstJson = """
            {"email":"CaseDupe@example.com","password":"a-strong-password","fullName":"First","role":"AGENT"}""";
        ResponseEntity<String> firstResponse = restTemplate.postForEntity(
            "/api/users", new HttpEntity<>(firstJson, authedHeaders(admin)), String.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String secondJson = """
            {"email":"casedupe@example.com","password":"another-password","fullName":"Second","role":"AGENT"}""";
        ResponseEntity<String> secondResponse = restTemplate.postForEntity(
            "/api/users", new HttpEntity<>(secondJson, authedHeaders(admin)), String.class);

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Stored normalized (lowercase) by EmailNormalizer - exactly one row.
        assertThat(appUserRepository.findByEmail("casedupe@example.com")).isPresent();
    }

    @Test
    void anAdminCannotDeactivateThemselves() {
        Session admin = loginAs("admin@example.com", "change-me-now");
        AppUser adminUser = appUserRepository.findByEmail("admin@example.com").orElseThrow();

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/users/" + adminUser.getId(), HttpMethod.PATCH,
            new HttpEntity<>("{\"active\":false}", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(appUserRepository.findByEmail("admin@example.com").orElseThrow().isActive()).isTrue();
    }

    @Test
    void adminCanChangeARoleAndPasswordAndTheOldSessionAndPasswordStopWorking() {
        Session admin = loginAs("admin@example.com", "change-me-now");
        String createJson = """
            {"email":"role-change-agent@example.com","password":"original-password","fullName":"Will Change","role":"AGENT"}""";
        restTemplate.postForEntity("/api/users", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);
        Session target = loginAs("role-change-agent@example.com", "original-password");

        AppUser targetUser = appUserRepository.findByEmail("role-change-agent@example.com").orElseThrow();
        String updateJson = """
            {"role":"MANAGER","password":"brand-new-password"}""";
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/users/" + targetUser.getId(), HttpMethod.PATCH,
            new HttpEntity<>(updateJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().toLowerCase()).doesNotContain("password");
        assertThat(appUserRepository.findByEmail("role-change-agent@example.com").orElseThrow().getRole())
            .isEqualTo(Role.MANAGER);

        // The old session's cached AppUserPrincipal still holds the old
        // role/authorities - a role change must expire it, the same way
        // deactivation does, or the stale authorities stay live.
        HttpHeaders meHeaders = new HttpHeaders();
        meHeaders.add(HttpHeaders.COOKIE, "SESSION=" + target.sessionCookie());
        assertThat(restTemplate.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(meHeaders), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(loginFails("role-change-agent@example.com", "original-password")).isTrue();
        Session withNewPassword = loginAs("role-change-agent@example.com", "brand-new-password");
        assertThat(withNewPassword.sessionCookie()).isNotBlank();
    }

    private boolean loginFails(String email, String password) {
        ResponseEntity<String> csrfResponse = restTemplate.getForEntity("/api/auth/me", String.class);
        String csrfToken = extractCookie(csrfResponse, "XSRF-TOKEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("email", email);
        body.add("password", password);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/auth/login", new HttpEntity<>(body, headers), String.class);
        return response.getStatusCode() == HttpStatus.UNAUTHORIZED;
    }
}
