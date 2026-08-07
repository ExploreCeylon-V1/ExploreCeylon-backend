package com.exploreceylon.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DbConstraintFixInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            log.info("Checking and dropping legacy PostgreSQL trips_status_check constraint if present...");
            jdbcTemplate.execute("ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_status_check");
            log.info("Successfully dropped legacy trips_status_check constraint!");
        } catch (Exception e) {
            log.warn("Could not drop trips_status_check constraint automatically: {}", e.getMessage());
        }
    }
}
