package it.eng.dcp.issuer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Basic test to verify that the Spring Boot application context loads successfully.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
class IssuerApplicationTests {

    /**
     * Test that verifies the Spring application context loads without errors.
     */
    @Test
    void contextLoads() {
        // This test verifies that the Spring application context loads without errors
    }
}

