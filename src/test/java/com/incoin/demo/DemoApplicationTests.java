package com.incoin.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — just verifies the Spring context loads without errors.
 * Requires Redis to be available (use Testcontainers or mock for CI).
 */
@SpringBootTest
@ActiveProfiles("test")
class DemoApplicationTests {

    @Test
    void contextLoads() {
        // If the application context starts without throwing, this test passes.
    }
}
