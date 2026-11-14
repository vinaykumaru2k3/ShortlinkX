package com.link.shortlinkx;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the real Flyway migrations (through Spring Boot's own Flyway auto-configuration) and lets Hibernate
 * validate the entities against the migrated schema, so drift between the two is caught in CI.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flyway_check;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsRunAndEntitiesMatchTheSchema() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE AND \"version\" IS NOT NULL",
                Integer.class);
        assertEquals(2, applied);
    }
}
