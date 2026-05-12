package it.eng.dataplane.httppush;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot entry point for the HTTP-PUSH Data Plane service.
 * Component-scans both data-plane and tools packages to auto-configure
 * S3, encryption, and other shared infrastructure from s3-support.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"it.eng.dataplane", "it.eng.tools"})
public class DataPlaneHttpPushApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DataPlaneHttpPushApplication.class, args);
    }
}
