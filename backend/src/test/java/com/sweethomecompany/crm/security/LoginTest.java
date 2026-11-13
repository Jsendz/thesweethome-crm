package com.sweethomecompany.crm.security;

import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogRepository;
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
class LoginTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    // LoginTest drives real HTTP round-trips (TestRestTemplate + RANDOM_PORT), which run
    // on a separate thread/transaction from the test method, so @Transactional rollback
    // (used elsewhere in this codebase) doesn't apply here. Without this cleanup, rows
    // written by one run would survive into the next, letting anyMatch/count assertions
    // below keep passing on stale data even if login auditing broke.
    @BeforeEach
    void clearAuditLog() {
        auditLogRepository.deleteAll();
    }

    private String extractCookie(ResponseEntity<?> response, String name) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        for (String cookie : cookies) {
            if (cookie.startsWith(name + "=")) {
                return cookie.split(";")[0].substring(name.length() + 1);
            }
        }
        throw new AssertionError(name + " cookie not found in: " + cookies);
    }

    @Test
    void getMeWithoutASessionReturns401AndSetsTheCsrfCookie() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/auth/me", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE).toString()).contains("XSRF-TOKEN");
    }

    @Test
    void loginWithCorrectCredentialsSucceedsAndWritesALoginAuditRow() {
        ResponseEntity<String> csrfResponse = restTemplate.getForEntity("/api/auth/me", String.class);
        String csrfToken = extractCookie(csrfResponse, "XSRF-TOKEN");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("email", "admin@example.com");
        body.add("password", "change-me-now");

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/auth/login", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auditLogRepository.findAll().stream()
            .filter(row -> row.getAction() == AuditAction.LOGIN).count()).isEqualTo(1);
    }

    @Test
    void loginWithWrongPasswordAndLoginWithUnknownEmailBothFailTheSameWay() {
        ResponseEntity<String> csrfResponse = restTemplate.getForEntity("/api/auth/me", String.class);
        String csrfToken = extractCookie(csrfResponse, "XSRF-TOKEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);

        MultiValueMap<String, String> wrongPassword = new LinkedMultiValueMap<>();
        wrongPassword.add("email", "admin@example.com");
        wrongPassword.add("password", "not-the-right-password");
        ResponseEntity<String> wrongPasswordResponse = restTemplate.postForEntity(
            "/api/auth/login", new HttpEntity<>(wrongPassword, headers), String.class);

        MultiValueMap<String, String> unknownEmail = new LinkedMultiValueMap<>();
        unknownEmail.add("email", "nobody-here@example.com");
        unknownEmail.add("password", "whatever");
        ResponseEntity<String> unknownEmailResponse = restTemplate.postForEntity(
            "/api/auth/login", new HttpEntity<>(unknownEmail, headers), String.class);

        assertThat(wrongPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmailResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(auditLogRepository.findAll().stream()
            .filter(row -> row.getAction() == AuditAction.LOGIN_FAILED).count()).isEqualTo(2);
    }

    @Test
    void postingToLogoutWithoutACsrfTokenIsRejected() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/auth/logout", new HttpEntity<>(new LinkedMultiValueMap<>()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
