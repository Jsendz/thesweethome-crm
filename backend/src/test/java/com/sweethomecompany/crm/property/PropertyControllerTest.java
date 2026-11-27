package com.sweethomecompany.crm.property;

import com.sweethomecompany.crm.audit.AuditAction;
import com.sweethomecompany.crm.audit.AuditLogRepository;
import com.sweethomecompany.crm.contact.ContactRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
// Explicit import required: this package now declares its own MediaType
// enum (Task 4), which would otherwise shadow Spring's MediaType used
// below - a same-package type always wins over a wildcard import.
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PropertyControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private PropertyTranslationRepository propertyTranslationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Value("${app.default-agency-id}")
    private UUID defaultAgencyId;

    // Same reasoning as UserControllerTest: these tests create real rows via
    // real HTTP calls, so @Transactional rollback doesn't apply. Clean up by
    // the reference codes these tests create, translations first (FK).
    private static final List<String> TEST_REFERENCE_CODES = List.of(
            "P-001", "P-DUPE", "P-NO-CA", "P-BAD-PRICE", "P-NEGATIVE-PRICE", "P-NO-PRICE", "P-GET", "P-404-PROOF",
            "P-LIST-A", "P-LIST-B", "P-UPDATE", "P-BAD-PATCH-PRICE", "P-CA-EDIT", "P-TRANSLATE", "P-CA-REJECT",
            "P-DUPE-TARGET", "P-DUPE-SOURCE", "P-BLANK-PATCH", "P-PARISH-ONLY",
            "P-OWNER-CREATE", "P-OWNER-404", "P-OWNER-UPDATE");

    // ownerContactId tests create a contact to reference - cleaned up by last
    // name, same pattern ContactControllerTest/DealControllerTest use.
    private static final List<String> TEST_CONTACT_LAST_NAMES = List.of("Property-Owner");

    @BeforeEach
    @AfterEach
    void cleanUpTestProperties() {
        TEST_REFERENCE_CODES.forEach(code ->
                propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, code).ifPresent(property -> {
                    propertyTranslationRepository.deleteAll(
                            propertyTranslationRepository.findByPropertyId(property.getId()));
                    propertyRepository.delete(property);
                }));
        contactRepository.findAll().stream()
                .filter(c -> TEST_CONTACT_LAST_NAMES.contains(c.getLastName()))
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

    private record Session(String csrfToken, String sessionCookie) {
    }

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
    void creatingAPropertyWithValidDataSucceedsAndWritesACreateAuditRow() {
        Session admin = loginAsAdmin();
        String createJson = """
                {"referenceCode":"P-001","propertyType":"APARTMENT","operation":"SALE",
                 "price":250000,"caTitle":"Pis a Andorra la Vella","caDescription":"Molt lluminós"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/properties", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"referenceCode\":\"P-001\"");
        assertThat(response.getBody()).contains("\"status\":\"DRAFT\"");
        assertThat(response.getBody()).contains("\"locale\":\"ca\"");
        assertThat(response.getBody()).contains("Pis a Andorra la Vella");

        Property saved = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-001").orElseThrow();
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(row -> row.getAction() == AuditAction.CREATE && row.getEntityId().equals(saved.getId())))
                .isTrue();
    }

    @Test
    void creatingWithADuplicateReferenceCodeInTheSameAgencyReturns409() {
        Session admin = loginAsAdmin();
        String createJson = """
                {"referenceCode":"P-DUPE","propertyType":"HOUSE","operation":"RENT",
                 "price":1200,"caTitle":"Casa de lloguer"}""";
        restTemplate.postForEntity("/api/properties", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        ResponseEntity<String> secondResponse = restTemplate.postForEntity(
                "/api/properties", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void creatingWithoutACatalanTitleReturns400() {
        Session admin = loginAsAdmin();
        String createJson = """
                {"referenceCode":"P-NO-CA","propertyType":"LAND","operation":"SALE","price":50000}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/properties", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-NO-CA")).isEmpty();
    }

    @Test
    void creatingWithAZeroOrNegativePriceReturns400() {
        Session admin = loginAsAdmin();
        String createJson = """
                {"referenceCode":"P-BAD-PRICE","propertyType":"LAND","operation":"SALE",
                 "price":0,"caTitle":"Terreny"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/properties", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-BAD-PRICE")).isEmpty();
    }

    // Final-review finding (Review Focus gap): the existing "zero or
    // negative price" test only ever sent price:0 - negative and missing
    // price were never actually exercised on create, nor was price<=0 ever
    // tested on PATCH.
    @Test
    void creatingWithANegativePriceReturns400() {
        Session admin = loginAsAdmin();
        String createJson = """
                {"referenceCode":"P-NEGATIVE-PRICE","propertyType":"LAND","operation":"SALE",
                 "price":-500,"caTitle":"Terreny"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/properties", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-NEGATIVE-PRICE")).isEmpty();
    }

    @Test
    void creatingWithoutAPriceReturns400() {
        Session admin = loginAsAdmin();
        String createJson = """
                {"referenceCode":"P-NO-PRICE","propertyType":"LAND","operation":"SALE","caTitle":"Terreny"}""";

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/properties", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-NO-PRICE")).isEmpty();
    }

    @Test
    void updatingWithAZeroOrNegativePriceReturns400() {
        Session admin = loginAsAdmin();
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-BAD-PATCH-PRICE","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Original"}""", authedHeaders(admin)), String.class);
        Property saved = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-BAD-PATCH-PRICE")
                .orElseThrow();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/properties/" + saved.getId(), HttpMethod.PATCH,
                new HttpEntity<>("{\"price\":-1}", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Property reloaded = propertyRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPrice()).isEqualByComparingTo(java.math.BigDecimal.valueOf(1000));
    }

    @Test
    void gettingAnExistingPropertyReturnsItWithItsCaTranslation() {
        Session admin = loginAsAdmin();
        String createJson = """
                {"referenceCode":"P-GET","propertyType":"APARTMENT","operation":"SALE",
                 "price":99000,"caTitle":"Pis per vendre"}""";
        restTemplate.postForEntity("/api/properties", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);
        Property saved = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-GET").orElseThrow();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/properties/" + saved.getId(), HttpMethod.GET, new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Pis per vendre");
        assertThat(response.getBody()).contains("\"locale\":\"ca\"");
    }

    @Test
    void gettingANonexistentPropertyReturns404() {
        Session admin = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/properties/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listingReturnsSummariesForAllProperties() {
        Session admin = loginAsAdmin();
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-LIST-A","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"A"}""", authedHeaders(admin)), String.class);
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-LIST-B","propertyType":"HOUSE","operation":"RENT",
                 "price":2000,"caTitle":"B"}""", authedHeaders(admin)), String.class);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/properties", HttpMethod.GET, new HttpEntity<>(authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("P-LIST-A");
        assertThat(response.getBody()).contains("P-LIST-B");
    }

    @Test
    void updatingFieldsIncludingStatusSucceedsAndWritesAnUpdateAuditRowWithChangedFields() {
        Session admin = loginAsAdmin();
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-UPDATE","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Original"}""", authedHeaders(admin)), String.class);
        Property saved = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-UPDATE").orElseThrow();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/properties/" + saved.getId(), HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"WITHDRAWN\",\"price\":1500}", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"WITHDRAWN\"");
        Property reloaded = propertyRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PropertyStatus.WITHDRAWN);
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(row -> row.getAction() == AuditAction.UPDATE
                        && row.getEntityId().equals(saved.getId())
                        && row.getDetail() != null
                        && row.getDetail().contains("WITHDRAWN")))
                .isTrue();
    }

    // Final-review finding: changing a property's referenceCode to one
    // already used by another property hit the unique index at commit time
    // (after this method had already returned 200), surfacing as a bare 500
    // instead of the 409 create() already gives for the same conflict.
    @Test
    void updatingToAReferenceCodeAlreadyUsedByAnotherPropertyReturns409() {
        Session admin = loginAsAdmin();
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-DUPE-TARGET","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Target"}""", authedHeaders(admin)), String.class);
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-DUPE-SOURCE","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Source"}""", authedHeaders(admin)), String.class);
        Property source = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-DUPE-SOURCE")
                .orElseThrow();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/properties/" + source.getId(), HttpMethod.PATCH,
                new HttpEntity<>("{\"referenceCode\":\"P-DUPE-TARGET\"}", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Property reloaded = propertyRepository.findById(source.getId()).orElseThrow();
        assertThat(reloaded.getReferenceCode()).isEqualTo("P-DUPE-SOURCE");
    }

    // Final-review finding: a present-but-blank referenceCode/caTitle used to
    // be written as-is through PATCH, silently undoing create()'s own
    // required-field validation for the same fields.
    @Test
    void updatingWithABlankReferenceCodeOrCatalanTitleReturns400() {
        Session admin = loginAsAdmin();
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-BLANK-PATCH","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Original"}""", authedHeaders(admin)), String.class);
        Property saved = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-BLANK-PATCH")
                .orElseThrow();

        ResponseEntity<String> blankReferenceCode = restTemplate.exchange(
                "/api/properties/" + saved.getId(), HttpMethod.PATCH,
                new HttpEntity<>("{\"referenceCode\":\"\"}", authedHeaders(admin)), String.class);
        ResponseEntity<String> blankCaTitle = restTemplate.exchange(
                "/api/properties/" + saved.getId(), HttpMethod.PATCH,
                new HttpEntity<>("{\"caTitle\":\"\"}", authedHeaders(admin)), String.class);

        assertThat(blankReferenceCode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blankCaTitle.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Property reloaded = propertyRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getReferenceCode()).isEqualTo("P-BLANK-PATCH");
    }

    // Final-review finding: fields other than referenceCode/propertyType/
    // operation/status/price were applied without being recorded in
    // `changes`, so an update touching only one of them (e.g. just the
    // parish) wrote no audit row at all.
    @Test
    void updatingOnlyTheParishWritesAnUpdateAuditRow() {
        Session admin = loginAsAdmin();
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-PARISH-ONLY","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Original","parish":"Encamp"}""", authedHeaders(admin)), String.class);
        Property saved = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-PARISH-ONLY")
                .orElseThrow();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/properties/" + saved.getId(), HttpMethod.PATCH,
                new HttpEntity<>("{\"parish\":\"Ordino\"}", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(row -> row.getAction() == AuditAction.UPDATE
                        && row.getEntityId().equals(saved.getId())
                        && row.getDetail() != null
                        && row.getDetail().contains("Ordino")))
                .isTrue();
    }

    @Test
    void updatingTheCatalanTitleThroughPatchChangesTheCaTranslation() {
        Session admin = loginAsAdmin();
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-CA-EDIT","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Abans"}""", authedHeaders(admin)), String.class);
        Property saved = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-CA-EDIT").orElseThrow();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/properties/" + saved.getId(), HttpMethod.PATCH,
                new HttpEntity<>("{\"caTitle\":\"Despres\"}", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Despres");
        PropertyTranslation ca = propertyTranslationRepository
                .findByPropertyIdAndLocale(saved.getId(), "ca").orElseThrow();
        assertThat(ca.getTitle()).isEqualTo("Despres");
    }

    @Test
    void addingANonCatalanTranslationSucceeds() {
        Session admin = loginAsAdmin();
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-TRANSLATE","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Catala"}""", authedHeaders(admin)), String.class);
        Property saved = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-TRANSLATE").orElseThrow();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/properties/" + saved.getId() + "/translations",
                new HttpEntity<>("{\"locale\":\"en\",\"title\":\"English title\"}", authedHeaders(admin)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(propertyTranslationRepository.findByPropertyIdAndLocale(saved.getId(), "en"))
                .isPresent()
                .get().extracting(PropertyTranslation::getTitle).isEqualTo("English title");
    }

    @Test
    void addingATranslationWithLocaleCaIsRejectedWith400() {
        Session admin = loginAsAdmin();
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-CA-REJECT","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Catala"}""", authedHeaders(admin)), String.class);
        Property saved = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-CA-REJECT").orElseThrow();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/properties/" + saved.getId() + "/translations",
                new HttpEntity<>("{\"locale\":\"ca\",\"title\":\"Should be rejected\"}", authedHeaders(admin)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String createContact(Session session, String lastName) {
        String createJson = """
            {"contactType":"LEAD","firstName":"Owner","lastName":"%s","source":"MANUAL"}""".formatted(lastName);
        String body = restTemplate.postForEntity(
                "/api/contacts", new HttpEntity<>(createJson, authedHeaders(session)), String.class).getBody();
        int start = body.indexOf("\"id\":\"") + 6;
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    @Test
    void creatingWithAnOwnerContactIdSucceedsAndItIsReflectedInTheResponse() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Property-Owner");
        String createJson = """
                {"referenceCode":"P-OWNER-CREATE","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Amb propietari","ownerContactId":"%s"}""".formatted(contactId);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/properties", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"ownerContactId\":\"" + contactId + "\"");
    }

    @Test
    void creatingWithANonexistentOwnerContactIdReturns404() {
        Session admin = loginAsAdmin();
        String createJson = """
                {"referenceCode":"P-OWNER-404","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Sense propietari","ownerContactId":"%s"}""".formatted(UUID.randomUUID());

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/properties", new HttpEntity<>(createJson, authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-OWNER-404")).isEmpty();
    }

    @Test
    void updatingOwnerContactIdSucceedsAndWritesAnUpdateAuditRow() {
        Session admin = loginAsAdmin();
        String contactId = createContact(admin, "Property-Owner");
        restTemplate.postForEntity("/api/properties", new HttpEntity<>("""
                {"referenceCode":"P-OWNER-UPDATE","propertyType":"APARTMENT","operation":"SALE",
                 "price":1000,"caTitle":"Per actualitzar"}""", authedHeaders(admin)), String.class);
        Property saved = propertyRepository.findByAgencyIdAndReferenceCode(defaultAgencyId, "P-OWNER-UPDATE").orElseThrow();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/properties/" + saved.getId(), HttpMethod.PATCH,
                new HttpEntity<>("{\"ownerContactId\":\"" + contactId + "\"}", authedHeaders(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"ownerContactId\":\"" + contactId + "\"");
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(row -> row.getAction() == AuditAction.UPDATE
                        && row.getEntityId().equals(saved.getId())
                        && row.getDetail() != null
                        && row.getDetail().contains("ownerContactId")))
                .isTrue();
    }
}
