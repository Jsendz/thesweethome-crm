package com.sweethomecompany.crm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsConfigTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void allowsRequestsFromLocalFrontendOrigin() {
        RequestEntity<Void> request = RequestEntity
            .get(URI.create("/actuator/health"))
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .build();

        ResponseEntity<String> response = restTemplate.exchange(request, String.class);

        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .isEqualTo("http://localhost:3000");
    }
}
