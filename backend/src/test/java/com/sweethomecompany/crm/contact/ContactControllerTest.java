package com.sweethomecompany.crm.contact;

import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogRepository;
import com.sweethomecompany.crm.user.AppUser;
import com.sweethomecompany.crm.user.AppUserRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContactControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    // These tests create real contacts via real HTTP POSTs, so (same
    // reasoning as UserControllerTest/PropertyControllerTest) @Transactional
    // rollback doesn't apply - clean up by last name, which every test here
    // sets to something unique to it.
    private static final List<String> TEST_LAST_NAMES = List.of(
            "Puig", "Owned", "Missing-Type", "Blank-Name", "Gotten", "Listed-A", "Listed-B", "Updated");

    @BeforeEach
    @AfterEach
    void cleanUpTestContacts() {
        contactRepository.findAll().stream()
                .filter(contact -> TEST_LAST_NAMES.contains(contact.getLastName()))
                .forEach(contactRepository::delete);
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

    private Session loginAsAdmin() {
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
    void creatingAContactWithValidDataSucceedsAndWritesACreateAuditRow() {
        Session admin = loginAsAdmin();
        AppUser adminUser = appUserRepository.findByEmail("admin@example.com").orElseThrow();
        String createJson = """
            {"contactType":"LEAD","firstName":"Maria","lastName":"Puig","source":"WALK_IN"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/contacts", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"contactType\":\"LEAD\"", "\"firstName\":\"Maria\"", "\"lastName\":\"Puig\"");
        Contact saved = contactRepository.findAll().stream()
            .filter(c -> c.getLastName().equals("Puig")).findFirst().orElseThrow();
        assertThat(saved.getOwnerUserId()).isEqualTo(adminUser.getId());
        assertThat(auditLogRepository.findAll().stream()
            .anyMatch(row -> row.getAction() == AuditAction.CREATE
                && "CONTACT".equals(row.getEntityType())
                && row.getEntityId().equals(saved.getId()))).isTrue();
    }

    @Test
    void creatingWithAnExplicitOwnerUserIdUsesIt() {
        Session admin = loginAsAdmin();
        AppUser adminUser = appUserRepository.findByEmail("admin@example.com").orElseThrow();
        String createJson = """
            {"contactType":"LEAD","firstName":"Explicit","lastName":"Owned","source":"MANUAL","ownerUserId":"%s"}"""
            .formatted(adminUser.getId());

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/contacts", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Contact saved = contactRepository.findAll().stream()
            .filter(c -> c.getLastName().equals("Owned")).findFirst().orElseThrow();
        assertThat(saved.getOwnerUserId()).isEqualTo(adminUser.getId());
    }

    @Test
    void creatingWithoutAContactTypeOrSourceReturns400() {
        Session admin = loginAsAdmin();
        String missingType = """
            {"firstName":"No","lastName":"Missing-Type","source":"MANUAL"}""";
        ResponseEntity<String> typeResponse = restTemplate.postForEntity(
            "/api/contacts", new HttpEntity<>(missingType, authedHeaders(admin)), String.class);
        assertThat(typeResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String missingSource = """
            {"contactType":"LEAD","firstName":"No","lastName":"Missing-Type"}""";
        ResponseEntity<String> sourceResponse = restTemplate.postForEntity(
            "/api/contacts", new HttpEntity<>(missingSource, authedHeaders(admin)), String.class);
        assertThat(sourceResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void creatingWithABlankFirstNameOrLastNameReturns400() {
        Session admin = loginAsAdmin();
        String blankFirst = """
            {"contactType":"LEAD","firstName":"","lastName":"Blank-Name","source":"MANUAL"}""";
        ResponseEntity<String> firstResponse = restTemplate.postForEntity(
            "/api/contacts", new HttpEntity<>(blankFirst, authedHeaders(admin)), String.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String blankLast = """
            {"contactType":"LEAD","firstName":"Blank-Name","lastName":"","source":"MANUAL"}""";
        ResponseEntity<String> lastResponse = restTemplate.postForEntity(
            "/api/contacts", new HttpEntity<>(blankLast, authedHeaders(admin)), String.class);
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void gettingAnExistingContactReturnsIt() {
        Session admin = loginAsAdmin();
        String createJson = """
            {"contactType":"BUYER","firstName":"Get","lastName":"Gotten","source":"REFERRAL"}""";
        String createdId = extractId(restTemplate.postForEntity(
            "/api/contacts", new HttpEntity<>(createJson, authedHeaders(admin)), String.class).getBody());

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/contacts/" + createdId, HttpMethod.GET, new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"lastName\":\"Gotten\"");
    }

    @Test
    void gettingANonexistentContactReturns404() {
        Session admin = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/contacts/" + UUID.randomUUID(), HttpMethod.GET, new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listingReturnsAllContacts() {
        Session admin = loginAsAdmin();
        restTemplate.postForEntity("/api/contacts", new HttpEntity<>(
            """
            {"contactType":"LEAD","firstName":"List","lastName":"Listed-A","source":"MANUAL"}""",
            authedHeaders(admin)), String.class);
        restTemplate.postForEntity("/api/contacts", new HttpEntity<>(
            """
            {"contactType":"LEAD","firstName":"List","lastName":"Listed-B","source":"MANUAL"}""",
            authedHeaders(admin)), String.class);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/contacts", HttpMethod.GET, new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Listed-A", "Listed-B");
    }

    @Test
    void updatingFieldsSucceedsAndWritesAnUpdateAuditRow() {
        Session admin = loginAsAdmin();
        AppUser adminUser = appUserRepository.findByEmail("admin@example.com").orElseThrow();
        String createJson = """
            {"contactType":"LEAD","firstName":"Update","lastName":"Updated","source":"MANUAL"}""";
        String createdId = extractId(restTemplate.postForEntity(
            "/api/contacts", new HttpEntity<>(createJson, authedHeaders(admin)), String.class).getBody());

        String updateJson = """
            {"notes":"Called back, interested","ownerUserId":"%s"}""".formatted(adminUser.getId());
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/contacts/" + createdId, HttpMethod.PATCH,
            new HttpEntity<>(updateJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Called back, interested");
        assertThat(auditLogRepository.findAll().stream()
            .anyMatch(row -> row.getAction() == AuditAction.UPDATE
                && "CONTACT".equals(row.getEntityType())
                && row.getEntityId().equals(UUID.fromString(createdId)))).isTrue();
    }

    private String extractId(String json) {
        // {"id":"<uuid>", ...} - id is always the first field ContactResponse serializes.
        int start = json.indexOf("\"id\":\"") + 6;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
