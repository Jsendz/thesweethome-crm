package com.sweethomecompany.crm.auth;

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
class AuthControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String extractCookie(ResponseEntity<?> response, String name) {
        for (String cookie : response.getHeaders().get(HttpHeaders.SET_COOKIE)) {
            if (cookie.startsWith(name + "=")) {
                return cookie.split(";")[0].substring(name.length() + 1);
            }
        }
        throw new AssertionError(name + " cookie not found");
    }

    @Test
    void returnsTheLoggedInUserAndNeverLeaksThePasswordHash() {
        ResponseEntity<String> csrfResponse = restTemplate.getForEntity("/api/auth/me", String.class);
        String csrfToken = extractCookie(csrfResponse, "XSRF-TOKEN");

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        loginHeaders.add("X-XSRF-TOKEN", csrfToken);
        loginHeaders.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("email", "admin@example.com");
        body.add("password", "change-me-now");
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
            "/api/auth/login", new HttpEntity<>(body, loginHeaders), String.class);
        String sessionCookie = extractCookie(loginResponse, "SESSION");

        HttpHeaders meHeaders = new HttpHeaders();
        meHeaders.add(HttpHeaders.COOKIE, "SESSION=" + sessionCookie);
        ResponseEntity<String> meResponse = restTemplate.exchange(
            "/api/auth/me", HttpMethod.GET, new HttpEntity<>(meHeaders), String.class);

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody()).contains("admin@example.com").contains("ADMIN");
        assertThat(meResponse.getBody().toLowerCase()).doesNotContain("password");
    }
}
