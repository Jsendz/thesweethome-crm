package com.sweethomecompany.crm.deal;

import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogRepository;
import com.sweethomecompany.crm.contact.ContactRepository;
import com.sweethomecompany.crm.property.PropertyRepository;
import com.sweethomecompany.crm.property.PropertyTranslationRepository;
import com.sweethomecompany.crm.user.AppUser;
import com.sweethomecompany.crm.user.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivityControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private PropertyTranslationRepository propertyTranslationRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Value("${app.default-agency-id}")
    private UUID defaultAgencyId;

    private static final List<String> TEST_CONTACT_LAST_NAMES = List.of(
            "Activity-Call", "Activity-Viewing", "Activity-Override", "Activity-NoType", "Activity-404", "Activity-List");

    private static final List<String> TEST_PROPERTY_CODES = List.of("ACT-P-1");

    @BeforeEach
    @AfterEach
    void cleanUp() {
        contactRepository.findAll().stream()
                .filter(c -> TEST_CONTACT_LAST_NAMES.contains(c.getLastName()))
                .forEach(contact -> {
                    dealRepository.findByContactId(contact.getId()).forEach(deal -> {
                        activityRepository.findByDealIdOrderByOccurredAtDesc(deal.getId())
                                .forEach(activityRepository::delete);
                        dealRepository.delete(deal);
                    });
                    contactRepository.delete(contact);
                });
        TEST_PROPERTY_CODES.forEach(code ->
                propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, code).ifPresent(property -> {
                    propertyTranslationRepository.deleteAll(
                            propertyTranslationRepository.findByPropertyId(property.getId()));
                    propertyRepository.delete(property);
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

    private String extractId(String json) {
        int start = json.indexOf("\"id\":\"") + 6;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private String createContact(Session session, String lastName) {
        String createJson = """
            {"contactType":"LEAD","firstName":"Test","lastName":"%s","source":"MANUAL"}""".formatted(lastName);
        return extractId(restTemplate.postForEntity(
            "/api/contacts", new HttpEntity<>(createJson, authedHeaders(session)), String.class).getBody());
    }

    private String createProperty(Session session, String referenceCode) {
        String createJson = """
            {"referenceCode":"%s","propertyType":"APARTMENT","operation":"SALE",
             "price":200000,"caTitle":"Test property"}""".formatted(referenceCode);
        return extractId(restTemplate.postForEntity(
            "/api/properties", new HttpEntity<>(createJson, authedHeaders(session)), String.class).getBody());
    }

    private String createDeal(Session session, String contactId, String propertyId) {
        String createJson = propertyId == null
            ? """
                {"contactId":"%s"}""".formatted(contactId)
            : """
                {"contactId":"%s","propertyId":"%s"}""".formatted(contactId, propertyId);
        return extractId(restTemplate.postForEntity(
            "/api/deals", new HttpEntity<>(createJson, authedHeaders(session)), String.class).getBody());
    }

    @Test
    void loggingACallAgainstADealSucceedsInheritsContactAndPropertyAndWritesACreateAuditRow() {
        Session admin = loginAsAdmin();
        AppUser adminUser = appUserRepository.findByEmail("admin@example.com").orElseThrow();
        String contactId = createContact(admin, "Activity-Call");
        String propertyId = createProperty(admin, "ACT-P-1");
        String dealId = createDeal(admin, contactId, propertyId);
        String activityJson = """
            {"activityType":"CALL","notes":"Left a voicemail","occurredAt":"2026-11-26T10:00:00Z"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/deals/" + dealId + "/activities", new HttpEntity<>(activityJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"contactId\":\"" + contactId + "\"");
        assertThat(response.getBody()).contains("\"propertyId\":\"" + propertyId + "\"");
        assertThat(response.getBody()).contains("\"userId\":\"" + adminUser.getId() + "\"");
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED\"");
        String activityId = extractId(response.getBody());
        assertThat(auditLogRepository.findAll().stream()
            .anyMatch(row -> row.getAction() == AuditAction.CREATE
                && "ACTIVITY".equals(row.getEntityType())
                && row.getEntityId().equals(UUID.fromString(activityId)))).isTrue();
    }

    @Test
    void loggingAViewingWithNoExplicitStatusDefaultsToScheduledNotCompleted() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Activity-Viewing");
        String dealId = createDeal(admin, contactId, null);
        String activityJson = """
            {"activityType":"VIEWING","occurredAt":"2026-11-27T15:00:00Z"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/deals/" + dealId + "/activities", new HttpEntity<>(activityJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"status\":\"SCHEDULED\"");
    }

    @Test
    void anExplicitStatusOverridesTheDefault() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Activity-Override");
        String dealId = createDeal(admin, contactId, null);
        String activityJson = """
            {"activityType":"VIEWING","occurredAt":"2026-11-27T15:00:00Z","status":"COMPLETED"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/deals/" + dealId + "/activities", new HttpEntity<>(activityJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED\"");
    }

    @Test
    void loggingWithoutAnActivityTypeOrOccurredAtReturns400() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Activity-NoType");
        String dealId = createDeal(admin, contactId, null);

        ResponseEntity<String> missingType = restTemplate.postForEntity(
            "/api/deals/" + dealId + "/activities",
            new HttpEntity<>("""
                {"occurredAt":"2026-11-27T15:00:00Z"}""", authedHeaders(admin)), String.class);
        assertThat(missingType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> missingOccurredAt = restTemplate.postForEntity(
            "/api/deals/" + dealId + "/activities",
            new HttpEntity<>("""
                {"activityType":"CALL"}""", authedHeaders(admin)), String.class);
        assertThat(missingOccurredAt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void loggingAgainstANonexistentDealReturns404() {
        Session admin = loginAsAdmin();
        String activityJson = """
            {"activityType":"CALL","occurredAt":"2026-11-27T15:00:00Z"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/deals/" + UUID.randomUUID() + "/activities",
            new HttpEntity<>(activityJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listingADealsActivitiesReturnsThemNewestFirst() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Activity-List");
        String dealId = createDeal(admin, contactId, null);
        restTemplate.postForEntity("/api/deals/" + dealId + "/activities", new HttpEntity<>(
            """
            {"activityType":"NOTE","notes":"First","occurredAt":"2026-11-20T09:00:00Z"}""",
            authedHeaders(admin)), String.class);
        restTemplate.postForEntity("/api/deals/" + dealId + "/activities", new HttpEntity<>(
            """
            {"activityType":"NOTE","notes":"Second","occurredAt":"2026-11-25T09:00:00Z"}""",
            authedHeaders(admin)), String.class);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId + "/activities", HttpMethod.GET, new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body.indexOf("Second")).isLessThan(body.indexOf("First"));
    }
}
