package com.sweethomecompany.crm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// Requires Postgres reachable at the configured DB_HOST/DB_PORT — run
// `docker compose up -d postgres` first.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrmApplicationDbHealthTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReportsDatabaseUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getBody()).contains("\"db\"").contains("\"status\":\"UP\"");
    }
}
