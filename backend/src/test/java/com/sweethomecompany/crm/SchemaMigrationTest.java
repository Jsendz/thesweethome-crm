package com.sweethomecompany.crm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsTheDefaultAgency() {
        String name = jdbcTemplate.queryForObject(
            "SELECT name FROM agency WHERE id = '11111111-1111-1111-1111-111111111111'",
            String.class);
        assertThat(name).isEqualTo("The Sweet Home Company");
    }

    @Test
    void appUserAndAuditLogTablesExist() {
        Long userCount = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Long.class);
        Long auditCount = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log", Long.class);
        assertThat(userCount).isZero();
        assertThat(auditCount).isZero();
    }
}
