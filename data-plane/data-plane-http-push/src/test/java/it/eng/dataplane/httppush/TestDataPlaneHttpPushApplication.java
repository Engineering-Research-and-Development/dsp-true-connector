package it.eng.dataplane.httppush;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test entry point for the HTTP-PUSH Data Plane integration tests.
 *
 * <p>Mirrors {@code DataPlaneHttpPushApplication} but lives in the test classpath so
 * that {@code @SpringBootTest} without an explicit {@code classes} attribute can find it
 * via Spring Boot's upward {@code @SpringBootConfiguration} scan.
 */
@SpringBootApplication(scanBasePackages = {"it.eng.dataplane", "it.eng.tools"})
public class TestDataPlaneHttpPushApplication {

    /**
     * Starts the test application context.
     *
     * @param args command-line arguments (unused in tests)
     */
    public static void main(String[] args) {
        SpringApplication.run(TestDataPlaneHttpPushApplication.class, args);
    }
}
