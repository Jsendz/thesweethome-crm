package com.sweethomecompany.crm.deal;

import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogRepository;
import com.sweethomecompany.crm.contact.Contact;
import com.sweethomecompany.crm.contact.ContactRepository;
import com.sweethomecompany.crm.property.Property;
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
class DealControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DealRepository dealRepository;

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
            "Deal-Basic", "Deal-Explicit", "Deal-NoContact", "Deal-NoProperty", "Deal-Names",
            "Deal-404", "Deal-ListA", "Deal-ListB", "Deal-FilterA", "Deal-FilterB", "Deal-Stage",
            "Deal-ColdNoReason", "Deal-ColdBlank", "Deal-ColdOk", "Deal-LostNoReason", "Deal-LostBlank",
            "Deal-LostOk", "Deal-ColdBack", "Deal-ColdResubmit");

    private static final List<String> TEST_PROPERTY_CODES = List.of(
            "DEAL-P-1", "DEAL-P-2");

    @BeforeEach
    @AfterEach
    void cleanUp() {
        contactRepository.findAll().stream()
                .filter(c -> TEST_CONTACT_LAST_NAMES.contains(c.getLastName()))
                .forEach(contact -> {
                    dealRepository.findByContactId(contact.getId()).forEach(dealRepository::delete);
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

    private String createContact(Session session, String lastName) {
        String createJson = """
            {"contactType":"LEAD","firstName":"Test","lastName":"%s","source":"MANUAL"}""".formatted(lastName);
        String body = restTemplate.postForEntity(
            "/api/contacts", new HttpEntity<>(createJson, authedHeaders(session)), String.class).getBody();
        return extractId(body);
    }

    private String createProperty(Session session, String referenceCode) {
        String createJson = """
            {"referenceCode":"%s","propertyType":"APARTMENT","operation":"SALE",
             "price":200000,"caTitle":"Test property"}""".formatted(referenceCode);
        String body = restTemplate.postForEntity(
            "/api/properties", new HttpEntity<>(createJson, authedHeaders(session)), String.class).getBody();
        return extractId(body);
    }

    private String extractId(String json) {
        int start = json.indexOf("\"id\":\"") + 6;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    @Test
    void creatingADealWithValidDataSucceedsDefaultsStageToLeadAndWritesACreateAuditRow() {
        Session admin = loginAsAdmin();
        AppUser adminUser = appUserRepository.findByEmail("admin@example.com").orElseThrow();
        String contactId = createContact(admin, "Deal-Basic");
        String createJson = """
            {"contactId":"%s"}""".formatted(contactId);

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/deals", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"stage\":\"LEAD\"");
        assertThat(response.getBody()).contains("\"assignedUserId\":\"" + adminUser.getId() + "\"");
        Deal saved = dealRepository.findByContactId(UUID.fromString(contactId)).get(0);
        assertThat(auditLogRepository.findAll().stream()
            .anyMatch(row -> row.getAction() == AuditAction.CREATE
                && "DEAL".equals(row.getEntityType())
                && row.getEntityId().equals(saved.getId()))).isTrue();
    }

    @Test
    void creatingWithAnExplicitPropertyIdAndAssignedUserIdUsesThem() {
        Session admin = loginAsAdmin();
        AppUser adminUser = appUserRepository.findByEmail("admin@example.com").orElseThrow();
        String contactId = createContact(admin, "Deal-Explicit");
        String propertyId = createProperty(admin, "DEAL-P-1");
        String createJson = """
            {"contactId":"%s","propertyId":"%s","assignedUserId":"%s"}"""
            .formatted(contactId, propertyId, adminUser.getId());

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/deals", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"propertyId\":\"" + propertyId + "\"");
        assertThat(response.getBody()).contains("\"propertyReferenceCode\":\"DEAL-P-1\"");
        assertThat(response.getBody()).contains("\"assignedUserId\":\"" + adminUser.getId() + "\"");
    }

    @Test
    void creatingWithANonexistentContactIdReturns404() {
        Session admin = loginAsAdmin();
        String createJson = """
            {"contactId":"%s"}""".formatted(UUID.randomUUID());

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/deals", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void creatingWithANonexistentPropertyIdReturns404() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Deal-NoProperty");
        String createJson = """
            {"contactId":"%s","propertyId":"%s"}""".formatted(contactId, UUID.randomUUID());

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/deals", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void gettingAnExistingDealReturnsItWithContactAndPropertyNames() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Deal-Names");
        String propertyId = createProperty(admin, "DEAL-P-2");
        String createJson = """
            {"contactId":"%s","propertyId":"%s"}""".formatted(contactId, propertyId);
        String dealId = extractId(restTemplate.postForEntity(
            "/api/deals", new HttpEntity<>(createJson, authedHeaders(admin)), String.class).getBody());

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId, HttpMethod.GET, new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"contactName\":\"Test Deal-Names\"");
        assertThat(response.getBody()).contains("\"propertyReferenceCode\":\"DEAL-P-2\"");
    }

    @Test
    void gettingANonexistentDealReturns404() {
        Session admin = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + UUID.randomUUID(), HttpMethod.GET, new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listingReturnsAllDeals() {
        Session admin = loginAsAdmin();
        String contactA = createContact(admin, "Deal-ListA");
        String contactB = createContact(admin, "Deal-ListB");
        restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactA), authedHeaders(admin)), String.class);
        restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactB), authedHeaders(admin)), String.class);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals", HttpMethod.GET, new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(contactA, contactB);
    }

    @Test
    void listingFilteredByContactIdReturnsOnlyThatContactsDeals() {
        Session admin = loginAsAdmin();
        String contactA = createContact(admin, "Deal-FilterA");
        String contactB = createContact(admin, "Deal-FilterB");
        restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactA), authedHeaders(admin)), String.class);
        restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactB), authedHeaders(admin)), String.class);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals?contactId=" + contactA, HttpMethod.GET, new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(contactA);
        assertThat(response.getBody()).doesNotContain(contactB);
    }

    @Test
    void updatingStageToAnOrdinaryValueSucceedsAndWritesAnUpdateAuditRow() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Deal-Stage");
        String dealId = extractId(restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactId), authedHeaders(admin)), String.class).getBody());

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"QUALIFIED"}""", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"stage\":\"QUALIFIED\"");
        assertThat(auditLogRepository.findAll().stream()
            .anyMatch(row -> row.getAction() == AuditAction.UPDATE
                && "DEAL".equals(row.getEntityType())
                && row.getEntityId().equals(UUID.fromString(dealId)))).isTrue();
    }

    @Test
    void updatingStageToColdWithoutAColdReasonReturns400() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Deal-ColdNoReason");
        String dealId = extractId(restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactId), authedHeaders(admin)), String.class).getBody());

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"COLD"}""", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatingStageToColdWithABlankColdReasonReturns400() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Deal-ColdBlank");
        String dealId = extractId(restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactId), authedHeaders(admin)), String.class).getBody());

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"COLD","coldReason":""}""", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatingStageToColdWithAColdReasonSucceedsAndSetsColdMarkedFields() {
        Session admin = loginAsAdmin();
        AppUser adminUser = appUserRepository.findByEmail("admin@example.com").orElseThrow();
        String contactId = createContact(admin, "Deal-ColdOk");
        String dealId = extractId(restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactId), authedHeaders(admin)), String.class).getBody());

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"COLD","coldReason":"Went quiet after second viewing"}""", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"stage\":\"COLD\"");
        assertThat(response.getBody()).contains("Went quiet after second viewing");
        Deal saved = dealRepository.findById(UUID.fromString(dealId)).orElseThrow();
        assertThat(saved.getColdMarkedByUserId()).isEqualTo(adminUser.getId());
        assertThat(saved.getColdMarkedAt()).isNotNull();
    }

    @Test
    void updatingStageToClosedLostWithoutALostReasonReturns400() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Deal-LostNoReason");
        String dealId = extractId(restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactId), authedHeaders(admin)), String.class).getBody());

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"CLOSED_LOST"}""", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatingStageToClosedLostWithABlankLostReasonReturns400() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Deal-LostBlank");
        String dealId = extractId(restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactId), authedHeaders(admin)), String.class).getBody());

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"CLOSED_LOST","lostReason":""}""", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatingStageToClosedLostWithALostReasonSucceeds() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Deal-LostOk");
        String dealId = extractId(restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactId), authedHeaders(admin)), String.class).getBody());

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"CLOSED_LOST","lostReason":"Chose a competing agency"}""", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"stage\":\"CLOSED_LOST\"");
        assertThat(response.getBody()).contains("Chose a competing agency");
    }

    @Test
    void movingAColdDealBackToAnActiveStageLeavesTheColdFieldsInPlace() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Deal-ColdBack");
        String dealId = extractId(restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactId), authedHeaders(admin)), String.class).getBody());
        restTemplate.exchange("/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"COLD","coldReason":"No response in 2 weeks"}""", authedHeaders(admin)), String.class);
        Deal afterCold = dealRepository.findById(UUID.fromString(dealId)).orElseThrow();
        UUID coldMarkedBy = afterCold.getColdMarkedByUserId();
        var coldMarkedAt = afterCold.getColdMarkedAt();

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"QUALIFIED"}""", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"stage\":\"QUALIFIED\"");
        assertThat(response.getBody()).contains("No response in 2 weeks");
        Deal afterReactivate = dealRepository.findById(UUID.fromString(dealId)).orElseThrow();
        assertThat(afterReactivate.getColdReason()).isEqualTo("No response in 2 weeks");
        assertThat(afterReactivate.getColdMarkedByUserId()).isEqualTo(coldMarkedBy);
        assertThat(afterReactivate.getColdMarkedAt()).isEqualTo(coldMarkedAt);
    }

    @Test
    void resubmittingColdOnAnAlreadyColdDealDoesNotOverwriteTheOriginalColdMarkedAt() throws InterruptedException {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Deal-ColdResubmit");
        String dealId = extractId(restTemplate.postForEntity("/api/deals", new HttpEntity<>(
            """
            {"contactId":"%s"}""".formatted(contactId), authedHeaders(admin)), String.class).getBody());
        restTemplate.exchange("/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"COLD","coldReason":"First reason"}""", authedHeaders(admin)), String.class);
        Deal firstCold = dealRepository.findById(UUID.fromString(dealId)).orElseThrow();
        var originalColdMarkedAt = firstCold.getColdMarkedAt();
        // Sleep briefly so a bug that resets coldMarkedAt on resubmission would
        // produce a detectably different Instant, not one indistinguishable by
        // clock resolution.
        Thread.sleep(10);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/deals/" + dealId, HttpMethod.PATCH,
            new HttpEntity<>("""
                {"stage":"COLD","coldReason":"First reason"}""", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Deal afterResubmit = dealRepository.findById(UUID.fromString(dealId)).orElseThrow();
        assertThat(afterResubmit.getColdMarkedAt()).isEqualTo(originalColdMarkedAt);
    }
}
